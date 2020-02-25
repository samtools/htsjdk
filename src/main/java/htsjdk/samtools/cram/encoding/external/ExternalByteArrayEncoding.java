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
package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

public final class ExternalByteArrayEncoding extends ExternalEncoding<byte[]> {

    public ExternalByteArrayEncoding(final int externalBlockContentId) {
        super(externalBlockContentId);
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return ExternalByteArrayEncoding with parameters populated from serializedParams
     */
    public static ExternalByteArrayEncoding fromSerializedEncodingParams(byte[] serializedParams) {
        final int contentId = ITF8.readUnsignedITF8(serializedParams);
        return new ExternalByteArrayEncoding(contentId);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        return ITF8.writeUnsignedITF8(externalBlockContentId);
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        final ByteArrayInputStream is = sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getExternalInputStream(externalBlockContentId);
        final ByteArrayOutputStream os = sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getExternalOutputStream(externalBlockContentId);
        return new ExternalByteArrayCodec(is, os);
    }

    @Override
    public String toString() {
        return String.format("Content ID: %d", externalBlockContentId);
    }


}
