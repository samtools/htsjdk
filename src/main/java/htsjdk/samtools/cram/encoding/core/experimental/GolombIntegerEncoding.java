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

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;

import java.nio.ByteBuffer;

public final class GolombIntegerEncoding extends ExperimentalEncoding<Integer> {
    private final int offset;
    private final int m;

    private GolombIntegerEncoding(final int offset, final int m) {
        super(EncodingID.GOLOMB);
        this.offset = offset;
        this.m = m;
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return GolombIntegerEncoding with parameters populated from serializedParams
     */
    public static GolombIntegerEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buffer = ByteBuffer.wrap(serializedParams);
        final int offset = ITF8.readUnsignedITF8(buffer);
        final int m = ITF8.readUnsignedITF8(buffer);
        return new GolombIntegerEncoding(offset, m);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES * 2);
        ITF8.writeUnsignedITF8(offset, buffer);
        ITF8.writeUnsignedITF8(m, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new GolombIntegerCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                offset,
                m);
    }

    @Override
    public String toString() {
        return String.format("Offset: %d m: %d", offset, m);
    }

}
