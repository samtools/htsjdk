package htsjdk.samtools.cram.compression.rans;

/** Parameters for the rANS 4x8 codec: only the encoding order (0 or 1). */
public final class RANS4x8Params implements RANSParams {

    private final ORDER order;

    public RANS4x8Params(final ORDER order) {
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

    @Override
    public int getFormatFlags() {
        return order == ORDER.ONE ? RANSNx16Params.ORDER_FLAG_MASK : 0;
    }
}
