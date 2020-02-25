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

import htsjdk.samtools.util.StringUtil;

import java.util.Arrays;

/**
 * A class for representing an encoding, including encoding-specific parameters, suitable for
 * serialization to/from a stream.
 */
public class EncodingDescriptor {

    private final EncodingID encodingID;
    private final byte[] encodingParameters;

    /**
     * Representation of an encoding, including untyped encoding-specific parameters in the form of an array
     * of bytes.
     * @param encodingID the encoding ID for this descriptor
     * @param encodingParameters the encoding parameters for this descriptor
     */
    public EncodingDescriptor(final EncodingID encodingID, final byte[] encodingParameters) {
        this.encodingID = encodingID;
        this.encodingParameters = encodingParameters;
    }

    /**
     * @return the {@link EncodingID} for this descriptor.
     */
    public EncodingID getEncodingID() {
        return encodingID;
    }

    /**
     * @return A byte array containing the encoding parameters for this descriptor.
     */
    public byte[] getEncodingParameters() {
        return encodingParameters;
    }

    @Override
    public String toString() {
        return String.format(
                "%s: (%s)",
                getEncodingID().name(),
                StringUtil.bytesToHexString(
                        Arrays.copyOfRange(
                                getEncodingParameters(),0, Math.max(20, getEncodingParameters().length))));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EncodingDescriptor that = (EncodingDescriptor) o;

        if (getEncodingID() != that.getEncodingID()) return false;
        return Arrays.equals(getEncodingParameters(), that.getEncodingParameters());

    }

    @Override
    public int hashCode() {
        int result = getEncodingID().hashCode();
        result = 31 * result + Arrays.hashCode(getEncodingParameters());
        return result;
    }

}
