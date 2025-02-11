package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.compression.range.ByteModel;

public class FQZModels {

    private final ByteModel[] quality; // Primary model for quality values
    private final ByteModel[] length; // Read length models with the context 0-3 being successive byte numbers (little endian order)
    private final ByteModel reverse; // indicates which strings to reverse
    private final ByteModel duplicate; // Indicates if this whole string is a duplicate of the last one
    private final ByteModel selector; // Used if gflags.multi_param or pflags.do_sel are defined.

    public FQZModels(final FQZParams fqzParams) {
        quality = new ByteModel[1 << 16];
        for (int i = 0; i < (1 << 16); i++) {
            quality[i] = new ByteModel(fqzParams.getMaxSymbol() + 1); // +1 as max value not num. values
        }
        length = new ByteModel[4];
        for (int i = 0; i < 4; i++) {
            length[i] = new ByteModel(256);
        }
        reverse = new ByteModel(2);
        duplicate = new ByteModel(2);
        selector = fqzParams.getMaxSelector() > 0 ?
                new ByteModel(fqzParams.getMaxSelector() + 1) :
                null;
    }

    public ByteModel[] getQuality() {
        return quality;
    }

    public ByteModel[] getLength() {
        return length;
    }

    public ByteModel getReverse() {
        return reverse;
    }

    public ByteModel getDuplicate() {
        return duplicate;
    }

    public ByteModel getSelector() {
        return selector;
    }

}