package htsjdk.samtools.model;

public class TempBlock {
    private int bytesToCompress;
    private byte[] uncompressedBuffer;
    private byte[] compressedBuffer;

    public TempBlock(int bytesToCompress, byte[] uncompressedBuffer, byte[] compressedBuffer) {
        this.bytesToCompress = bytesToCompress;
        this.uncompressedBuffer = uncompressedBuffer;
        this.compressedBuffer = compressedBuffer;
    }

    //For poison pill only
    public TempBlock() {
    }

    public int getBytesToCompress() {
        return bytesToCompress;
    }

    public byte[] getUncompressedBuffer() {
        return uncompressedBuffer;
    }

    public byte[] getCompressedBuffer() {
        return compressedBuffer;
    }
}