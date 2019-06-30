package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

public abstract class ExternalEncoding<T> extends CRAMEncoding<T> {
    protected final int externalBlockContentId;

    protected ExternalEncoding(final int externalBlockContentId) {
        super(EncodingID.EXTERNAL);
        this.externalBlockContentId = externalBlockContentId;
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        return ITF8.writeUnsignedITF8(externalBlockContentId);
    }

    @Override
    public String toString() {
        return String.format("Content ID: %d", externalBlockContentId);
    }
}
