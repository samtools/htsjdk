package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

/**
 *  Defines the type parameters instantiated for variants encoders.
 */
public interface VariantsEncoder extends HtsEncoder<VCFHeader, VariantContext> { }
