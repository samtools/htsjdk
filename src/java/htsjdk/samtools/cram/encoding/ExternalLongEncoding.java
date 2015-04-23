/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.util.Map;

public class ExternalLongEncoding implements Encoding<Long> {
    private static final EncodingID encodingId = EncodingID.EXTERNAL;
    private int contentId = -1;

    public ExternalLongEncoding() {
    }

    public static EncodingParams toParam(int contentId) {
        ExternalLongEncoding e = new ExternalLongEncoding();
        e.contentId = contentId;
        return new EncodingParams(encodingId, e.toByteArray());
    }

    public byte[] toByteArray() {
        return ITF8.writeUnsignedITF8(contentId);
    }

    public void fromByteArray(byte[] data) {
        contentId = ITF8.readUnsignedITF8(data);
    }

    @Override
    public BitCodec<Long> buildCodec(Map<Integer, InputStream> inputMap,
                                     Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        InputStream is = inputMap == null ? null : inputMap.get(contentId);
        ExposedByteArrayOutputStream os = outputMap == null ? null : outputMap.get(contentId);
        return new ExternalLongCodec(os, is);
    }

    @Override
    public EncodingID id() {
        return encodingId;
    }

}
