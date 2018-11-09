package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.CramEncoding;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.util.Log;

abstract class ExperimentalEncoding<T> extends CramEncoding<T> {
    ExperimentalEncoding(final EncodingID encodingID) {
        super(encodingID);

        final String subclass = this.getClass().getName();
        final String warning = String.format(
                "Using the experimental encoding %s which is untested and scheduled for removal from the CRAM spec",
                subclass);

        final Log log = Log.getInstance(ExperimentalEncoding.class);
        log.warn(warning);
    }
}
