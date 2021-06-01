package htsjdk.beta.codecs.reads.cram;

import htsjdk.io.IOPath;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;

import java.util.Optional;

/**
 * Encoder options for CRAM encoder. This enables encoders that can accept
 * things such as a custom encoding map or other CRAM-specific params.
 */
public class CRAMEncoderOptions {
    private CRAMReferenceSource referenceSource;
    private IOPath referencePath;

    public Optional<CRAMReferenceSource> getReferenceSource() {
        return Optional.ofNullable(referenceSource);
    }

    // Mutually exclusive with setReferencePath
    public CRAMEncoderOptions setReferenceSource(final CRAMReferenceSource referenceSource) {
        this.referencePath = null;  // // path is mutually exclusive with setReferenceSource
        this.referenceSource = referenceSource;
        return this;
    }

    public Optional<IOPath> getReferencePath() {
        return Optional.ofNullable(referencePath);
    }

    // Mutually exclusive with setReferenceSource
    public CRAMEncoderOptions setReferencePath(final IOPath referencePath) {
        this.referenceSource = null; // path is mutually exclusive with setReferenceSource
        this.referencePath = referencePath;
        return this;
    }

}
