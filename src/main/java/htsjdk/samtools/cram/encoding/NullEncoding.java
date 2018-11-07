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

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.InputStream;
import java.util.Map;

public class NullEncoding<T> extends Encoding<T> {
    public NullEncoding() {
        super(EncodingID.NULL);
    }

    @Override
    public byte[] toByteArray() {
        return new byte[]{};
    }

    @Override
    public CramCodec<T> buildCodec(final BitInputStream coreBlockInputStream,
                                   final BitOutputStream coreBlockOutputStream,
                                   final Map<Integer, InputStream> externalBlockInputMap,
                                   final Map<Integer, ExposedByteArrayOutputStream> externalBlockOutputMap) {
        return new NullCodec<T>();
    }

}
