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
package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.CramCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class GammaIntegerEncoding extends ExperimentalEncoding<Integer> {
    private final int offset;

    private GammaIntegerEncoding(final int offset) {
        super(EncodingID.GAMMA);
        this.offset = offset;
    }

    public static GammaIntegerEncoding fromParams(final byte[] data) {
        final int offset = ITF8.readUnsignedITF8(data);
        return new GammaIntegerEncoding(offset);
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES);
        ITF8.writeUnsignedITF8(offset, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public CramCodec<Integer> buildCodec(final BitInputStream coreBlockInputStream,
                                         final BitOutputStream coreBlockOutputStream,
                                         final Map<Integer, InputStream> externalBlockInputMap,
                                         final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        return new GammaIntegerCodec(coreBlockInputStream, coreBlockOutputStream, offset);
    }

}
