package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;

public class RANS4x8Params implements RANSParams {

    private final ORDER order;

    public RANS4x8Params(final ORDER order)  {
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
        return order == ORDER.ONE ?
                RANSNx16Params.ORDER_FLAG_MASK :
                0;
    }

}