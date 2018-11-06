package htsjdk.samtools.cram.encoding.experimental;

import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.util.Log;

abstract class ExperimentalEncoding<T> implements Encoding<T> {
    private static final Log log = Log.getInstance(ExperimentalEncoding.class);

    ExperimentalEncoding() {
        final String warning = String.format(
                "Using the experimental encoding %s which is untested and scheduled for removal from the CRAM spec",
                this.getClass().getName());

        log.warn(warning);
    }
}
