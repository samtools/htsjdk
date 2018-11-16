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
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class ExternalByteArrayEncoding extends CRAMEncoding<byte[]> {
    private final int externalBlockContentId;

    public ExternalByteArrayEncoding(final int externalBlockContentId) {
        super(EncodingID.EXTERNAL);
        this.externalBlockContentId = externalBlockContentId;
    }

    public static ExternalByteArrayEncoding fromParams(byte[] params) {
        final int contentId = ITF8.readUnsignedITF8(params);
        return new ExternalByteArrayEncoding(contentId);
    }

    @Override
    public byte[] toByteArray() {
        return ITF8.writeUnsignedITF8(externalBlockContentId);
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final BitInputStream coreBlockInputStream,
                                        final BitOutputStream coreBlockOutputStream,
                                        final Map<Integer, InputStream> externalBlockInputMap,
                                        final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        final InputStream inputStream = externalBlockInputMap == null ? null : externalBlockInputMap.get(externalBlockContentId);
        final OutputStream outputStream = externalBlockOutputMap == null ? null : externalBlockOutputMap.get(externalBlockContentId);
        return new ExternalByteArrayCodec(inputStream, outputStream);
    }

}
