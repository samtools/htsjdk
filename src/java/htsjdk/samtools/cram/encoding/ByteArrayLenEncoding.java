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
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class ByteArrayLenEncoding implements Encoding<byte[]> {
    private final static EncodingID ID = EncodingID.BYTE_ARRAY_LEN;
    private Encoding<Integer> lenEncoding;
    private Encoding<byte[]> byteEncoding;

    public ByteArrayLenEncoding() {
    }

    @Override
    public EncodingID id() {
        return ID;
    }

    public static EncodingParams toParam(final EncodingParams lenParams,
                                         final EncodingParams byteParams) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write((byte) lenParams.id.ordinal());
            ITF8.writeUnsignedITF8(lenParams.params.length, byteArrayOutputStream);
            byteArrayOutputStream.write(lenParams.params);

            byteArrayOutputStream.write((byte) byteParams.id.ordinal());
            ITF8.writeUnsignedITF8(byteParams.params.length, byteArrayOutputStream);
            byteArrayOutputStream.write(byteParams.params);
        } catch (final IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return new EncodingParams(ID, byteArrayOutputStream.toByteArray());
    }

    public byte[] toByteArray() {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write((byte) lenEncoding.id().ordinal());
            final byte[] lenBytes = lenEncoding.toByteArray();
            ITF8.writeUnsignedITF8(lenBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(lenBytes);

            byteArrayOutputStream.write((byte) byteEncoding.id().ordinal());
            final byte[] byteBytes = byteEncoding.toByteArray();
            ITF8.writeUnsignedITF8(byteBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(byteBytes);
        } catch (final IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return byteArrayOutputStream.toByteArray();
    }

    public void fromByteArray(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);

        final EncodingFactory encodingFactory = new EncodingFactory();

        final EncodingID lenID = EncodingID.values()[buffer.get()];
        lenEncoding = encodingFactory.createEncoding(DataSeriesType.INT, lenID);
        int length = ITF8.readUnsignedITF8(buffer);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        lenEncoding.fromByteArray(bytes);

        final EncodingID byteID = EncodingID.values()[buffer.get()];
        byteEncoding = encodingFactory.createEncoding(DataSeriesType.BYTE_ARRAY, byteID);
        length = ITF8.readUnsignedITF8(buffer);
        bytes = new byte[length];
        buffer.get(bytes);
        byteEncoding.fromByteArray(bytes);
    }

    @Override
    public BitCodec<byte[]> buildCodec(final Map<Integer, InputStream> inputMap,
                                       final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new ByteArrayLenCodec(
                lenEncoding.buildCodec(inputMap, outputMap),
                byteEncoding.buildCodec(inputMap, outputMap));
    }

    private static class ByteArrayLenCodec extends AbstractBitCodec<byte[]> {
        private final BitCodec<Integer> lenCodec;
        private final BitCodec<byte[]> byteCodec;

        public ByteArrayLenCodec(final BitCodec<Integer> lenCodec,
                                 final BitCodec<byte[]> byteCodec) {
            super();
            this.lenCodec = lenCodec;
            this.byteCodec = byteCodec;
        }

        @Override
        public byte[] read(final BitInputStream bitInputStream) throws IOException {
            final int length = lenCodec.read(bitInputStream);
            return byteCodec.read(bitInputStream, length);
        }

        @Override
        public byte[] read(final BitInputStream bitInputStream, final int length) throws IOException {
            throw new RuntimeException("Not implemented.");
        }

        @Override
        public long write(final BitOutputStream bitOutputStream, final byte[] object)
                throws IOException {
            long length = lenCodec.write(bitOutputStream, object.length);
            length += byteCodec.write(bitOutputStream, object);
            return length;
        }

        @Override
        public long numberOfBits(final byte[] object) {
            return lenCodec.numberOfBits(object.length)
                    + byteCodec.numberOfBits(object);
        }

    }
}
