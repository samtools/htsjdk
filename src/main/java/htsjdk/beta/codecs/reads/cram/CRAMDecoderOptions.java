package htsjdk.beta.codecs.reads.cram;

import htsjdk.io.IOPath;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;

import java.util.Optional;

/**
 * Decoder options specific to CRAM decoders.
 */
public class CRAMDecoderOptions {
    private CRAMReferenceSource referenceSource;
    private IOPath referencePath;

    /**
     * Get the {@link CRAMReferenceSource} for these options.
     *
     * @return the {@link CRAMReferenceSource} for these options, or Optional.empty() if none
     */
    public Optional<CRAMReferenceSource> getReferenceSource() {
        return Optional.ofNullable(referenceSource);
    }

    /**
     * Set the {@link CRAMReferenceSource} for these options. Mutually exclusive with
     * {@link #setReferencePath(IOPath)}, which must be set to null in order to set a {@link CRAMReferenceSource}.
     *
     * @param referenceSource the {@link CRAMReferenceSource} for these options. May be null.
     * @return updated CRAMDecoderOptions
     * @throws IllegalArgumentException if a reference path is already set on these options
     */
    public CRAMDecoderOptions setReferenceSource(final CRAMReferenceSource referenceSource) {
        if (referencePath != null) {
            throw new IllegalStateException(String.format(
                        "Reference source and reference path are mutually exclusive. Reference path already has value %s.",
                        referencePath.getRawInputString()));
        }
        this.referencePath = null;
        this.referenceSource = referenceSource;
        return this;
    }

    /**
     * Get the reference path for these options.
     *
     * @return the reference path for these options, or Optional.empty() if none.
     */
    public Optional<IOPath> getReferencePath() {
        return Optional.ofNullable(referencePath);
    }

    /**
     * Set the reference path for these options. Mutually exclusive with {@link #setReferenceSource(CRAMReferenceSource)},
     * which must be set to null in order to set a reference path.
     *
     * @param referencePath The path to use. may be null.
     * @return updated CRAMDecoderOptions
     * @throws IllegalArgumentException if a reference source is already set on these options
     */
    public CRAMDecoderOptions setReferencePath(final IOPath referencePath) {
        if (referenceSource != null) {
            throw new IllegalStateException(String.format(
                    "Reference path and reference source are mutually exclusive. Reference source already has value %s.",
                    referenceSource));
        }
        this.referenceSource = null;
        this.referencePath = referencePath;
        return this;
    }

}
