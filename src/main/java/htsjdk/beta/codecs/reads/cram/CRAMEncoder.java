package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#FMT_READS_CRAM} decoders.
 */
@InternalAPI
public abstract class CRAMEncoder implements ReadsEncoder {
    private final Bundle outputBundle;
    private final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;
    private CRAMFileWriter cramFileWriter;

    /**
     * InternalAPI
     *
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    @InternalAPI
    public CRAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(readsEncoderOptions, "readsEncoderOptions");

        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.CT_ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        ValidationUtils.nonNull(samFileHeader, "samFileHeader");

        if (cramFileWriter != null) {
            throw new IllegalStateException(String.format(
                    "A SAMFileHeader has already been has already been set for encoder %s", getDisplayName()));
        }
        cramFileWriter = getCRAMWriter(samFileHeader, readsEncoderOptions);
    }

    @Override
    public void write(final SAMRecord record) {
        ValidationUtils.nonNull(record, "record");

        if (cramFileWriter == null) {
            throw new IllegalStateException(
                    String.format("A SAMFileHeader must be set to establish a CRAM writer for %s", getDisplayName()));
        }
        cramFileWriter.addAlignment(record);
    }

    @Override
    public void close() {
        if (cramFileWriter != null) {
            cramFileWriter.close();
        }
    }

    /**
     * Get the output {@link Bundle} for this encoder.
     *
     * @return the output {@link Bundle} for this encoder
     */
    public Bundle getOutputBundle() {
        return outputBundle;
    }

    /**
     * Get the {@link ReadsEncoderOptions} for this encoder.
     *
     * @return the {@link ReadsEncoderOptions} for this encoder.
     */
    public ReadsEncoderOptions getReadsEncoderOptions() {
        return readsEncoderOptions;
    }

    /**
     * Return a {@link CRAMReferenceSource} using the {@link ReadsEncoderOptions}, or a default source.
     *
     * @param cramEncoderOptions CRAMEncoderOptions options to use
     * @return a {@link CRAMReferenceSource}
     */
    @InternalAPI
    public static CRAMReferenceSource getCRAMReferenceSource(final CRAMEncoderOptions cramEncoderOptions) {
        ValidationUtils.nonNull(cramEncoderOptions, "cramEncoderOptions");

        if (cramEncoderOptions.getReferenceSource().isPresent()) {
            return cramEncoderOptions.getReferenceSource().get();
        } else if (cramEncoderOptions.getReferencePath().isPresent()) {
            return CRAMCodec.getCRAMReferenceSource(cramEncoderOptions.getReferencePath().get());
        }

        // if none is specified, get the default "lazy" reference source that throws when queried, to allow
        // operations that don't require a reference
        return ReferenceSource.getDefaultCRAMReferenceSource();
    }

    private CRAMFileWriter getCRAMWriter(final SAMFileHeader samFileHeader, final ReadsEncoderOptions readsEncoderOptions) {
        // the CRAMFileWriter constructors assume presorted; so if we're presorted, use the CRAMFileWriters
        // directly so we can support writing to a stream
        if (readsEncoderOptions.isPreSorted()) {
            final BundleResource outputResource = outputBundle.getOrThrow(BundleResourceType.CT_ALIGNED_READS);
            if (outputResource.getIOPath().isPresent()) {
                cramFileWriter = new CRAMFileWriter(
                        outputResource.getIOPath().get().getOutputStream(),
                        getCRAMReferenceSource(readsEncoderOptions.getCRAMEncoderOptions()),
                        samFileHeader,
                        outputResource.getIOPath().get().toString());
                return cramFileWriter;
            } else {
                cramFileWriter = new CRAMFileWriter(
                        outputResource.getOutputStream().get(),
                        getCRAMReferenceSource(readsEncoderOptions.getCRAMEncoderOptions()),
                        samFileHeader,
                        outputResource.getDisplayName());
                return cramFileWriter;
            }
        } else {
            // this path uses SAMFileWriterFactory to ensure presorted==false is handled correctly
            final SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
            final boolean preSorted = readsEncoderOptions.isPreSorted();

            final BundleResource readsResource = getOutputBundle().getOrThrow(BundleResourceType.CT_ALIGNED_READS);
            final Optional<BundleResource> optIndexResource = getOutputBundle().get(BundleResourceType.CT_READS_INDEX);
            final Optional<BundleResource> optMD5Resource = getOutputBundle().get(BundleResourceType.CT_MD5);

            //TODO: SamFileWriterFactory code paths currently only support writing an index to a plain file, so
            // for now throw if an index is requested on any other type
            if (optIndexResource.isPresent()) {
                final BundleResource indexResource = optIndexResource.get();
                if (indexResource.getIOPath().isPresent()) {
                    samFileWriterFactory.setCreateIndex(true);
                } else {
                    throw new HtsjdkUnsupportedOperationException("Writing a CRAM index to a stream is not yet supported");
                }
            }

            //TODO: CRAMFileWriter currently only supports writing an md5 to a plain file with a name that
            // it chooses, so throw if an md5 resource is specified since we can't direct it to the specified
            // resource
            if (optMD5Resource.isPresent()) {
                throw new HtsjdkUnsupportedOperationException("Can't yet specify an MD5 resource name");
            }

            final CRAMEncoderOptions cramEncoderOptions = readsEncoderOptions.getCRAMEncoderOptions();
            if (!cramEncoderOptions.getReferencePath().isPresent()) {
                throw new IllegalArgumentException("An IOPath reference is required to create a CRAM encoder");
            }
            if (readsResource.getIOPath().isPresent()) {
                return samFileWriterFactory.makeCRAMWriter(
                        samFileHeader,
                        preSorted,
                        readsResource.getIOPath().get().toPath(),
                        cramEncoderOptions.getReferencePath().get().toPath());
            } else {
                throw new IllegalArgumentException(String.format(
                        "An reads IOPath resource is required to create a CRAM encoder (%s)", getOutputBundle()));
            }
        }
    }

}
