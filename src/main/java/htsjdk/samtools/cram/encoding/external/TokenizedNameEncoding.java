package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TokenizedNameEncoding extends ExternalEncoding<byte[]> {
    //private final byte stopByte;
    private final int externalId;
    private final ByteBuffer buf;

    public TokenizedNameEncoding(final int externalId) {
        super(externalId);
        //this.stopByte = stopByte;
        this.externalId = externalId;
        this.buf = ByteBuffer.allocate(ITF8.MAX_BYTES + 1);
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return ByteArrayStopEncoding with parameters populated from serializedParams
     */
    public static TokenizedNameEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buf = ByteBuffer.wrap(serializedParams);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final byte stopByte = buf.get();
        final int externalId = ITF8.readUnsignedITF8(buf);
        return new TokenizedNameEncoding(externalId);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        buf.clear();

        buf.order(ByteOrder.LITTLE_ENDIAN);
        //buf.put(stopByte);
        ITF8.writeUnsignedITF8(externalId, buf);

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    @Override
    public CRAMCodec<byte[]> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        final ByteArrayInputStream is = sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getExternalInputStream(externalId);
        final ByteArrayOutputStream os = sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getExternalOutputStream(externalId);
        return new TokenizedNameCodec(is, os);
    }

    @Override
    public String toString() {
        return String.format("Content ID: %d", externalId);
    }

}
