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

import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingDescriptor;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

/**
 * A base class for the various CRAM encodings. This class serves as a (typed) bridge between an
 * {@link EncodingDescriptor}, which only describes an encoding, and the various {@link CRAMCodec} classes,
 * which can be used to read and write typed values to a stream. Has methods to serialize/deserialize
 * its parameters to/from a byte array and a method to construct a read or write {@link CRAMCodec} instance.
 *
 * @param <T> the (source) data eries value type for this encoding. There is no way to express the type constraint
 *           on this type using Java type bounds, since the legitimate values are drawn from a set of Java types
 *           that correspond to the allowable types defined by {@link DataSeriesType}, but logically it must be a
 *           type that corresponds to one of the {@link DataSeriesType} "types", i.e., it must one of be Byte,
 *           Integer, Long or byte[].
 */
public abstract class CRAMEncoding<T> {
    private final EncodingID encodingId;

    /**
     * Create a new encoding.  Concrete implementation constructors will specify their parameters
     * @param encodingId the EncodingID associated with the concrete implementation
     */
    protected CRAMEncoding(final EncodingID encodingId) {
        this.encodingId = encodingId;
    }

    public EncodingID id() {
        return encodingId;
    }

    public EncodingDescriptor toEncodingDescriptor() {
        return new EncodingDescriptor(id(), toSerializedEncodingParams());
    }

    /**
     * Serialize encoding parameters to an ITF8-encoded byte array.
     * By convention, each subclass should have a corresponding and symmetric "fromSerializedEncodingParams"
     * that returns a new instance of that encoding populated with values from the serialized encoding params.
     * @return a byte array containing the encoding's parameter values encoded as an ITF8 stream.
     */
    public abstract byte[] toSerializedEncodingParams();

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
