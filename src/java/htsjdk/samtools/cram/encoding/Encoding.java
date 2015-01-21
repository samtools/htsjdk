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
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.InputStream;
import java.util.Map;


public interface Encoding<T> {

    public EncodingID id();

    public byte[] toByteArray();

    public void fromByteArray(byte[] data);

    public BitCodec<T> buildCodec(Map<Integer, InputStream> inputMap,
                                  Map<Integer, ExposedByteArrayOutputStream> outputMap);

}
