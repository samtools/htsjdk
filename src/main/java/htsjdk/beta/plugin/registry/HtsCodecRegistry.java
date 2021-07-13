package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.exception.HtsjdkPluginException;

/**
 * A registry for tracking {@link HtsCodec} instances.
 * <p>
 * Registries are populated with {@link HtsCodec} objects that are either discovered
 * and registered statically in the {@link HtsDefaultRegistry}, or manually registered in a private
 * registry (see {@link HtsCodecRegistry#createPrivateRegistry()}) via the {@link #registerCodec(HtsCodec)}
 * method.
 *
 * The global static {@link HtsDefaultRegistry} is immutable. A private, mutable registry for registering
 * custom codecs can be created with {@link HtsCodecRegistry#createPrivateRegistry()}.
 */
public class HtsCodecRegistry {
    private final HaploidReferenceResolver htsHaploidReferenceResolver = new HaploidReferenceResolver();
    private final ReadsResolver htsReadsResolver = new ReadsResolver();
    private final VariantsResolver htsVariantsResolver = new VariantsResolver();

    /**
     * Create a registry. Protected to prevent use outside of the registry package. To create
     * a private registry from outside the registry package, use {@link #createPrivateRegistry}.
     */
    protected HtsCodecRegistry() { }

    /**
     * Add a codec to the registry. If a codec that supports the same (format, version) (determined
     * by {@link HtsCodec#getFileFormat()} and {@link HtsCodec#getVersion()} methods) is already
     * registered, the new registry is updated to contain the new codec, and the previously registered
     * codec is returned.
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
     * Create a mutable registry for private use. The {@link HtsDefaultRegistry} is immutable, but
     * a private registry can be populated with, and used to resolve against, custom codecs. A private registry
     * is initially populated with all codecs that are registered in the {@link HtsDefaultRegistry}. Custom
     * codecs can be then be installed into the private registry using
     * {@link HtsCodecRegistry#registerCodec(HtsCodec)}.
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

