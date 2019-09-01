package htsjdk.samtools.cram.compression.rans;

class RANSDecodingSymbol {
    int start; // Start of range.
    int freq; // Symbol frequency.

    public void reset() {
        start = freq = 0;
    }
}
