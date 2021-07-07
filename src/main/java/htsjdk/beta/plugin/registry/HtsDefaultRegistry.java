package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import java.util.ServiceLoader;

/**
 * A global, static, immutable, public registry for {@link HtsCodec} instances. The {@link HtsDefaultRegistry}
 * is populated using dynamic discovery of {@link HtsCodec} implementations by a {@link ServiceLoader}.
 *
 * To create a private, mutable registry for resolving inputs against custom codec implementations, use
 * {@link HtsCodecRegistry#createPrivateRegistry()}.
 */
public class HtsDefaultRegistry {
    // package accessible for access by HtsCodecRegistry
    static final HtsCodecRegistry htsDefaultCodecRegistry = new HtsCodecRegistry();

    /**
     * statically populate the default registry with any codecs on the classpath
     */
    static {ServiceLoader.load(HtsCodec .class).forEach(htsDefaultCodecRegistry::registerCodec);}

    /**
     * Grt the {@link HaploidReferenceResolver} resolver for this registry.
     *
     * @return the {@link HaploidReferenceResolver} resolver for this registry
     */
    public static synchronized HaploidReferenceResolver getHaploidReferenceResolver() {
        return htsDefaultCodecRegistry.getHaploidReferenceResolver(); }

    /**
     * Gt the {@link ReadsResolver} resolver for this registry.
     *
     * @return the {@link ReadsResolver} resolver for this registry
     */
    public static synchronized ReadsResolver getReadsResolver() {
        return htsDefaultCodecRegistry.getReadsResolver(); }

    /**
     * Get the {@link VariantsResolver} resolver for this registry.
     *
     * @return the {@link VariantsResolver} resolver for this registry
     */
    public static synchronized VariantsResolver getVariantsResolver() {
        return htsDefaultCodecRegistry.getVariantsResolver(); }

}
