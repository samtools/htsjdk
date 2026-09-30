package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

/**
 * CRAMEncoding class for byte arrays whose bytes are each Huffman coded in the core block.
 *
 * The serialized parameters are those of {@link CanonicalHuffmanByteEncoding}: they describe the code for a single
 * byte, and the array form applies it to every byte of the array. The array's length is not stored here; the caller
 * supplies it when reading, as it does for the other byte-array codecs. A code with a single symbol has a code word
 * length of zero, so an array of that symbol costs no bits at all.
 */
public final class CanonicalHuffmanByteArrayEncoding extends CRAMEncoding<byte[]> {
    private final CanonicalHuffmanByteEncoding byteEncoding;

    /**
     * @param byteEncoding the encoding of a single byte, whose code is applied to each byte of an array
     */
    public CanonicalHuffmanByteArrayEncoding(final CanonicalHuffmanByteEncoding byteEncoding) {
        super(EncodingID.HUFFMAN);
        this.byteEncoding = byteEncoding;
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams the parameters of a {@link CanonicalHuffmanByteEncoding}
     * @return CanonicalHuffmanByteArrayEncoding with parameters populated from serializedParams
     */
    public static CanonicalHuffmanByteArrayEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        return new CanonicalHuffmanByteArrayEncoding(
                CanonicalHuffmanByteEncoding.fromSerializedEncodingParams(serializedParams));
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        return byteEncoding.toSerializedEncodingParams();
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(
            final SliceBlocksReadStreams sliceBlocksReadStreams,
            final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new CanonicalHuffmanByteArrayCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                byteEncoding.getHuffmanParams());
    }

    @Override
    public String toString() {
        return byteEncoding.toString();
    }
}
