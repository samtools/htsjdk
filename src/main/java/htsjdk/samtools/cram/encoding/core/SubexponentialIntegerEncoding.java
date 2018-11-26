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
package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class SubexponentialIntegerEncoding extends CRAMEncoding<Integer> {
    private final int offset;
    private final int k;

    SubexponentialIntegerEncoding(final int offset, final int k) {
        super(EncodingID.SUBEXPONENTIAL);

        if (k < 0) {
            throw new IllegalArgumentException("k parameter must not be negative");
        }

        this.offset = offset;
        this.k = k;
    }

    public static SubexponentialIntegerEncoding fromParams(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final int offset = ITF8.readUnsignedITF8(buffer);
        final int k = ITF8.readUnsignedITF8(buffer);
        return new SubexponentialIntegerEncoding(offset, k);
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES * 2);

        ITF8.writeUnsignedITF8(offset, buffer);
        ITF8.writeUnsignedITF8(k, buffer);
        buffer.flip();
        final byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);

        return bytes;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final BitInputStream coreBlockInputStream,
                                         final BitOutputStream coreBlockOutputStream,
                                         final Map<Integer, ByteArrayInputStream> externalBlockInputMap,
                                         final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        return new SubexponentialIntegerCodec(coreBlockInputStream, coreBlockOutputStream, offset, k);
    }

}
