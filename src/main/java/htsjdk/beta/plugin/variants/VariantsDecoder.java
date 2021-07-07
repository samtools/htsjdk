package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

/**
 *  Base class for all {@link HtsContentType#VARIANT_CONTEXTS} decoders.
 */
public interface VariantsDecoder extends HtsDecoder<VCFHeader, VariantContext> { }
