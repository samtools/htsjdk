package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;

import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;

import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormat;
import htsjdk.exception.HtsjdkPluginException;

import java.util.*;

//TODO: Master TODO list:
// - resolve/clarify/rename/document the canDecodeURI/canDecodeSignature protocol
//      document how to implement codecs that need to see the stream (can't deterministically tell from the extension)
//      clarify ownership of stream (ie, streams that are passed in are closed ? but we can't close
//      output streams that are passed in ??)
// - finish encoders/options for BAM/CRAM, respect presorted in Reads encoders
// - add a "PublicAPI" opt-in annotation to exempt internal methods that need to be public because
//      they're shared from being part of the public API
// - Incomplete: BAM/CRAM encoder options, VCF 4.2, FASTA codecs
// - Missing: SAM/CRAM 2.1/VCF 4.1, 4.3 (read only)/BCF codec ?
// - javadoc/final/PublicAPI/ValidateArgs
// - tests
// - fix CRAM codec access to the eliminate FastaDecoder getReferenceSequenceFile accessor
// - prevent the decoders that delegate to SamReaderFactory from attempting to automatically
//      resolve index files so we don't introduce incompatibilities when the SamReaderFactory
//      implementation dependency is removed
// - address CRAM reference leak issue in master
// - implement index creation on decoders (existing inputs), requires unbuffered stream
// - encryption/decryption key files, etc.
// - implement a built-in cloud channel wrapper and replace the lambdas currently exposed as options
// - test stdin/stdout
// - publish the JSON Bundle JSON schema
// - upgrade API

/**
 * Registry/cache for binding to encoders/decoders.
 */
@SuppressWarnings("rawtypes")
public class HtsCodecRegistry {
    private static final HtsCodecRegistry htsCodecRegistry = new HtsCodecRegistry();

    // for each codec type, keep track of registered codec instances, by format and version
    private static HtsCodecResolver<HaploidReferenceFormat, HaploidReferenceCodec> haprefCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.HAPLOID_REFERENCE,
                    HaploidReferenceFormat::contentSubTypeToFormat);
    private static HtsCodecResolver<ReadsFormat, ReadsCodec> readsCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.ALIGNED_READS,
                    ReadsFormat::contentSubTypeToFormat);
    private static HtsCodecResolver<VariantsFormat, VariantsCodec> variantsCodecResolver =
            new HtsCodecResolver<>(
                    BundleResourceType.VARIANT_CONTEXTS,
                    VariantsFormat::contentSubTypeToFormat);

    //discover any codecs on the classpath
    static { ServiceLoader.load(HtsCodec.class).forEach(htsCodecRegistry::registerCodec); }

    private HtsCodecRegistry() {}

    /**
     * Add a codec to the registry
     */
    public HtsCodec<?, ?, ?> registerCodec(final HtsCodec<?, ?, ?> codec) {
        switch (codec.getCodecType()) {
            case ALIGNED_READS:
                return readsCodecResolver.registerCodec((ReadsCodec) codec);

            case HAPLOID_REFERENCE:
                return haprefCodecResolver.registerCodec((HaploidReferenceCodec) codec);

            case VARIANT_CONTEXTS:
                return variantsCodecResolver.registerCodec((VariantsCodec) codec);

            case FEATURES:
                throw new HtsjdkPluginException("Features codec type not yet implemented");

            default:
                throw new HtsjdkPluginException("Unknown codec type");
        }
    }

    public static HtsCodecResolver<ReadsFormat, ReadsCodec> getReadsCodecResolver() {
        return readsCodecResolver;
    }

    public static HtsCodecResolver<VariantsFormat, VariantsCodec> getVariantsCodecResolver() {
        return variantsCodecResolver;
    }

    public static HtsCodecResolver<HaploidReferenceFormat, HaploidReferenceCodec> getHapRefCodecResolver() {
        return haprefCodecResolver;
    }

}

