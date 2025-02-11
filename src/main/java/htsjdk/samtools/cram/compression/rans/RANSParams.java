package htsjdk.samtools.cram.compression.rans;

public interface RANSParams {

     enum ORDER {
        ZERO, ONE;

        public static ORDER fromInt(final int orderValue) {
            try {
                return ORDER.values()[orderValue];
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Unknown rANS order: " + orderValue, e);
            }
        }
    }

    int getFormatFlags();

    ORDER getOrder();

}