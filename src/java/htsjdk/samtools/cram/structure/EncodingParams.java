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
package htsjdk.samtools.cram.structure;


import java.util.Arrays;

public class EncodingParams {

    public final EncodingID id;
    public final byte[] params;

    public EncodingParams(final EncodingID id, final byte[] params) {
        super();
        this.id = id;
        this.params = params;
    }

    @Override
    public String toString() {
        return id.name() + ":" + javax.xml.bind.DatatypeConverter.printHexBinary(Arrays.copyOfRange(params, 0, Math.max(20, params.length)));
    }

}
