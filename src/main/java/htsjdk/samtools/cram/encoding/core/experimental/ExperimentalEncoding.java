package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.util.Log;

/**
 * An ExperimentalEncoding is one which is included in the CRAM specification for historic reasons,
 * and will be removed in future versions.  At that time, it will be removed from this codebase as well.
 *
 * There are two such encoding families: Golomb and Golomb-Rice
 *
 * @param <T> the data series type to be encoded
 */
abstract class ExperimentalEncoding<T> extends CRAMEncoding<T> {
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
