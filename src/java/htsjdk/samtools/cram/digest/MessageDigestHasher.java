package htsjdk.samtools.cram.digest;

import java.security.MessageDigest;

class MessageDigestHasher extends AbstractSerialDigest<byte[]> {
    private final MessageDigest md;

    MessageDigestHasher(MessageDigest md, Combine<byte[]> combine,
                        byte[] value) {
        super(combine, value);
        this.md = md;
    }

    @Override
    protected void resetAndUpdate(byte[] data) {
        md.reset();
        md.update(data);
    }

    @Override
    protected byte[] getValue() {
        return md.digest();
    }

    @Override
    protected byte[] asByteArray() {
        return md.digest();
    }

}
