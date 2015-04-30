/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
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
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write((byte) lenParams.id.ordinal());
            ITF8.writeUnsignedITF8(lenParams.params.length, baos);
            baos.write(lenParams.params);

            baos.write((byte) byteParams.id.ordinal());
            ITF8.writeUnsignedITF8(byteParams.params.length, baos);
            baos.write(byteParams.params);
        } catch (final IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return new EncodingParams(ID, baos.toByteArray());
    }

    public byte[] toByteArray() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write((byte) lenEncoding.id().ordinal());
            final byte[] lenBytes = lenEncoding.toByteArray();
            ITF8.writeUnsignedITF8(lenBytes.length, baos);
            baos.write(lenBytes);

            baos.write((byte) byteEncoding.id().ordinal());
            final byte[] byteBytes = byteEncoding.toByteArray();
            ITF8.writeUnsignedITF8(byteBytes.length, baos);
            baos.write(byteBytes);
        } catch (final IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return baos.toByteArray();
    }

    public void fromByteArray(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);

        final EncodingFactory f = new EncodingFactory();

        final EncodingID lenID = EncodingID.values()[buf.get()];
        lenEncoding = f.createEncoding(DataSeriesType.INT, lenID);
        int len = ITF8.readUnsignedITF8(buf);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        lenEncoding.fromByteArray(bytes);

        final EncodingID byteID = EncodingID.values()[buf.get()];
        byteEncoding = f.createEncoding(DataSeriesType.BYTE_ARRAY, byteID);
        len = ITF8.readUnsignedITF8(buf);
        bytes = new byte[len];
        buf.get(bytes);
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
        public byte[] read(final BitInputStream bis) throws IOException {
            final int len = lenCodec.read(bis);
            return byteCodec.read(bis, len);
        }

        @Override
        public byte[] read(final BitInputStream bis, final int len) throws IOException {
            throw new RuntimeException("Not implemented.");
        }

        @Override
        public long write(final BitOutputStream bos, final byte[] object)
                throws IOException {
            long len = lenCodec.write(bos, object.length);
            len += byteCodec.write(bos, object);
            return len;
        }

        @Override
        public long numberOfBits(final byte[] object) {
            return lenCodec.numberOfBits(object.length)
                    + byteCodec.numberOfBits(object);
        }

    }
}
