package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.core.CoreCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.util.Log;

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
