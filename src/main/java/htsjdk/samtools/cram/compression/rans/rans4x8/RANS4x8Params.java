package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.compression.rans.RANSParams;

public class RANS4x8Params implements RANSParams {

    private ORDER order;

    public RANS4x8Params(ORDER order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "RANS4x8Params{" + "order=" + order + "}";
    }

    @Override
    public ORDER getOrder() {
        return order;
    }

    public int getFormatFlags(){
        return order.ordinal();
    }

}