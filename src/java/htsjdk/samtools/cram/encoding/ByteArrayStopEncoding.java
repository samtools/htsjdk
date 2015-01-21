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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public class ByteArrayStopEncoding implements Encoding<byte[]> {
    public final static EncodingID ID = EncodingID.BYTE_ARRAY_STOP;
    private byte stopByte = 0;
    private int externalId;

    public ByteArrayStopEncoding() {
    }

    @Override
    public EncodingID id() {
        return ID;
    }

    public ByteArrayStopEncoding(byte stopByte, int externalId) {
        this.stopByte = stopByte;
        this.externalId = externalId;
    }

    public static EncodingParams toParam(byte stopByte, int externalId) {
        ByteArrayStopEncoding e = new ByteArrayStopEncoding(stopByte,
                externalId);
        EncodingParams params = new EncodingParams(ID, e.toByteArray());
        return params;
    }

    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(stopByte);
        ByteBufferUtils.writeUnsignedITF8(externalId, buf);

        buf.flip();
        byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    public void fromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        stopByte = buf.get();
        externalId = ByteBufferUtils.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<byte[]> buildCodec(Map<Integer, InputStream> inputMap,
                                       Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        InputStream is = inputMap == null ? null : inputMap.get(externalId);
        ExposedByteArrayOutputStream os = outputMap == null ? null : outputMap
                .get(externalId);
        return new ByteArrayStopCodec(stopByte, is, os);
    }

    public static class ByteArrayStopCodec extends AbstractBitCodec<byte[]> {

        private int stop;
        private InputStream is;
        private OutputStream os;
        private ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
        private int b;

        public ByteArrayStopCodec(byte stopByte, InputStream is, OutputStream os) {
            this.stop = 0xFF & stopByte;
            this.is = is;
            this.os = os;
        }

        @Override
        public byte[] read(BitInputStream bis) throws IOException {
            readingBAOS.reset();
            while ((b = is.read()) != -1 && b != stop)
                readingBAOS.write(b);

            return readingBAOS.toByteArray();
        }

        @Override
        public byte[] read(BitInputStream bis, int len) throws IOException {
            throw new RuntimeException("Not implemented.");
        }

        @Override
        public long write(BitOutputStream bos, byte[] object)
                throws IOException {
            os.write(object);
            os.write(stop);
            return object.length + 1;
        }

        @Override
        public long numberOfBits(byte[] object) {
            return object.length + 1;
        }

    }
}
