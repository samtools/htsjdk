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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.util.Map;

public class ExternalByteArrayEncoding implements Encoding<byte[]> {
    private static final EncodingID encodingId = EncodingID.EXTERNAL;
    private int contentId = -1;

    public ExternalByteArrayEncoding() {
    }

    public static EncodingParams toParam(final int contentId) {
        final ExternalByteArrayEncoding e = new ExternalByteArrayEncoding();
        e.contentId = contentId;
        return new EncodingParams(encodingId, e.toByteArray());
    }

    public byte[] toByteArray() {
        return ITF8.writeUnsignedITF8(contentId);
    }

    public void fromByteArray(final byte[] data) {
        contentId = ITF8.readUnsignedITF8(data);
    }

    @Override
    public BitCodec<byte[]> buildCodec(final Map<Integer, InputStream> inputMap,
                                       final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        final InputStream inputStream = inputMap == null ? null : inputMap.get(contentId);
        final ExposedByteArrayOutputStream outputStream = outputMap == null ? null : outputMap
                .get(contentId);
        return new ExternalByteArrayCodec(outputStream, inputStream);
    }

    @Override
    public EncodingID id() {
        return encodingId;
    }

}
