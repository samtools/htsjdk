package htsjdk.samtools.cram.encoding.experimental;

import htsjdk.samtools.cram.encoding.AbstractBitCodec;
import htsjdk.samtools.util.Log;

abstract class ExperimentalCodec<T> extends AbstractBitCodec<T> {
    private static final Log log = Log.getInstance(ExperimentalCodec.class);

    ExperimentalCodec() {
        final String warning = String.format(
                "Using the experimental codec %s which is untested and scheduled for removal from the CRAM spec",
                this.getClass().getName());

        log.warn(warning);
    }
}
