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
package htsjdk.samtools.cram.encoding.experimental;

import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class GolombLongEncoding extends ExperimentalEncoding<Long> {
    private final int offset;
    private final int m;

    private GolombLongEncoding(final int offset, final int m) {
        super(EncodingID.GOLOMB);
        this.offset = offset;
        this.m = m;
    }

    public static GolombLongEncoding fromParams(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final int offset = ITF8.readUnsignedITF8(buffer);
        final int m = ITF8.readUnsignedITF8(buffer);
        return new GolombLongEncoding(offset, m);
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES * 2);
        ITF8.writeUnsignedITF8(offset, buffer);
        ITF8.writeUnsignedITF8(m, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public BitCodec<Long> buildCodec(final Map<Integer, InputStream> inputMap,
                                     final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new GolombLongCodec(offset, m);
    }
}
