package htsjdk.samtools.cram.digest;

import java.security.MessageDigest;

class MessageDigestHasher extends AbstractSerialDigest<byte[]> {
    private final MessageDigest messageDigest;

    MessageDigestHasher(final MessageDigest messageDigest, final Combine<byte[]> combine,
                        final byte[] value) {
        super(combine, value);
        this.messageDigest = messageDigest;
    }

    @Override
    protected void resetAndUpdate(final byte[] data) {
        messageDigest.reset();
        messageDigest.update(data);
    }

    @Override
    protected byte[] getValue() {
        return messageDigest.digest();
    }

    @Override
    protected byte[] asByteArray() {
        return messageDigest.digest();
    }

}
