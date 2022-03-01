package htsjdk.samtools.cram.compression.rans;

public class RANS4x8Params implements RANSParams{

    private ORDER order;

    public RANS4x8Params(ORDER order) {
        this.order = order;
    }

    @Override
    public ORDER getOrder() {
        return order;
    }

}