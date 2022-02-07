package htsjdk.samtools.cram.compression.rans40;

class ArithmeticDecoder {
    final FC[] fc = new FC[256];

    // reverse lookup table ?
    byte[] R = new byte[Constants.TOTFREQ];

    public ArithmeticDecoder() {
        for (int i = 0; i < 256; i++) {
            fc[i] = new FC();
        }
    }

    public void reset() {
        for (int i = 0; i < 256; i++) {
            fc[i].reset();
        }
        for (int i = 0; i < Constants.TOTFREQ; i++) {
            R[i] = 0;
        }
    }

}
