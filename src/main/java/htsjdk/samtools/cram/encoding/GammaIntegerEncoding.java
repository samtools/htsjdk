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

import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class GammaIntegerEncoding implements Encoding<Integer> {
    private static final EncodingID ENCODING_ID = EncodingID.GAMMA;
    private int offset;

    public GammaIntegerEncoding() {
        this(0);
    }

    public GammaIntegerEncoding(final int offset) {
        this.offset = offset;
    }

    @Override
    public EncodingID id() {
        return ENCODING_ID;
    }

    public static EncodingParams toParam(final int offset) {
        final GammaIntegerEncoding gammaIntegerEncoding = new GammaIntegerEncoding();
        gammaIntegerEncoding.offset = offset;
        return new EncodingParams(ENCODING_ID, gammaIntegerEncoding.toByteArray());
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        ITF8.writeUnsignedITF8(offset, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public void fromByteArray(final byte[] data) {
        offset = ITF8.readUnsignedITF8(data);
    }

    @Override
    public BitCodec<Integer> buildCodec(final Map<Integer, InputStream> inputMap,
                                        final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new GammaIntegerCodec(offset);
    }

}
