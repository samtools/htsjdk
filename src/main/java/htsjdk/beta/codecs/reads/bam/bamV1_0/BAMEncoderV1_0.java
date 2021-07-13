package htsjdk.beta.codecs.reads.bam.bamV1_0;

import htsjdk.beta.codecs.reads.bam.BAMEncoder;
import htsjdk.beta.codecs.reads.bam.BAMEncoderOptions;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.BAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;

import java.util.Optional;

/**
 * BAM v1.0 encoder.
 */
public class BAMEncoderV1_0 extends BAMEncoder {
    private SAMFileWriter samFileWriter;

    /**
     * Create a V1.0 BAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    public BAMEncoderV1_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return BAMCodecV1_0.VERSION_1;
    }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        samFileWriter = getBAMFileWriter(readsEncoderOptions, samFileHeader);
    }

    @Override
    public void write(final SAMRecord record) {
        samFileWriter.addAlignment(record);
    }

    @Override
    public void close() {
        if (samFileWriter != null) {
            samFileWriter.close();
        }
    }

    /**
     *  Propagate BAMEncoderOptions to a SAMFileWriterFactory.
     */
    private void bamEncoderOptionsToSamWriterFactory(
            final BAMEncoderOptions bamEncoderOptions,
            final SAMFileWriterFactory samFileWriterFactory) {
        samFileWriterFactory.setDeflaterFactory(bamEncoderOptions.getDeflaterFactory());
        samFileWriterFactory.setCompressionLevel(bamEncoderOptions.getCompressionLevel());
        samFileWriterFactory.setTempDirectory(bamEncoderOptions.getTempDirPath().toPath().toFile());
        samFileWriterFactory.setBufferSize(bamEncoderOptions.getOutputBufferSize());
        samFileWriterFactory.setUseAsyncIo(bamEncoderOptions.isUseAsyncIo());
        samFileWriterFactory.setAsyncOutputBufferSize(bamEncoderOptions.getAsyncOutputBufferSize());
        samFileWriterFactory.setMaxRecordsInRam(bamEncoderOptions.getMaxRecordsInRam());
    }

    private SAMFileWriter getBAMFileWriter(
            final ReadsEncoderOptions readsEncoderOptions,
            final SAMFileHeader samFileHeader) {

        final BAMEncoderOptions bamEncoderOptions = readsEncoderOptions.getBAMEncoderOptions();
        final SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
        bamEncoderOptionsToSamWriterFactory(bamEncoderOptions, samFileWriterFactory);

        final boolean preSorted = readsEncoderOptions.isPreSorted();

        final BundleResource readsResource = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS);
        final Optional<BundleResource> optIndexResource = outputBundle.get(BundleResourceType.READS_INDEX);
        final Optional<BundleResource> optMD5Resource = outputBundle.get(BundleResourceType.MD5);

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
            throw new HtsjdkPluginException("Can't yet specify an MD5 resource name");
        }

        if (readsResource.getIOPath().isPresent()) {
            return samFileWriterFactory.makeBAMWriter(
                    samFileHeader,
                    preSorted,
                    readsResource.getIOPath().get().toPath());
        } else {
            return samFileWriterFactory.makeBAMWriter(
                    samFileHeader,
                    preSorted,
                    readsResource.getOutputStream().get());
        }
    }
}
