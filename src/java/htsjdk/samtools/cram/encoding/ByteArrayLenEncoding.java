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
import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class ByteArrayLenEncoding implements Encoding<byte[]> {
    public final static EncodingID ID = EncodingID.BYTE_ARRAY_LEN;
    Encoding<Integer> lenEncoding;
    Encoding<byte[]> byteEncoding;

    public ByteArrayLenEncoding() {
    }

    @Override
    public EncodingID id() {
        return ID;
    }

    public static EncodingParams toParam(EncodingParams lenParams,
                                         EncodingParams byteParams) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write((byte) lenParams.id.ordinal());
            ByteBufferUtils.writeUnsignedITF8(lenParams.params.length, baos);
            baos.write(lenParams.params);

            baos.write((byte) byteParams.id.ordinal());
            ByteBufferUtils.writeUnsignedITF8(byteParams.params.length, baos);
            baos.write(byteParams.params);
        } catch (IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return new EncodingParams(ID, baos.toByteArray());
//		ByteBuffer buf = ByteBuffer.allocate(1024);
//		buf.put((byte) lenParams.id.ordinal());
//		ByteBufferUtils.writeUnsignedITF8(lenParams.params.length, buf);
//		buf.put(lenParams.params);
//
//		buf.put((byte) byteParams.id.ordinal());
//		ByteBufferUtils.writeUnsignedITF8(byteParams.params.length, buf);
//		buf.put(byteParams.params);
//
//		buf.flip();
//		byte[] data = new byte[buf.limit()];
//		buf.get(data);
//
//		EncodingParams params = new EncodingParams(ID, data);
//		return params;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write((byte) lenEncoding.id().ordinal());
            byte[] lenBytes = lenEncoding.toByteArray();
            ByteBufferUtils.writeUnsignedITF8(lenBytes.length, baos);
            baos.write(lenBytes);

            baos.write((byte) byteEncoding.id().ordinal());
            byte[] byteBytes = byteEncoding.toByteArray();
            ByteBufferUtils.writeUnsignedITF8(byteBytes.length, baos);
            baos.write(byteBytes);
        } catch (IOException e) {
            throw new RuntimeException("It never happened. ");
        }
        return baos.toByteArray();

//		ByteBuffer buf = ByteBuffer.allocate(1024);
//		buf.put((byte) lenEncoding.id().ordinal());
//		byte[] lenBytes = lenEncoding.toByteArray();
//		ByteBufferUtils.writeUnsignedITF8(lenBytes.length, buf);
//		buf.put(lenBytes);
//
//		buf.put((byte) byteEncoding.id().ordinal());
//		byte[] byteBytes = lenEncoding.toByteArray();
//		ByteBufferUtils.writeUnsignedITF8(byteBytes.length, buf);
//		buf.put(byteBytes);
//
//		buf.flip();
//		byte[] array = new byte[buf.limit()];
//		buf.get(array);
//
//		return array;
    }

    public void fromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);

        EncodingFactory f = new EncodingFactory();

        EncodingID lenID = EncodingID.values()[buf.get()];
        lenEncoding = f.createEncoding(DataSeriesType.INT, lenID);
        int len = ByteBufferUtils.readUnsignedITF8(buf);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        lenEncoding.fromByteArray(bytes);

        EncodingID byteID = EncodingID.values()[buf.get()];
        byteEncoding = f.createEncoding(DataSeriesType.BYTE_ARRAY, byteID);
        len = ByteBufferUtils.readUnsignedITF8(buf);
        bytes = new byte[len];
        buf.get(bytes);
        byteEncoding.fromByteArray(bytes);
    }

    @Override
    public BitCodec<byte[]> buildCodec(Map<Integer, InputStream> inputMap,
                                       Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new ByteArrayLenCodec(
                lenEncoding.buildCodec(inputMap, outputMap),
                byteEncoding.buildCodec(inputMap, outputMap));
    }

    private static class ByteArrayLenCodec extends AbstractBitCodec<byte[]> {
        private BitCodec<Integer> lenCodec;
        private BitCodec<byte[]> byteCodec;

        public ByteArrayLenCodec(BitCodec<Integer> lenCodec,
                                 BitCodec<byte[]> byteCodec) {
            super();
            this.lenCodec = lenCodec;
            this.byteCodec = byteCodec;
        }

        @Override
        public byte[] read(BitInputStream bis) throws IOException {
            int len = lenCodec.read(bis);
            return byteCodec.read(bis, len);
        }

        @Override
        public byte[] read(BitInputStream bis, int len) throws IOException {
            throw new RuntimeException("Not implemented.");
        }

        @Override
        public long write(BitOutputStream bos, byte[] object)
                throws IOException {
            long len = lenCodec.write(bos, object.length);
            len += byteCodec.write(bos, object);
            return len;
        }

        @Override
        public long numberOfBits(byte[] object) {
            return lenCodec.numberOfBits(object.length)
                    + byteCodec.numberOfBits(object);
        }

    }
}
