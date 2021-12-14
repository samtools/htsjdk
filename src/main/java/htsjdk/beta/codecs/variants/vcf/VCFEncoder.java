package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.io.IOPath;
import htsjdk.annotations.InternalAPI;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;

import java.util.Optional;


/**
 * InternalAPI
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} encoders.
 */
@InternalAPI
public abstract class VCFEncoder implements VariantsEncoder {
    private final static Log LOG = Log.getInstance(VCFEncoder.class);
    private final Bundle outputBundle;
    private final VariantsEncoderOptions variantsEncoderOptions;
    private final String displayName;
    private VariantContextWriter vcfWriter;

    /**
     * InternalAPI
     *
     * Create a new VCF encoder from a {@link Bundle}.
     *
     * NOTE: callers that provide an output stream resource should provide a buffered output stream
     * if buffering is desired, since the encoder does not provide an additional buffering layer.
     *
     * @param outputBundle the output {@link Bundle} to encode
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    @InternalAPI
    public VCFEncoder(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(variantsEncoderOptions, "variantsEncoderOptions");

        this.outputBundle = outputBundle;
        this.variantsEncoderOptions = variantsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
    }

   @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public void setHeader(final VCFHeader vcfHeader) {
        ValidationUtils.nonNull(vcfHeader, "vcfHeader");

        final HtsVersion htsVersion = getVersion();
        final HtsVersion headerHtsVersion = vcfHeader.getVCFHeaderVersion().toHtsVersion();
        if (!(headerHtsVersion.getMajorVersion() == htsVersion.getMajorVersion()) ||
                !(headerHtsVersion.getMinorVersion() == htsVersion.getMinorVersion())) {
            LOG.warn(String.format("Using a version %s VCF header on a version %s encoder (this can happen when an in-place upgrade fails).",
                    vcfHeader.getVCFHeaderVersion(),
                    getVersion()));
        }

        vcfWriter = getVCFWriter(getOutputBundle(), getVariantsEncoderOptions());
        vcfWriter.writeHeader(vcfHeader);
    }

    @Override
    public void write(final VariantContext variantContext) {
        ValidationUtils.nonNull(variantContext, "variantContext");

        if (vcfWriter == null) {
            throw new IllegalStateException("The VCF encoder must have a valid header before records can be written");
        }
        vcfWriter.add(variantContext);
    }

    @Override
    public void close() {
        if (vcfWriter != null) {
            vcfWriter.close();
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
     * Get the {@link VariantsEncoderOptions} for this encoder.
     *
     * @return the {@link VariantsEncoderOptions} for this encoder.
     */
    public VariantsEncoderOptions getVariantsEncoderOptions() {
        return variantsEncoderOptions;
    }

    private static VariantContextWriter getVCFWriter(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions) {
        final VariantContextWriterBuilder writerBuilder =
                variantsEncoderOptionsToVariantContextWriterBuilder(
                        variantsEncoderOptions,
                        outputBundle.get(BundleResourceType.VARIANTS_INDEX).isPresent()
                );
        setWriterBuilderOutputs(writerBuilder, outputBundle);
        return writerBuilder.build();
    }

    // propagate VariantsEncoderOptions -> VariantContextWriterBuilder
    private static VariantContextWriterBuilder variantsEncoderOptionsToVariantContextWriterBuilder(
            final VariantsEncoderOptions variantsEncoderOptions,
            final boolean createIndex) {
        final VariantContextWriterBuilder vcWriterBuilder = new VariantContextWriterBuilder();
        vcWriterBuilder.clearOptions();

        vcWriterBuilder.setBuffer(variantsEncoderOptions.getBufferSize());

        if (createIndex) {
            vcWriterBuilder.setOption(Options.INDEX_ON_THE_FLY);
        } else {
            vcWriterBuilder.unsetOption(Options.INDEX_ON_THE_FLY);
        }

        if (variantsEncoderOptions.isAllowFieldsMissingFromHeader()) {
            vcWriterBuilder.setOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER);
        } else {
            vcWriterBuilder.unsetOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER);
        }

        if (variantsEncoderOptions.isWriteSitesOnly()) {
            vcWriterBuilder.setOption(Options.DO_NOT_WRITE_GENOTYPES);
        } else {
            vcWriterBuilder.unsetOption(Options.DO_NOT_WRITE_GENOTYPES);
        }

        if (variantsEncoderOptions.isWriteFullFormatField()) {
            vcWriterBuilder.setOption(Options.WRITE_FULL_FORMAT_FIELD);
        } else {
            vcWriterBuilder.unsetOption(Options.WRITE_FULL_FORMAT_FIELD);
        }

        return vcWriterBuilder;
    }

    private static void setWriterBuilderOutputs(
            final VariantContextWriterBuilder writerBuilder,
            final Bundle outputBundle) {
        final BundleResource variantsResource = outputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS);
        if (!variantsResource.hasOutputType()) {
            throw new IllegalArgumentException(String.format(
                    "The provided %s resource (%s) must be a writeable/output resource",
                    BundleResourceType.VARIANT_CONTEXTS,
                    variantsResource));
        }

        final Optional<IOPath> optIndexIOPath = getIndexIOPath(outputBundle);
        if (variantsResource.getIOPath().isPresent()) {
            final IOPath variantsIOPath = variantsResource.getIOPath().get();
            if (optIndexIOPath.isPresent()) {
                //TODO: this resolves the index automatically. it should check to make sure the provided index
                // matches the one that is automatically resolved, otherwise throw since the request will not be honored
            }
            writerBuilder.setOutputPath(variantsIOPath.toPath());
            validateImputedOutputType(variantsIOPath);
        } else if (variantsResource.getOutputStream().isPresent()) {
            if (optIndexIOPath.isPresent()) {
                throw new HtsjdkUnsupportedOperationException(String.format(
                        "Can't write a VCF index to file %s when output is written to a stream %s",
                        optIndexIOPath.get(),
                        variantsResource));
            }
            // VariantContextWriterBuilder doesn't provide any buffering, but if we were to wrap the provided
            // stream in a buffered stream here, we wouldn't be able to properly control the flushing or lifetime
            // of the underlying stream. we don't want to close the user's stream, but the user doesn't have
            // access to the buffering layer. so just let them provide the buffering stream from the start.

            // This method unconditionally sets the output type to VariantContextWriterBuilder.OutputType.VCF_STREAM,
            // which has the limitation that you can never write BLOCK_COMPRESSED output to a stream.
            writerBuilder.setOutputVCFStream(variantsResource.getOutputStream().get());
        }
    }

    // Calling VariantContextWriterBuilder.setOutputPath has a side effect of setting the output type, based
    // on the file extension, by calling VariantContextWriterBuilder.determineOutputTypeFromFile(Path). So do
    // a sanity check to ensure that it got set to a VCF type (VCF or BLOCK_COMPRESSED_VCF), and not something
    // like BCF. We can't retrieve the value used by the writer directly from the writer, but we can call the
    // same method the writer uses and check the return value.
    private static void validateImputedOutputType(final IOPath variantsIOPath) {
        final VariantContextWriterBuilder.OutputType imputedOutputType =
                VariantContextWriterBuilder.determineOutputTypeFromFile(variantsIOPath.toPath());
        if (imputedOutputType != VariantContextWriterBuilder.OutputType.VCF &&
                imputedOutputType != VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF) {
            throw new HtsjdkPluginException(String.format(
                    "An unsupported output type %s was derived for the resource %s ",
                    imputedOutputType,
                    variantsIOPath.getRawInputString()
            ));
        }
    }

    private static Optional<IOPath> getIndexIOPath(final Bundle outputBundle) {
        final Optional<BundleResource> optIndexResource = outputBundle.get(BundleResourceType.VARIANTS_INDEX);
        if (!optIndexResource.isPresent()) {
            return Optional.empty();
        }
        final BundleResource indexResource = optIndexResource.get();
        if (!indexResource.hasOutputType()) {
            throw new IllegalArgumentException(String.format(
                    "The provided %s index resource (%s) must be a writeable/output resource",
                    BundleResourceType.VARIANTS_INDEX,
                    indexResource));
        }
        if (!indexResource.getIOPath().isPresent()) {
            throw new HtsjdkUnsupportedOperationException("Writing a VCF index to a stream not implemented");
        }
        return indexResource.getIOPath();
    }

}
