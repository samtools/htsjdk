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
// use is prefix for boolean getters
// - implement SeekableStream source
// - finish encoders/options for BAM/CRAM, respect presorted in Reads encoders
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
 * Registry for tracking {@link HtsCodec} instances. Classes that implement {@link HtsCodec} are
 * either dynamically discovered at startup and entered into this registry, or manually registered
 * via the {@link #registerCodec(HtsCodec)} method.
 *
 * This class uses a single {@link HtsCodecResolver} object for each of the 4 different codec types
 * to manage instances of that type, delegating registration to the appropriate {@link HtsCodecResolver}
 * based on the type of the codec being registered. The {@link HtsCodecResolver} that is subsequently
 * used to resolve an input or output to a specific codec.
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
     * Add a codec to the registry. If a codec that supports the same (format, version) values, as
     * returned by {@link HtsCodec#getFileFormat()} and {@link HtsCodec#getVersion()} ()} methods
     * is already, registered, the new codec replaces the previous one, and the previously registered
     * codec is returned.
     *
     * @param codec the codec to be added
     * @return a previously registered codec with the same (format, version), or null if no codec was
     * previously registered
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

    /**
     * Return the {@link HtsCodecResolver} for {@link ReadsCodec}s.
     */
    public static HtsCodecResolver<ReadsFormat, ReadsCodec> getReadsCodecResolver() {
        return readsCodecResolver;
    }

    /**
     * Return the {@link HtsCodecResolver} for {@link VariantsCodec}s.
     */
    public static HtsCodecResolver<VariantsFormat, VariantsCodec> getVariantsCodecResolver() {
        return variantsCodecResolver;
    }

    /**
     * Return the {@link HtsCodecResolver} for {@link HaploidReferenceCodec}s.
     */
    public static HtsCodecResolver<HaploidReferenceFormat, HaploidReferenceCodec> getHapRefCodecResolver() {
        return haprefCodecResolver;
    }

}

