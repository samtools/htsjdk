package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

/**
 *  Base class for all {@link HtsContentType#VARIANT_CONTEXTS} encoders.
 */
public interface VariantsEncoder extends HtsEncoder<VCFHeader, VariantContext> { }
