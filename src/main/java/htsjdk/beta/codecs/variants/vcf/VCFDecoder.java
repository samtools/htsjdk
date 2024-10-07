package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.AbstractVCFCodec;
import htsjdk.variant.vcf.VCFHeader;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * InternalAPII
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} decoders.
 */
@InternalAPI
public abstract class VCFDecoder implements VariantsDecoder {
    private final Bundle inputBundle;
    private final VariantsDecoderOptions variantsDecoderOptions;
    private final String displayName;
    private final FeatureReader<VariantContext> vcfReader;
    private final VCFHeader vcfHeader;

    /**
     * InternalAPI
     *
     * Create a new VCF decoder.
     *
     * @param inputBundle the input {@link Bundle} to decode
     * @param vcfCodec the {@link AbstractVCFCodec} to use for this decoder
     * @param variantsDecoderOptions the {@link VariantsDecoderOptions} for this decoder
     */
    @InternalAPI
    @SuppressWarnings("unchecked")
    public VCFDecoder(
            final Bundle inputBundle,
            final AbstractVCFCodec vcfCodec,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(vcfCodec, "vcfCodec");
        ValidationUtils.nonNull(variantsDecoderOptions, "variantsDecoderOptions");

        this.inputBundle = inputBundle;
        this.variantsDecoderOptions = variantsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.CT_VARIANT_CONTEXTS).getDisplayName();
        vcfReader = getVCFReader(inputBundle, vcfCodec, variantsDecoderOptions);
        vcfHeader = (VCFHeader) vcfReader.getHeader();
    }

    @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public VCFHeader getHeader() {
        return vcfHeader;
    }

    @Override
    public CloseableIterator<VariantContext> iterator() {
        try {
            return vcfReader.iterator();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception creating variant context iterator for %s", displayName), e);
        }
    }

    @Override
    public boolean isQueryable() {
        VariantsCodecUtils.assertBundleContainsIndex(getInputBundle());
        return vcfReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        VariantsCodecUtils.assertBundleContainsIndex(getInputBundle());
        return vcfReader.isQueryable();
    }

    public CloseableIterator<VariantContext> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        ValidationUtils.nonNull(intervals, "interval list");
        ValidationUtils.validateArg(!intervals.isEmpty(), "interval list must contain intervals");
        VariantsCodecUtils.assertBundleContainsIndex(getInputBundle());

        if (intervals.size() > 1) {
            //TODO: implement lists, sorting, merging, and ensuring that features that overlap more than one interval
            // are only returned once
            throw new HtsjdkUnsupportedOperationException(String.format("query for lists not yet implemented for decoder %s", displayName));
        }
        if (queryRule != HtsQueryRule.OVERLAPPING) {
            //TODO: implement overlapping
            throw new HtsjdkUnsupportedOperationException(String.format("query for contained intervals not implemented for this decoder %s", displayName));
        }

        try {
            return vcfReader.query(HtsIntervalUtils.toLocatableList(intervals).get(0));
        } catch (final IOException e) {
            throw new HtsjdkIOException(String.format("Exception processing query on VCFDecoder %s", displayName), e);
        }
    }

    @Override
    public CloseableIterator<VariantContext> query(final String queryString) {
        ValidationUtils.nonNull(queryString, "queryString");
        VariantsCodecUtils.assertBundleContainsIndex(getInputBundle());

        return queryStart(queryString, 1);
    }

    @Override
    public CloseableIterator<VariantContext> queryStart(final String queryName, final long start) {
        ValidationUtils.nonNull(queryName, "queryName");
        ValidationUtils.validateArg(isQueryable(), String.format("Decoder %s is not queryable", displayName));
        VariantsCodecUtils.assertBundleContainsIndex(getInputBundle());

        if (vcfHeader == null) {
            throw new HtsjdkException(String.format(
                    "A valid VCF header is required to execute a query, but is not present: %s.",
                    displayName));
        }
        final SAMSequenceDictionary seqDict = vcfHeader.getSequenceDictionary();
        if (seqDict == null) {
            throw new HtsjdkException(String.format("No  sequence dictionary is present in the input: %s.", displayName));
        }
        final SAMSequenceRecord samSequenceRecord = seqDict.getSequence(queryName);
        if (samSequenceRecord == null) {
            throw new HtsjdkException(String.format(
                    "The query name %s is not present in the dictionary provided in the input: %s.",
                    queryName,
                    displayName));
        }
        final int length = samSequenceRecord.getSequenceLength();
        try {
            return vcfReader.query(queryName, HtsIntervalUtils.toIntegerSafe(start), length);
        } catch (final IOException e) {
            throw new HtsjdkIOException(String.format("Exception processing queryStart on VCFDecoder", displayName), e);
        }
    }

    @Override
    public void close() {
        try {
            vcfReader.close();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception closing input stream %s for", getDisplayName()), e);
        }
    }

    /**
     * Get the input {@link Bundle} for this decoder.
     *
     * @return the input {@link Bundle} for this decoder
     */
    public Bundle getInputBundle() {
        return inputBundle;
    }

    /**
     * Get the {@link VariantsDecoderOptions} for this decoder.
     *
     * @return the {@link VariantsDecoderOptions} for this decoder.
     */
    public VariantsDecoderOptions getReadsDecoderOptions() {
        return variantsDecoderOptions;
    }

    private static FeatureReader<VariantContext> getVCFReader(
            final Bundle inputBundle,
            final AbstractVCFCodec vcfCodec,
            final VariantsDecoderOptions decoderOptions) {
        final BundleResource variantsResource = inputBundle.getOrThrow(BundleResourceType.CT_VARIANT_CONTEXTS);
        if (!variantsResource.hasInputType()) {
            throw new IllegalArgumentException(String.format(
                    "The provided %s resource (%s) must be a readable/input resource",
                    BundleResourceType.CT_VARIANT_CONTEXTS,
                    variantsResource));
        } else if (variantsResource.getIOPath().isEmpty()) {
            throw new HtsjdkUnsupportedOperationException("VCF reader from stream not implemented");
        }
        final IOPath variantsIOPath = variantsResource.getIOPath().get();
        final Optional<IOPath> indexIOPath = getIndexIOPath(inputBundle);

        //TODO: this resolves the index automatically. it should check to make sure the provided index
        // matches the one that is automatically resolved, otherwise throw since the request will not be honored
        return AbstractFeatureReader.getFeatureReader(
                variantsIOPath.getURIString(),
                indexIOPath.map(IOPath::getURIString).orElse(null),
                vcfCodec,
                indexIOPath.isPresent(),
                decoderOptions.getVariantsChannelTransformer().orElse(null),
                decoderOptions.getIndexChannelTransformer().orElse(null)
        );
    }

    // the underlying readers can't handle index streams, so  for now we can only handle IOPaths
    private static Optional<IOPath> getIndexIOPath(final Bundle inputBundle) {
        final Optional<BundleResource> optIndexResource = inputBundle.get(BundleResourceType.CT_VARIANTS_INDEX);
        if (optIndexResource.isEmpty()) {
            return Optional.empty();
        }
        final BundleResource indexResource = optIndexResource.get();
        if (!indexResource.hasInputType()) {
            throw new IllegalArgumentException(String.format(
                "The provided %s index resource (%s) must be a readable/input resource",
                BundleResourceType.CT_VARIANTS_INDEX,
               indexResource));
        }
        if (indexResource.getIOPath().isEmpty()) {
            throw new HtsjdkUnsupportedOperationException("Reading a VCF index from a stream not implemented");
        }
        return indexResource.getIOPath();
    }

}
