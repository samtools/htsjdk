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

import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * NOTE: this encoding can be a hybrid encoding in that it ALLOWS for the possibility to split it's data
 * between the core block and an external block (i.e., if lenEncoding is CORE and byteEncoding is EXTERNAL)
 * This has implications for data access, since some of it's data is interleaved with other data in the
 * core block.
 */
public class ByteArrayLenEncoding extends CRAMEncoding<byte[]> {
    private final CRAMEncoding<Integer> lenEncoding;
    private final CRAMEncoding<byte[]> byteEncoding;

    /**
     * Note: depending on the sub-encodings, this encoding can wind up being a core/external hybrid.
     * See https://github.com/samtools/hts-specs/issues/426).
     */
    public ByteArrayLenEncoding(final CRAMEncoding<Integer> lenEncoding, final CRAMEncoding<byte[]> byteEncoding) {
        super(EncodingID.BYTE_ARRAY_LEN);
        this.lenEncoding = lenEncoding;
        this.byteEncoding = byteEncoding;
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return ByteArrayLenEncoding with parameters populated from serializedParams
     */
    public static ByteArrayLenEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buffer = ByteBuffer.wrap(serializedParams);

        final EncodingID lenEncodingID = EncodingID.values()[buffer.get()];
        final int lenLength = ITF8.readUnsignedITF8(buffer);
        final byte[] lenBytes = new byte[lenLength];
        buffer.get(lenBytes);
        final CRAMEncoding<Integer> lenEncoding = EncodingFactory.createCRAMEncoding(DataSeriesType.INT, lenEncodingID, lenBytes);

        final EncodingID byteID = EncodingID.values()[buffer.get()];
        final int byteLength = ITF8.readUnsignedITF8(buffer);
        final byte[] byteBytes = new byte[byteLength];
        buffer.get(byteBytes);
        final CRAMEncoding<byte[]> byteEncoding = EncodingFactory.createCRAMEncoding(DataSeriesType.BYTE_ARRAY, byteID, byteBytes);

        return new ByteArrayLenEncoding(lenEncoding, byteEncoding);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            // write the encoding ID used for length, followed by it's params length, and then the params
            byteArrayOutputStream.write((byte) lenEncoding.id().getId());
            final byte[] lenBytes = lenEncoding.toSerializedEncodingParams();
            ITF8.writeUnsignedITF8(lenBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(lenBytes);

            // write the encoding ID used for the bytes, followed by it's params length, and then the params
            byteArrayOutputStream.write((byte) byteEncoding.id().getId());
            final byte[] byteBytes = byteEncoding.toSerializedEncodingParams();
            ITF8.writeUnsignedITF8(byteBytes.length, byteArrayOutputStream);
            byteArrayOutputStream.write(byteBytes);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new ByteArrayLenCodec(
                lenEncoding.buildCodec(sliceBlocksReadStreams, sliceBlocksWriteStreams),
                byteEncoding.buildCodec(sliceBlocksReadStreams, sliceBlocksWriteStreams));
    }

    @Override
    public String toString() {
        return String.format("LenEncoding: %s ByteEncoding: %s", lenEncoding.toString(), byteEncoding.toString());
    }

}
