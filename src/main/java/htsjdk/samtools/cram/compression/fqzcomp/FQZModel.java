package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.compression.range.ByteModel;

public class FQZModel {

    private ByteModel[] quality; // Primary model for quality values
    private ByteModel[] length; // Read length models with the context 0-3 being successive byte numbers (little endian order)
    private ByteModel reverse; // indicates which strings to reverse
    private ByteModel duplicate; // Indicates if this whole string is a duplicate of the last one
    private ByteModel selector; // Used if gflags.multi_param or pflags.do_sel are defined.

    public FQZModel() {
    }

    public ByteModel[] getQuality() {

        return quality;
    }

    public void setQuality(ByteModel[] quality) {
        this.quality = quality;
    }

    public ByteModel[] getLength() {
        return length;
    }

    public void setLength(ByteModel[] length) {
        this.length = length;
    }

    public ByteModel getReverse() {
        return reverse;
    }

    public void setReverse(ByteModel reverse) {
        this.reverse = reverse;
    }

    public ByteModel getDuplicate() {
        return duplicate;
    }

    public void setDuplicate(ByteModel duplicate) {
        this.duplicate = duplicate;
    }

    public ByteModel getSelector() {
        return selector;
    }

    public void setSelector(ByteModel selector) {
        this.selector = selector;
    }
}