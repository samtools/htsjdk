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
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.util.Map;

/**
 * An interface to describe how a data series is encoded.
 * It also has methods to serialize/deserialize to/from byte array and a method to construct
 * a {@link htsjdk.samtools.cram.encoding.BitCodec} instance.
 *
 * @param <T> data series type
 */
public abstract class Encoding<T> {
    private final EncodingID ENCODING_ID;

    protected Encoding (final EncodingID id) {
        ENCODING_ID = id;
    }

    public EncodingID id() {
        return ENCODING_ID;
    }

    public EncodingParams toParam() {
        return new EncodingParams(id(), toByteArray());
    }

    public abstract byte[] toByteArray();

    public abstract BitCodec<T> buildCodec(Map<Integer, InputStream> inputMap,
                                           Map<Integer, ExposedByteArrayOutputStream> outputMap);

}
