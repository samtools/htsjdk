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
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CompressionHeader {
    private static final String RN_readNamesIncluded = "RN";
    private static final String AP_alignmentPositionIsDelta = "AP";
    private static final String RR_referenceRequired = "RR";
    private static final String TD_tagIdsDictionary = "TD";
    private static final String SM_substitutionMatrix = "SM";

    private static final Log log = Log.getInstance(CompressionHeader.class);

    public boolean readNamesIncluded;
    public boolean APDelta = true;
    private boolean referenceRequired = true;

    public Map<DataSeries, EncodingParams> encodingMap;
    public Map<Integer, EncodingParams> tMap;
    public final Map<Integer, ExternalCompressor> externalCompressors = new HashMap<Integer, ExternalCompressor>();

    public SubstitutionMatrix substitutionMatrix;

    public List<Integer> externalIds;

    public byte[][][] dictionary;

    public CompressionHeader() {
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

        { // encoding map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buffer);
            encodingMap = new TreeMap<>();

            for (int i = 0; i < mapSize; i++) {
                final String dataSeriesAbbreviation = new String(new byte[]{buffer.get(), buffer.get()});
                final DataSeries dataSeries = DataSeries.byCanonicalName(dataSeriesAbbreviation);

                final EncodingID id = EncodingID.values()[buffer.get()];
                final int paramLen = ITF8.readUnsignedITF8(buffer);
                final byte[] paramBytes = new byte[paramLen];
                buffer.get(paramBytes);

                encodingMap.put(dataSeries, new EncodingParams(id, paramBytes));

                log.debug(String.format("FOUND ENCODING: %s, %s, %s.",
                        dataSeries.name(), id.name(),
                        Arrays.toString(Arrays.copyOf(paramBytes, 20))));
            }
        }

        { // tag encoding map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buf = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buf);
            tMap = new TreeMap<Integer, EncodingParams>();
            for (int i = 0; i < mapSize; i++) {
                final int key = ITF8.readUnsignedITF8(buf);

                final EncodingID id = EncodingID.values()[buf.get()];
                final int paramLen = ITF8.readUnsignedITF8(buf);
                final byte[] paramBytes = new byte[paramLen];
                buf.get(paramBytes);

                tMap.put(key, new EncodingParams(id, paramBytes));
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

        { // encoding map:
            int size = 0;
            for (final DataSeries dataSeries : encodingMap.keySet()) {
                if (encodingMap.get(dataSeries).id != EncodingID.NULL)
                    size++;
            }

            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(size, mapBuffer);
            for (final DataSeries dataSeries : encodingMap.keySet()) {
                if (encodingMap.get(dataSeries).id == EncodingID.NULL)
                    continue;

                final String dataSeriesAbbreviation = dataSeries.getCanonicalName();
                mapBuffer.put((byte) dataSeriesAbbreviation.charAt(0));
                mapBuffer.put((byte) dataSeriesAbbreviation.charAt(1));

                final EncodingParams params = encodingMap.get(dataSeries);
                mapBuffer.put((byte) (0xFF & params.id.getId()));
                ITF8.writeUnsignedITF8(params.params.length, mapBuffer);
                mapBuffer.put(params.params);
            }
            mapBuffer.flip();
            final byte[] mapBytes = new byte[mapBuffer.limit()];
            mapBuffer.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
            outputStream.write(mapBytes);
        }

        { // tag encoding map:
            final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(tMap.size(), mapBuffer);
            for (final Integer dataSeries : tMap.keySet()) {
                ITF8.writeUnsignedITF8(dataSeries, mapBuffer);

                final EncodingParams params = tMap.get(dataSeries);
                mapBuffer.put((byte) (0xFF & params.id.getId()));
                ITF8.writeUnsignedITF8(params.params.length, mapBuffer);
                mapBuffer.put(params.params);
            }
            mapBuffer.flip();
            final byte[] mapBytes = new byte[mapBuffer.limit()];
            mapBuffer.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
            outputStream.write(mapBytes);
        }
    }

    /**
     * Read a COMPRESSION_HEADER Block from an InputStream and return its contents as a CompressionHeader
     * We do this instead of reading the InputStream directly because the Block content may be compressed
     *
     * @param cramVersion the CRAM version
     * @param blockStream the stream to read from
     * @return a new CompressionHeader from the input
     */
    public static CompressionHeader read(final int cramVersion, final InputStream blockStream) {
        final Block block = Block.read(cramVersion, blockStream);
        if (block.getContentType() != BlockContentType.COMPRESSION_HEADER)
            throw new RuntimeIOException("Compression Header Block expected, found: " + block.getContentType().name());

        try (final ByteArrayInputStream internalStream = new ByteArrayInputStream(block.getUncompressedContent())) {
            final CompressionHeader header = new CompressionHeader();
            header.internalRead(internalStream);
            return header;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
