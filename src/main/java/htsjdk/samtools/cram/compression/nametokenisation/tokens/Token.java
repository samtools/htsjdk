package htsjdk.samtools.cram.compression.nametokenisation.tokens;

import java.nio.ByteBuffer;

public class Token {

    private final ByteBuffer byteBuffer;

    public Token(ByteBuffer inputByteBuffer) {
        byteBuffer = inputByteBuffer;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

}