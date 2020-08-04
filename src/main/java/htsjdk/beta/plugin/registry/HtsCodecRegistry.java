package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;

import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;

import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormat;

import java.util.*;

// To add a new codec type T (new interfaces):
//
//      define the header and record interfaces
//      define the corresponding HtsCodec, HtsEncoder and HtsDecoder subinterfaces
//      add a new HtsCodecsForType variable to the registry
//      update registerCodec to register discovered codecs of that type
//      add getEncoder and getDecoder entry points to the registry:
//
//        getTDecoder(IOPath)
//        getTDecoder(IOPath, ReadOptions)
//        getTDecoder(Bundle)
//        getTDecoder(Bundle, ReadOptions)
//        getTDecoder(Bundle, ReadOptions, HtsCodecVersion)
//
//        getTEncoder(IOPath)
//        getTEncoder(IOPath, WriteOptions)
//        getTEncoder(Bundle)
//        getTEncoder(Bundle, WriteOptions)
//        getTEncoder(Bundle, WriteOptions, HtsCodecVersion)

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

    public final static String NO_CODEC_MSG_FORMAT_STRING = "A %s codec capable of handling \"%s\" could not be found";

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

