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
package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ByteArrayStopEncoding extends CRAMEncoding<byte[]> {
    private final byte stopByte;
    private final int externalId;
    private final ByteBuffer buf;

    public ByteArrayStopEncoding(final byte stopByte, final int externalId) {
        super(EncodingID.BYTE_ARRAY_STOP);
        this.stopByte = stopByte;
        this.externalId = externalId;
        this.buf = ByteBuffer.allocate(ITF8.MAX_BYTES + 1);
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return ByteArrayStopEncoding with parameters populated from serializedParams
     */
    public static ByteArrayStopEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buf = ByteBuffer.wrap(serializedParams);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final byte stopByte = buf.get();
        final int externalId = ITF8.readUnsignedITF8(buf);
        return new ByteArrayStopEncoding(stopByte, externalId);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        buf.clear();

        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(stopByte);
        ITF8.writeUnsignedITF8(externalId, buf);

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        final ByteArrayInputStream is = sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getExternalInputStream(externalId);
        final ByteArrayOutputStream os = sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getExternalOutputStream(externalId);
        return new ByteArrayStopCodec(is, os, stopByte);
    }

    @Override
    public String toString() {
        return String.format("Content ID: %d StopByte: %d", externalId, stopByte);
    }
}
