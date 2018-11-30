package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.core.CoreCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.util.Log;

/**
 * An ExperimentalCodec is one which is included in the CRAM specification for historic reasons,
 * and will be removed in future versions.  At that time, it will be removed from this codebase as well.
 *
 * There are two such codec families: Golomb and Golomb-Rice
 *
 * @param <T> the data series type of the codec
 */
abstract class ExperimentalCodec<T> extends CoreCodec<T> {
    ExperimentalCodec(final BitInputStream coreBlockInputStream,
                      final BitOutputStream coreBlockOutputStream) {
        super(coreBlockInputStream, coreBlockOutputStream);

        final String subclass = this.getClass().getName();
        final String warning = String.format(
                "Using the experimental codec %s which is untested and scheduled for removal from the CRAM spec",
                subclass);

        final Log log = Log.getInstance(ExperimentalCodec.class);
        log.warn(warning);
    }
}
