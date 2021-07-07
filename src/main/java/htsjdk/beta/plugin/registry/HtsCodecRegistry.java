package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.exception.HtsjdkPluginException;

//TODO: Master TODO list:
// - javadoc/final/ValidateArgs/audit super() calls
// - Incomplete: BAM/CRAM de/encoder options, VCF 4.2, FASTA codecs, respect presorted in Reads encoders
// - Missing: SAM/CRAM 2.1/VCF 4.1, 4.3 (read only)/BCF codec ?
// - finish adding tests/input index resolution rules
// - finish content type inference
// - implement a built-in cloud channel wrapper and replace the lambdas currently exposed as options
// - fix CRAM codec access to the eliminate FastaDecoder getReferenceSequenceFile accessor
// - address CRAM reference leak issue in master
// - test stdin/stdout (would be way easier with GATK)
//
// TODO: post PR
// - encryption/decryption key files, etc.
// - publish the JSON Bundle JSON schema
// - implement index creation on decoders (existing inputs), requires unbuffered stream
// - upgrade API

/**
 * A registry for tracking {@link HtsCodec} instances.
 *
 * Registries are populated with objects that implement {@link HtsCodec}, which are either discovered
 * and registered dynamically at startup (see {@link HtsDefaultRegistry}), or manually registered via
 * the {@link #registerCodec(HtsCodec)} method (see {@link HtsCodecRegistry#createPrivateRegistry()}).
 *
 * A global static immutable registry is maintained by {@link HtsDefaultRegistry}. A private mutable
 * registry that can be used with custom codecs can be created with
 * {@link HtsCodecRegistry#createPrivateRegistry()}.
 */
public class HtsCodecRegistry {
    private final HaploidReferenceResolver htsHaploidReferenceResolver = new HaploidReferenceResolver();;
    private final ReadsResolver htsReadsResolver = new ReadsResolver();
    private final VariantsResolver htsVariantsResolver = new VariantsResolver();

    /**
     * Create a registry. Package protected to prevent use outside of the registry package. To create
     * a private registry from outside the registry package, use {@link #createPrivateRegistry}.
     */
    HtsCodecRegistry() { }

    /**
     * Add a codec to the registry. If a codec that supports the same (format, version) as
     * the new codec (determined by {@link HtsCodec#getFileFormat()} and {@link HtsCodec#getVersion()}
     * methods) is already registered, the new registry is update to contain the new codec, and the
     * previously registered codec is returned.
     *
     * @param codec the codec to be added
     * @return a previously registered codec with the same (format, version), or null if no codec
     * was previously registered
     */
    public synchronized HtsCodec<?, ?> registerCodec(final HtsCodec<?, ?> codec) {
        switch (codec.getContentType()) {
            case HAPLOID_REFERENCE:
                return htsHaploidReferenceResolver.registerCodec((HaploidReferenceCodec) codec);

            case ALIGNED_READS:
                return htsReadsResolver.registerCodec((ReadsCodec) codec);

            case VARIANT_CONTEXTS:
                return htsVariantsResolver.registerCodec((VariantsCodec) codec);

            case FEATURES:
                throw new HtsjdkPluginException("Features codec type not yet implemented");

            default:
                throw new HtsjdkPluginException("Unknown codec type");
        }
    }

    /**
     * Create a mutable registry instance for private use. The {@link HtsDefaultRegistry} is immutable, but
     * a private registry can be populated with, and used to resolve against, custom codecs. The private registry
     * is initialized to contain all codecs that are registered in the {@link HtsDefaultRegistry}. Custom
     * codecs can be installed using {@link HtsCodecRegistry#registerCodec(HtsCodec)}.
     *
     * @return a mutable registry instance for private use
     */
    public synchronized static HtsCodecRegistry createPrivateRegistry() {
        final HtsCodecRegistry privateRegistry = new HtsCodecRegistry();

        // propagate the codecs from the sourceRegistry to the new registry
        HtsDefaultRegistry.htsDefaultCodecRegistry.getHaploidReferenceResolver().getCodecs()
                .forEach(c -> privateRegistry.registerCodec(c));
        HtsDefaultRegistry.htsDefaultCodecRegistry.getReadsResolver().getCodecs().
                forEach(c -> privateRegistry.registerCodec(c));
        HtsDefaultRegistry.htsDefaultCodecRegistry.getVariantsResolver().getCodecs()
                .forEach(c -> privateRegistry.registerCodec(c));
        return privateRegistry;
    }

    /**
     * Get the {@link HaploidReferenceResolver} for this registry.
     *
     * @return the {@link HaploidReferenceResolver} for this registry
     */
    public synchronized HaploidReferenceResolver getHaploidReferenceResolver() { return htsHaploidReferenceResolver; }

    /**
     * Get the {@link ReadsResolver} for this registry.
     *
     * @return the {@link ReadsResolver} for this registry
     */
    public synchronized ReadsResolver getReadsResolver() { return htsReadsResolver; }

    /**
     * Get the {@link VariantsResolver} for this registry.
     *
     * @return the {@link VariantsResolver} for this registry
     */
    public synchronized VariantsResolver getVariantsResolver() { return htsVariantsResolver; }

}

