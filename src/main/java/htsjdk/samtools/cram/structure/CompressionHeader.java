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

import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.*;

public class CompressionHeader {
    private static final Log log = Log.getInstance(CompressionHeader.class);

    private static final String RN_readNamesIncluded = "RN";
    private static final String AP_alignmentPositionIsDelta = "AP";
    private static final String RR_referenceRequired = "RR";
    private static final String TD_tagIdsDictionary = "TD";
    private static final String SM_substitutionMatrix = "SM";

    //TODO: add public methods for readNamesIncluded and referenceRequired
    public boolean readNamesIncluded = true;
    private boolean APDelta = true;
    private boolean referenceRequired = true;

    private CompressionHeaderEncodingMap encodingMap;

    //TODO: Move the tMap into CompressionHeaderEncodingMap
    public Map<Integer, EncodingDescriptor> tMap;
    public SubstitutionMatrix substitutionMatrix;
    public byte[][][] dictionary;

    public CompressionHeader() {
        encodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
        tMap = new TreeMap<>();
    }

    // TODO: the path should be interpreted by the caller so it only
    // TODO: happens once for the (container ?) factory rather than once
    // TODO: per container
    /**
     * Create a compression header using the given encodingStrategy.
     * @param encodingStrategy
     */
    public CompressionHeader(final CRAMEncodingStrategy encodingStrategy) {
        final String customCompressionMapPath = encodingStrategy.getCustomCompressionMapPath();
        if (customCompressionMapPath.isEmpty()) {
            encodingMap = new CompressionHeaderEncodingMap(encodingStrategy);
        } else {
            try {
                encodingMap = CompressionHeaderEncodingMap.readFromPath(IOUtil.getPath(customCompressionMapPath));
            } catch (final IOException e) {
                throw new RuntimeIOException("Failure reading custom encoding map from Path", e);
            }
        }
        tMap = new TreeMap<>();
    }

    /**
     * Read a COMPRESSION_HEADER Block from an InputStream and return its contents as a CompressionHeader.
     *
     * @param cramVersion the CRAM version
     * @param blockStream the stream to read from
     * @return a new CompressionHeader
     */
    public CompressionHeader(final int cramVersion, final InputStream blockStream) {
        final Block compressionHeaderBlock = Block.read(cramVersion, blockStream);
        if (compressionHeaderBlock.getContentType() != BlockContentType.COMPRESSION_HEADER)
            throw new RuntimeIOException("Compression Header Block expected, found: " + compressionHeaderBlock.getContentType().name());

        try (final ByteArrayInputStream internalStream = new ByteArrayInputStream(compressionHeaderBlock.getUncompressedContent())) {
            internalRead(internalStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public CompressionHeaderEncodingMap getEncodingMap() { return encodingMap; }

    public void setIsCoordinateSorted(final boolean coordinateSorted) {
        APDelta = coordinateSorted;
    }

    /**
    * Return true if the header is for a coordinate-sorted CRAM stream.
    * As required by the spec, we set the AP Delta flag according to that criterion,
    * so checking that flag is equivalent.
    * @return the value of the APDelta flag
    */
    public boolean isCoordinateSorted() {
        return APDelta;
    }

    public  void setTagIdDictionary(final byte[][][] dictionary) {
        this.dictionary = dictionary;
    }

    public void setSubstitutionMatrix(final SubstitutionMatrix substitutionMatrix) {
        this.substitutionMatrix = substitutionMatrix;
    }

    private byte[][][] parseDictionary(final byte[] bytes) {
        final List<List<byte[]>> dictionary = new ArrayList<List<byte[]>>();
        {
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
        }

        int maxWidth = 0;
        for (final List<byte[]> list : dictionary)
            maxWidth = Math.max(maxWidth, list.size());

        final byte[][][] array = new byte[dictionary.size()][][];
        for (int i = 0; i < dictionary.size(); i++) {
            final List<byte[]> list = dictionary.get(i);
            array[i] = list.toArray(new byte[list.size()][]);
        }

        return array;
    }

    private byte[] dictionaryToByteArray() {
        int size = 0;
        for (final byte[][] dictionaryArrayArray : dictionary) {
            for (final byte[] dictionaryArray : dictionaryArrayArray) size += dictionaryArray.length;
            size++;
        }

        final byte[] bytes = new byte[size];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        for (final byte[][] dictionaryArrayArray : dictionary) {
            for (final byte[] dictionaryArray : dictionaryArrayArray) buffer.put(dictionaryArray);
            buffer.put((byte) 0);
        }

        return bytes;
    }

    //TODO Unused ?
    public byte[][] getTagIds(final int id) {
        return dictionary[id];
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
                    readNamesIncluded = buffer.get() == 1;
                else if (AP_alignmentPositionIsDelta.equals(key))
                    APDelta = buffer.get() == 1;
                else if (RR_referenceRequired.equals(key))
                    referenceRequired = buffer.get() == 1;
                else if (TD_tagIdsDictionary.equals(key)) {
                    final int size = ITF8.readUnsignedITF8(buffer);
                    final byte[] dictionaryBytes = new byte[size];
                    buffer.get(dictionaryBytes);
                    dictionary = parseDictionary(dictionaryBytes);
                } else if (SM_substitutionMatrix.equals(key)) {
                    // parse subs matrix here:
                    final byte[] matrixBytes = new byte[5];
                    buffer.get(matrixBytes);
                    substitutionMatrix = new SubstitutionMatrix(matrixBytes);
                } else
                    throw new RuntimeException("Unknown preservation map key: "
                            + key);
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
            tMap = new TreeMap<>();
            for (int i = 0; i < mapSize; i++) {
                final int key = ITF8.readUnsignedITF8(buf);

                final EncodingID id = EncodingID.values()[buf.get()];
                final int paramLen = ITF8.readUnsignedITF8(buf);
                final byte[] paramBytes = new byte[paramLen];
                buf.get(paramBytes);

                tMap.put(key, new EncodingDescriptor(id, paramBytes));
            }
        }
    }

    /**
     * Write this CompressionHeader out to an internal OutputStream, wrap it in a Block, and write that
     * Block out to the passed-in OutputStream.
     *
     * @param cramVersion the CRAM major version number
     * @param blockStream the stream to write to
     */
    public void write(final Version cramVersion, final OutputStream blockStream) {
        try (final ByteArrayOutputStream internalOutputStream = new ByteArrayOutputStream()) {
            internalWrite(internalOutputStream);
            final Block block = Block.createRawCompressionHeaderBlock(internalOutputStream.toByteArray());
            block.write(cramVersion.major, blockStream);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private void internalWrite(final OutputStream outputStream) throws IOException {

        { // preservation map:
            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(5, mapBuffer);

            mapBuffer.put(RN_readNamesIncluded.getBytes());
            mapBuffer.put((byte) (readNamesIncluded ? 1 : 0));

            mapBuffer.put(AP_alignmentPositionIsDelta.getBytes());
            mapBuffer.put((byte) (APDelta ? 1 : 0));

            mapBuffer.put(RR_referenceRequired.getBytes());
            mapBuffer.put((byte) (referenceRequired ? 1 : 0));

            mapBuffer.put(SM_substitutionMatrix.getBytes());
            mapBuffer.put(substitutionMatrix.getEncodedMatrix());

            mapBuffer.put(TD_tagIdsDictionary.getBytes());
            {
                final byte[] dictionaryBytes = dictionaryToByteArray();
                ITF8.writeUnsignedITF8(dictionaryBytes.length, mapBuffer);
                mapBuffer.put(dictionaryBytes);
            }

            mapBuffer.flip();
            final byte[] mapBytes = new byte[mapBuffer.limit()];
            mapBuffer.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
            outputStream.write(mapBytes);
        }

        encodingMap.write(outputStream);

        { // tag encoding map:
            //TOTO: fix this static allocation size
            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(tMap.size(), mapBuffer);
            for (final Integer dataSeries : tMap.keySet()) {
                ITF8.writeUnsignedITF8(dataSeries, mapBuffer);

                final EncodingDescriptor params = tMap.get(dataSeries);
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

    public void addTagEncoding(final int tagId, final ExternalCompressor compressor, final EncodingDescriptor params) {
        encodingMap.addTagBlockCompression(tagId, compressor);
        tMap.put(tagId, params);
    }


}
