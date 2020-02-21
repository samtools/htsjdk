package htsjdk.samtools.model;

public class CompressedBlock {

    private int compressedSize;
    private int bytesToCompress;
    private byte[] compressedBuffer;

    public CompressedBlock(int compressedSize, int bytesToCompress, byte[] compressedBuffer) {
        this.compressedSize = compressedSize;
        this.bytesToCompress = bytesToCompress;
        this.compressedBuffer = compressedBuffer;
    }

    //For poison pill only
    public CompressedBlock() {
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public int getBytesToCompress() {
        return bytesToCompress;
    }

    public byte[] getCompressedBuffer() {
        return compressedBuffer;
    }

}
