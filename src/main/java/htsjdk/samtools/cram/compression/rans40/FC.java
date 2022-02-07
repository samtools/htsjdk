package htsjdk.samtools.cram.compression.rans40;

class FC {
    int F;  // frequency
    int C;  // cummulative frequency

    public void reset() {
        F = C = 0;
    }

}
