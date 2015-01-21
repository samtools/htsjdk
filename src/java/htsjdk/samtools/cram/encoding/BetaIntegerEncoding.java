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

import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;


public class BetaIntegerEncoding implements Encoding<Integer> {
    public static final EncodingID ENCODING_ID = EncodingID.BETA;
    private int offset;
    private int bitLimit;

    public BetaIntegerEncoding() {
    }

    public BetaIntegerEncoding(int bitLimit) {
        this.bitLimit = bitLimit;
    }

    @Override
    public EncodingID id() {
        return ENCODING_ID;
    }

    public static EncodingParams toParam(int offset, int bitLimit) {
        BetaIntegerEncoding e = new BetaIntegerEncoding();
        e.offset = offset;
        e.bitLimit = bitLimit;
        return new EncodingParams(ENCODING_ID, e.toByteArray());
    }

    @Override
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(10);
        ByteBufferUtils.writeUnsignedITF8(offset, buf);
        ByteBufferUtils.writeUnsignedITF8(bitLimit, buf);
        buf.flip();
        byte[] array = new byte[buf.limit()];
        buf.get(array);
        return array;
    }

    @Override
    public void fromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        offset = ByteBufferUtils.readUnsignedITF8(buf);
        bitLimit = ByteBufferUtils.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<Integer> buildCodec(Map<Integer, InputStream> inputMap,
                                        Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new BetaIntegerCodec(offset, bitLimit);
    }

}
