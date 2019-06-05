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
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;

import java.nio.ByteBuffer;

public final class GammaIntegerEncoding extends CRAMEncoding<Integer> {
    private final int offset;

    GammaIntegerEncoding(final int offset) {
        super(EncodingID.GAMMA);
        this.offset = offset;
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return GammaIntegerEncoding with parameters populated from serializedParams
     */
    public static GammaIntegerEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final int offset = ITF8.readUnsignedITF8(serializedParams);
        return new GammaIntegerEncoding(offset);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES);
        ITF8.writeUnsignedITF8(offset, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new GammaIntegerCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                offset);
    }

    @Override
    public String toString() {
        return String.format("Offset: %d", offset);
    }
}
