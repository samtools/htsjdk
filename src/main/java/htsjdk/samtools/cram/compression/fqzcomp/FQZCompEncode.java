package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.CRAMException;
import java.nio.ByteBuffer;

/**
 * Placeholder for the (not yet implemented) quality score encoder.
 */
public class FQZCompEncode {

    // This method assumes that inBuffer is already rewound.
    // It compresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the compressed data.
    public ByteBuffer compress(final ByteBuffer inBuffer) {
        throw new CRAMException("FQZComp compression is not implemented");
    }

}
