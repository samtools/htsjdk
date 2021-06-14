package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;

import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;

import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormat;

import java.util.*;

//TODO: Master TODO list:
// - add a "PublicAPI" opt-in annotation to exempt internal methods that need to be public because
//   they're shared from being part of the public API
// - resolve/clarify/rename/document the canDecodeURI/canDecodeSignature protocol
// - guard against multiple iterators
// - restore use of CloseableIterator in the interface APIs
// - implement a built-in cloud channel wrapper and replace the lambdas currently exposed as options
// - HtsInterval should be an interface
// - unify/clarify exception types
// - find a way to better align the ReadsFormat enum with content subtype strings
//   if we used Strings for codec format type instead of a locked down enum, it would
//   not only align better with the bundle content subtype concept, but it would make it
//   possible to dynamically extend this registry to additional content subtypes without
//   changing HTSJDK
// - encryption/decryption key files, etc.
// - need a way to allow the caller to provide custom codec resolution
// - display the bundle resource and the params of the winning codecs when there is more than one
// - need to support codecs that need to see the stream (can't deterministically tell from the extension)
// - Incomplete:
//      VCF, FASTA codecs
// - Missing:
//      SAM codec
//      CRAM 2.1 codec
//      BCF codec
// javadoc
// tests

// - fix CRAM codec access to the eliminate FastaDecoder getReferenceSequenceFile accessor
// - prevent the decoders that delegate to SamReaderFactory from attempting to automatically
//   resolving index files so we don't introduce incompatibilities when the SamReaderFactory
//   implementation dependency is removed
// - respect presorted in Reads encoders
// - publish the JSON Bundle JSON schema
// - upgrade API
// - support/test stdin/stdout

/**
 * Registry/cache for binding to encoders/decoders.
 */
@SuppressWarnings("rawtypes")
public class HtsCodecRegistry {
    private static final HtsCodecRegistry htsCodecRegistry = new HtsCodecRegistry();

    // maps of codec versions, by format, for each codec type
    private static HtsCodecsByFormat<HaploidReferenceFormat, HaploidReferenceCodec> haprefCodecs = new HtsCodecsByFormat<>();
    private static HtsCodecsByFormat<ReadsFormat, ReadsCodec> readsCodecs = new HtsCodecsByFormat<>();
    private static HtsCodecsByFormat<VariantsFormat, VariantsCodec> variantCodecs = new HtsCodecsByFormat<>();

    //discover any codecs on the classpath
    static { ServiceLoader.load(HtsCodec.class).forEach(htsCodecRegistry::registerCodec); }

    HtsCodecRegistry() {}

    /**
     * Add a codec to the registry
     */
    private void registerCodec(final HtsCodec codec) {
        switch (codec.getCodecType()) {
            case ALIGNED_READS:
                readsCodecs.register((ReadsCodec) codec);
                break;

            case HAPLOID_REFERENCE:
                haprefCodecs.register((HaploidReferenceCodec) codec);
                break;

            case VARIANTS:
                variantCodecs.register((VariantsCodec) codec);
                break;

            case FEATURES:
                throw new RuntimeException("Features codec type not yet implemented");

            default:
                throw new IllegalArgumentException("Unknown codec type");
        }
    }

    public static HtsCodecsByFormat<ReadsFormat, ReadsCodec> getReadsCodecs() {
        return readsCodecs;
    }

    public static HtsCodecsByFormat<VariantsFormat, VariantsCodec> getVariantCodecs() {
        return variantCodecs;
    }

    public static HtsCodecsByFormat<HaploidReferenceFormat, HaploidReferenceCodec> getHapRefCodecs() { return haprefCodecs; }

}

