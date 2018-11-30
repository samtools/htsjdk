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
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * An interface to describe how a data series is encoded.
 * It also has methods to serialize/deserialize its parameters to/from a byte array
 * and a method to construct a {@link CRAMCodec} instance.
 *
 * @param <T> data series type
 */
public abstract class CRAMEncoding<T> {
    private final EncodingID encodingId;

    /**
     * Create a new encoding.  Concrete implementation constructors will specify their parameters
     * @param id the EncodingID associated with the concrete implementation
     */
    protected CRAMEncoding(final EncodingID id) {
        encodingId = id;
    }

    public EncodingID id() {
        return encodingId;
    }

    public EncodingParams toParam() {
        return new EncodingParams(id(), toByteArray());
    }

    /**
     * Subclasses but have a defined serialization of their parameters
     * @return a byte array representing a specific encoding's parameter values
     */
    public abstract byte[] toByteArray();

    /**
     * Instantiate the codec represented by this encoding by supplying it with the appropriate streams
     *
     * @param coreBlockInputStream the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will read from
     * @param coreBlockOutputStream the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will write to
     * @param externalBlockInputMap the external block byte stream a {@link htsjdk.samtools.cram.encoding.external.ExternalCodec} will read from
     * @param externalBlockOutputMap the external block byte stream a {@link htsjdk.samtools.cram.encoding.external.ExternalCodec} will write to
     * @return a newly instantiated codec
     */
    public abstract CRAMCodec<T> buildCodec(final BitInputStream coreBlockInputStream,
                                            final BitOutputStream coreBlockOutputStream,
                                            final Map<Integer, ByteArrayInputStream> externalBlockInputMap,
                                            final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap);

    /**
     * Convenience initializer method for read codecs
     *
     * @param coreBlockInputStream the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will read from
     * @param externalBlockInputMap the external block byte stream a {@link htsjdk.samtools.cram.encoding.external.ExternalCodec} will read from
     * @return
     */
    public CRAMCodec<T> buildReadCodec(final BitInputStream coreBlockInputStream, final Map<Integer, ByteArrayInputStream> externalBlockInputMap) {
        return buildCodec(coreBlockInputStream, null, externalBlockInputMap, null);
    }

    /**
     * Convenience initializer method for write codecs
     *
     * @param coreBlockOutputStream the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will write to
     * @param externalBlockOutputMap the external block byte stream a {@link htsjdk.samtools.cram.encoding.external.ExternalCodec} will write to
     * @return
     */
    public CRAMCodec<T> buildWriteCodec(final BitOutputStream coreBlockOutputStream, final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        return buildCodec(null, coreBlockOutputStream, null, externalBlockOutputMap);
    }
}
