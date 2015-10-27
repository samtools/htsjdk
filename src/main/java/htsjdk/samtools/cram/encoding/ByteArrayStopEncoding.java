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
 * distributed under the License inputStream distributed on an "AS IS" BASIS,
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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public class ByteArrayStopEncoding implements Encoding<byte[]> {
    private final static EncodingID ID = EncodingID.BYTE_ARRAY_STOP;
    private byte stopByte = 0;
    private int externalId;

    public ByteArrayStopEncoding() {
    }

    @Override
    public EncodingID id() {
        return ID;
    }

    private ByteArrayStopEncoding(final byte stopByte, final int externalId) {
        this.stopByte = stopByte;
        this.externalId = externalId;
    }

    public static EncodingParams toParam(final byte stopByte, final int externalId) {
        final ByteArrayStopEncoding e = new ByteArrayStopEncoding(stopByte,
                externalId);
        return new EncodingParams(ID, e.toByteArray());
    }

    public byte[] toByteArray() {
        final ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(stopByte);
        ITF8.writeUnsignedITF8(externalId, buf);

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    public void fromByteArray(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        stopByte = buf.get();
        externalId = ITF8.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<byte[]> buildCodec(final Map<Integer, InputStream> inputMap,
                                       final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        final InputStream is = inputMap == null ? null : inputMap.get(externalId);
        final ExposedByteArrayOutputStream os = outputMap == null ? null : outputMap
                .get(externalId);
        return new ByteArrayStopCodec(stopByte, is, os);
    }

    public static class ByteArrayStopCodec extends AbstractBitCodec<byte[]> {

        private final int stop;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
        private int b;

        public ByteArrayStopCodec(final byte stopByte, final InputStream inputStream, final OutputStream outputStream) {
            this.stop = 0xFF & stopByte;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public byte[] read(final BitInputStream bitInputStream) throws IOException {
            readingBAOS.reset();
            while ((b = inputStream.read()) != -1 && b != stop)
                readingBAOS.write(b);

            return readingBAOS.toByteArray();
        }

        @Override
        public byte[] read(final BitInputStream bitInputStream, final int length) throws IOException {
            throw new RuntimeException("Not implemented.");
        }

        @Override
        public long write(final BitOutputStream bitOutputStream, final byte[] object)
                throws IOException {
            outputStream.write(object);
            outputStream.write(stop);
            return object.length + 1;
        }

        @Override
        public long numberOfBits(final byte[] object) {
            return object.length + 1;
        }

    }
}
