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

import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

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

    // TODO: The interconversion method names are confusing:
    // TODO:   this (base) class has a `toParam` method that returns type `EncodingParams`
    // TODO:   every subclass has a static `fromParams` that takes a byteArray and returns a subclass instance
    // TODO:   (not an `EncodingParams`)
    // TODO: Should/can these be symmetric and both use `EncodingParams` ? or can we get rid of EncodingParams
    // TODO: altogether ? Its redundant with this class since you can always render one given a CRAMEncoding
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
     * @param sliceBlocksReadStreams the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will read from
     * @param sliceBlocksWriteStreams the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will write to
     * @return a newly instantiated codec
     */
    public abstract CRAMCodec<T> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams);

    /**
     * Convenience initializer method for read codecs
     *
     * @param sliceBlocksReadStreams the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will read from
     * @return
     */
    public CRAMCodec<T> buildReadCodec(final SliceBlocksReadStreams sliceBlocksReadStreams) {
        return buildCodec(sliceBlocksReadStreams, null);
    }

    /**
     * Convenience initializer method for write codecs
     *
     * @param sliceBlocksWriteStreams the core block bit stream a {@link htsjdk.samtools.cram.encoding.core.CoreCodec} will write to
     * @return
     */
    public CRAMCodec<T> buildWriteCodec(final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return buildCodec(null, sliceBlocksWriteStreams);
    }
}
