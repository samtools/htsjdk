/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.*;

public class CompressionHeader {
    private static final String RN_readNamesIncluded = "RN";
    private static final String AP_alignmentPositionIsDelta = "AP";
    private static final String RR_referenceRequired = "RR";
    private static final String TD_tagIdsDictionary = "TD";
    private static final String SM_substitutionMatrix = "SM";

    private CompressionHeaderEncodingMap encodingMap;
    // these all default to true, per the spec
    private boolean APDelta = true;
    private boolean preserveReadNames = true;
    private boolean referenceRequired = true;

    // ContentID to tage series EncodingDescriptor. For tags, the content ID is defined
    // by to be a value derived from the tag data type and name (see @link #ReadTag.name3BytesToInt).
    private final Map<Integer, EncodingDescriptor> tagEncodingMap = new TreeMap<>();
    private SubstitutionMatrix substitutionMatrix;
    private byte[][][] tagIDDictionary;

    /**
     * Create a CompressionHeader using the default {@link CRAMEncodingStrategy}
     */
    public CompressionHeader() {
        encodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
    }

    public CompressionHeader(
            final CompressionHeaderEncodingMap encodingMap,
            final boolean isAPDelta,
            final boolean isPreserveReadNames,
            final boolean isReferenceRequired) {
        this.encodingMap = encodingMap;
        this.APDelta = isAPDelta;
        this.preserveReadNames = isPreserveReadNames;
        this.referenceRequired = isReferenceRequired;
    }

    /**
     * Create a compression header using the given {@link CompressionHeaderEncodingMap}.
     * @param encodingMap the encoding map to use for this compression header
     */
    public CompressionHeader(final CompressionHeaderEncodingMap encodingMap) {
        this.encodingMap = encodingMap;
    }

    /**
     * Read a COMPRESSION_HEADER Block from an InputStream and return its contents as a CompressionHeader.
     *
     * @param cramVersion the CRAM version
     * @param blockStream the stream to read from
     * @return a new CompressionHeader
     */
    public CompressionHeader(final CRAMVersion cramVersion, final InputStream blockStream) {
        final Block compressionHeaderBlock = Block.read(cramVersion, blockStream);
        if (compressionHeaderBlock.getContentType() != BlockContentType.COMPRESSION_HEADER) {
            throw new RuntimeIOException(
                    String.format("Compression header block expected, found: %s",
                            compressionHeaderBlock.getContentType().name()));
        }

        // get raw content since compression headers are always raw...
        try (final ByteArrayInputStream internalStream = new ByteArrayInputStream(compressionHeaderBlock.getRawContent())) {
            internalRead(internalStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Get the {@link CompressionHeaderEncodingMap} for this compression header.
     * @return {@link CompressionHeaderEncodingMap} for this {@link CompressionHeader}
     */
    public CompressionHeaderEncodingMap getEncodingMap() { return encodingMap; }

    /**
     * Write this CompressionHeader out to an internal OutputStream, wrap it in a Block, and write that
     * Block out to the passed-in OutputStream.
     *
     * @param cramVersion the CRAM version
     * @param blockStream the stream to write to
     */
    public void write(final CRAMVersion cramVersion, final OutputStream blockStream) {
        try (final ByteArrayOutputStream internalOutputStream = new ByteArrayOutputStream()) {
            internalWrite(internalOutputStream);
            final Block block = Block.createRawCompressionHeaderBlock(internalOutputStream.toByteArray());
            block.write(cramVersion, blockStream);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Return true if the header has APDelta set. Coordinate sorted input will use APDelta=true, but
     * it is also permitted for other sort orders to use APDelta=true.
     * @return the value of the APDelta flag
     */
    public boolean isAPDelta() {
        return APDelta;
    }

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public Map<Integer, EncodingDescriptor> getTagEncodingMap() {
        return tagEncodingMap;
    }

    public SubstitutionMatrix getSubstitutionMatrix() {
        return substitutionMatrix;
    }

    public byte[][][] getTagIDDictionary() {
        return tagIDDictionary;
    }

    public  void setTagIdDictionary(final byte[][][] dictionary) {
        this.tagIDDictionary = dictionary;
    }

    public void setSubstitutionMatrix(final SubstitutionMatrix substitutionMatrix) {
        this.substitutionMatrix = substitutionMatrix;
    }

    /**
     * @return true if RR is set on this compression header
     */
    public boolean isReferenceRequired() {
        return referenceRequired;
    }

    public void addTagEncoding(final int tagId, final ExternalCompressor compressor, final EncodingDescriptor params) {
        encodingMap.putTagBlockCompression(tagId, compressor);
        tagEncodingMap.put(tagId, params);
    }

    private byte[][][] parseDictionary(final byte[] bytes) {
        final List<List<byte[]>> dictionary = new ArrayList<List<byte[]>>();
        int i = 0;
        while (i < bytes.length) {
            final List<byte[]> list = new ArrayList<byte[]>();
            while (bytes[i] != 0) {
                list.add(Arrays.copyOfRange(bytes, i, i + 3));
                i += 3;
            }
            i++;
            dictionary.add(list);
        }

        int maxWidth = 0;
        for (final List<byte[]> list : dictionary) {
            maxWidth = Math.max(maxWidth, list.size());
        }

        final byte[][][] array = new byte[dictionary.size()][][];
        for (int j = 0; j < dictionary.size(); j++) {
            final List<byte[]> list = dictionary.get(j);
            array[j] = list.toArray(new byte[list.size()][]);
        }

        return array;
    }

    private byte[] dictionaryToByteArray() {
        int size = 0;
        for (final byte[][] dictionaryArrayArray : tagIDDictionary) {
            for (final byte[] dictionaryArray : dictionaryArrayArray) {
                size += dictionaryArray.length;
            }
            size++;
        }

        final byte[] bytes = new byte[size];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        for (final byte[][] dictionaryArrayArray : tagIDDictionary) {
            for (final byte[] dictionaryArray : dictionaryArrayArray) {
                buffer.put(dictionaryArray);
            }
            buffer.put((byte) 0);
        }

        return bytes;
    }

    private void internalRead(final InputStream is) {
        { // preservation map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buffer);
            for (int i = 0; i < mapSize; i++) {
                final String key = new String(new byte[]{buffer.get(), buffer.get()});
                if (RN_readNamesIncluded.equals(key))
                    preserveReadNames = buffer.get() == 1;
                else if (AP_alignmentPositionIsDelta.equals(key))
                    APDelta = buffer.get() == 1;
                else if (RR_referenceRequired.equals(key))
                    referenceRequired = buffer.get() == 1;
                else if (TD_tagIdsDictionary.equals(key)) {
                    final int size = ITF8.readUnsignedITF8(buffer);
                    final byte[] dictionaryBytes = new byte[size];
                    buffer.get(dictionaryBytes);
                    tagIDDictionary = parseDictionary(dictionaryBytes);
                } else if (SM_substitutionMatrix.equals(key)) {
                    // parse subs matrix here:
                    final byte[] matrixBytes = new byte[SubstitutionMatrix.BASES_SIZE];
                    buffer.get(matrixBytes);
                    substitutionMatrix = new SubstitutionMatrix(matrixBytes);
                } else {
                    throw new RuntimeException("Unknown preservation map key: " + key);
                }
            }
            if (substitutionMatrix == null || tagIDDictionary == null) {
                throw new CRAMException(
                        "substitution matrix and tag ID dictionary must be present in the compression header");
            }
        }

        // encoding map:
        encodingMap = new CompressionHeaderEncodingMap(is);

        { // tag encoding map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buf = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buf);
            for (int i = 0; i < mapSize; i++) {
                final int key = ITF8.readUnsignedITF8(buf);

                final EncodingID id = EncodingID.values()[buf.get()];
                final int paramLen = ITF8.readUnsignedITF8(buf);
                final byte[] paramBytes = new byte[paramLen];
                buf.get(paramBytes);

                tagEncodingMap.put(key, new EncodingDescriptor(id, paramBytes));
            }
        }
    }

    private void internalWrite(final OutputStream outputStream) throws IOException {
        { // preservation map:
            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(5, mapBuffer);

            mapBuffer.put(RN_readNamesIncluded.getBytes());
            mapBuffer.put((byte) (preserveReadNames ? 1 : 0));

            mapBuffer.put(AP_alignmentPositionIsDelta.getBytes());
            mapBuffer.put((byte) (APDelta ? 1 : 0));

            mapBuffer.put(RR_referenceRequired.getBytes());
            mapBuffer.put((byte) (referenceRequired ? 1 : 0));

            mapBuffer.put(SM_substitutionMatrix.getBytes());
            mapBuffer.put(substitutionMatrix.getEncodedMatrix());

            mapBuffer.put(TD_tagIdsDictionary.getBytes());
            final byte[] dictionaryBytes = dictionaryToByteArray();
            ITF8.writeUnsignedITF8(dictionaryBytes.length, mapBuffer);
            mapBuffer.put(dictionaryBytes);

            mapBuffer.flip();
            final byte[] mapBytes = new byte[mapBuffer.limit()];
            mapBuffer.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
            outputStream.write(mapBytes);
        }

        encodingMap.write(outputStream);

        { // tag encoding map:
            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(tagEncodingMap.size(), mapBuffer);
            for (final Integer dataSeries : tagEncodingMap.keySet()) {
                ITF8.writeUnsignedITF8(dataSeries, mapBuffer);

                final EncodingDescriptor params = tagEncodingMap.get(dataSeries);
                mapBuffer.put((byte) (0xFF & params.getEncodingID().getId()));
                ITF8.writeUnsignedITF8(params.getEncodingParameters().length, mapBuffer);
                mapBuffer.put(params.getEncodingParameters());
            }
            mapBuffer.flip();
            final byte[] mapBytes = new byte[mapBuffer.limit()];
            mapBuffer.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
            outputStream.write(mapBytes);
        }
    }

}
