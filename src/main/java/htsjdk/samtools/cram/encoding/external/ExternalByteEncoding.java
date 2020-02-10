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
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

public class ExternalByteEncoding extends ExternalEncoding<Byte> {
    public ExternalByteEncoding(final int externalBlockContentId) {
        super(externalBlockContentId);
    }

    public static ExternalByteEncoding fromParams(byte[] params) {
        final int contentId = ITF8.readUnsignedITF8(params);
        return new ExternalByteEncoding(contentId);
    }

    @Override
    public CRAMCodec<Byte> buildCodec(final BitInputStream coreBlockInputStream,
                                      final BitOutputStream coreBlockOutputStream,
                                      final Map<Integer, ByteArrayInputStream> externalBlockInputMap,
                                      final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        final ByteArrayInputStream inputStream = externalBlockInputMap == null ? null : externalBlockInputMap.get(externalBlockContentId);
        final ByteArrayOutputStream outputStream = externalBlockOutputMap == null ? null : externalBlockOutputMap.get(externalBlockContentId);
        return new ExternalByteCodec(inputStream, outputStream);
    }
}
