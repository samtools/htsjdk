package htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2;

import htsjdk.beta.codecs.reads.htsget.HtsgetBAMCodec;
import htsjdk.beta.codecs.reads.htsget.HtsgetBAMDecoder;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.beta.plugin.HtsRecord;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

import java.util.Optional;

/**
 * Version 1.2 of {@link BundleResourceType#FMT_READS_HTSGET_BAM} codec.
 */
public class HtsgetBAMCodecV1_2 extends HtsgetBAMCodec {

    @Override
    public HtsgetBAMDecoder getDecoder(final Bundle inputBundle,
                                       final ReadsDecoderOptions decodeOptions) {
        final BundleResource readsResource = inputBundle.getOrThrow(BundleResourceType.CT_ALIGNED_READS);
        final Optional<IOPath> inputPath = readsResource.getIOPath();
        if (!inputPath.isPresent()) {
            throw new IllegalArgumentException("The reads source must be a IOPath");
        }
        return new HtsgetBAMDecoderV1_2(inputBundle, decodeOptions);
    }

    @Override
    public HtsEncoder<?, ? extends HtsRecord> getEncoder(Bundle outputBundle, ReadsEncoderOptions encodeOptions) {
        throw new IllegalArgumentException("Htsget is read only - no Htsget BAM encoder component is available.");
    }

}
