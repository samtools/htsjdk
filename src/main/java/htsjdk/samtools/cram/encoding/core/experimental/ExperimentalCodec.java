package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.encoding.core.CoreBitCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.util.Log;

abstract class ExperimentalCodec<T> extends CoreBitCodec<T> {
    private static final Log log = Log.getInstance(ExperimentalCodec.class);

    ExperimentalCodec(final BitInputStream coreBlockInputStream,
                      final BitOutputStream coreBlockOutputStream) {
        super(coreBlockInputStream, coreBlockOutputStream);

        final String warning = String.format(
                "Using the experimental codec %s which is untested and scheduled for removal from the CRAM spec",
                this.getClass().getName());

        log.warn(warning);
    }
}
