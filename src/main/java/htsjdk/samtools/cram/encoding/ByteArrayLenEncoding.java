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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class ByteArrayLenEncoding extends CRAMEncoding<byte[]> {
    private final CRAMEncoding<Integer> lenEncoding;
    private final CRAMEncoding<byte[]> byteEncoding;

    public ByteArrayLenEncoding(final CRAMEncoding<Integer> lenEncoding, final CRAMEncoding<byte[]> byteEncoding) {
        super(EncodingID.BYTE_ARRAY_LEN);
        this.lenEncoding = lenEncoding;
        this.byteEncoding = byteEncoding;
    }

    public static ByteArrayLenEncoding fromParams(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);

        final EncodingID lenID = EncodingID.values()[buffer.get()];
        final int lenLength = ITF8.readUnsignedITF8(buffer);
        final byte[] lenBytes = new byte[lenLength];
        buffer.get(lenBytes);
        final CRAMEncoding<Integer> lenEncoding = EncodingFactory.createEncoding(DataSeriesType.INT, lenID, lenBytes);

        final EncodingID byteID = EncodingID.values()[buffer.get()];
        final int byteLength = ITF8.readUnsignedITF8(buffer);
        final byte[] byteBytes = new byte[byteLength];
        buffer.get(byteBytes);
        final CRAMEncoding<byte[]> byteEncoding = EncodingFactory.createEncoding(DataSeriesType.BYTE_ARRAY, byteID, byteBytes);

        return new ByteArrayLenEncoding(lenEncoding, byteEncoding);
    }

    @Override
    public byte[] toByteArray() {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write((byte) lenEncoding.id().getId());
            final byte[] lenBytes = lenEncoding.toByteArray();
            ITF8.writeUnsignedITF8(lenBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(lenBytes);

            byteArrayOutputStream.write((byte) byteEncoding.id().getId());
            final byte[] byteBytes = byteEncoding.toByteArray();
            ITF8.writeUnsignedITF8(byteBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(byteBytes);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final BitInputStream coreBlockInputStream,
                                        final BitOutputStream coreBlockOutputStream,
                                        final Map<Integer, InputStream> externalBlockInputMap,
                                        final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        return new ByteArrayLenCodec(
                lenEncoding.buildCodec(coreBlockInputStream, coreBlockOutputStream, externalBlockInputMap, externalBlockOutputMap),
                byteEncoding.buildCodec(coreBlockInputStream, coreBlockOutputStream, externalBlockInputMap, externalBlockOutputMap));
    }

}
