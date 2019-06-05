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
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

import java.nio.ByteBuffer;

public final class BetaIntegerEncoding extends CRAMEncoding<Integer> {
    private final int offset;
    private final int bitsPerValue;

    public BetaIntegerEncoding(final int offset, final int bitsPerValue) {
        super(EncodingID.BETA);

        if (bitsPerValue < 0) {
            throw new IllegalArgumentException("Number of bits per value must not be negative");
        } else if (bitsPerValue > 32) {
            throw new IllegalArgumentException("Number of bits per value must be 32 or lower");
        }

        this.offset = offset;
        this.bitsPerValue = bitsPerValue;
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return BetaIntegerEncoding with parameters populated from serializedParams
     */
    public static BetaIntegerEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buffer = ByteBuffer.wrap(serializedParams);
        final int offset = ITF8.readUnsignedITF8(buffer);
        final int bitLimit = ITF8.readUnsignedITF8(buffer);
        return new BetaIntegerEncoding(offset, bitLimit);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES * 2);
        ITF8.writeUnsignedITF8(offset, buffer);
        ITF8.writeUnsignedITF8(bitsPerValue, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new BetaIntegerCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                offset,
                bitsPerValue);
    }

    @Override
    public String toString() {
        return String.format("Offset: %d BitsPerValue: %d", offset, bitsPerValue);
    }

}
