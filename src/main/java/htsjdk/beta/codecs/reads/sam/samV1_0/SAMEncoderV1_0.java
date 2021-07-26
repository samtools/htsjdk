package htsjdk.beta.codecs.reads.sam.samV1_0;

import htsjdk.beta.codecs.reads.sam.SAMEncoder;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.utils.PrivateAPI;

import java.util.Optional;

/**
 * SAM v1.0 encoder.
 */
@PrivateAPI
public class SAMEncoderV1_0 extends SAMEncoder {
    private SAMFileWriter samFileWriter;

    /**
     * Create a V1.0 SAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    public SAMEncoderV1_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return SAMCodecV1_0.VERSION_1;
    }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        samFileWriter = getSAMFileWriter(getReadsEncoderOptions(), samFileHeader);
    }

    @Override
    public void write(final SAMRecord record) {
        if (samFileWriter == null) {
            throw new IllegalStateException(String.format(
                    "A SAMFileHeader must be established before a SAM writer can be established %s",
                    getDisplayName()));
        }
        samFileWriter.addAlignment(record);
    }

    @Override
    public void close() {
        if (samFileWriter != null) {
            samFileWriter.close();
        }
    }

    private SAMFileWriter getSAMFileWriter(
            final ReadsEncoderOptions readsEncoderOptions,
            final SAMFileHeader samFileHeader) {

        final SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
        final boolean preSorted = readsEncoderOptions.isPreSorted();

        final BundleResource readsResource = getOutputBundle().getOrThrow(BundleResourceType.ALIGNED_READS);
        final Optional<BundleResource> optIndexResource = getOutputBundle().get(BundleResourceType.READS_INDEX);
        final Optional<BundleResource> optMD5Resource = getOutputBundle().get(BundleResourceType.MD5);

        //TODO: BAMFileWriter currently only supports writing an index to a plain file, so for now
        // throw if an index is requested on any other type
        if (optIndexResource.isPresent()) {
            final BundleResource indexResource = optIndexResource.get();
            if (indexResource.getIOPath().isPresent()) {
                samFileWriterFactory.setCreateIndex(true);
            } else {
                throw new HtsjdkPluginException("Writing a BAM index to a stream is not yet supported");
            }
        }

        //TODO: BAMFileWriter currently only supports writing an md5 to a plain file with a name that
        // it chooses, so throw if an md5 resource is specified since we can't direct it to the specified
        // resource
        if (optMD5Resource.isPresent()) {
            throw new HtsjdkPluginException(String.format(
                    "Specifying an an MD5 resource name not yet implemented on %s", getDisplayName()));
        }

        if (readsResource.getIOPath().isPresent()) {
            return samFileWriterFactory.makeSAMWriter(
                    samFileHeader,
                    preSorted,
                    readsResource.getIOPath().get().toPath());
        } else {
            return samFileWriterFactory.makeSAMWriter(
                    samFileHeader,
                    preSorted,
                    readsResource.getOutputStream().get());
        }
    }
}
