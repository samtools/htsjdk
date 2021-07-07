package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

/**
 *  Defines the type parameters instantiated for variants decoders.
 */
public interface VariantsDecoder extends HtsDecoder<VCFHeader, VariantContext> { }
