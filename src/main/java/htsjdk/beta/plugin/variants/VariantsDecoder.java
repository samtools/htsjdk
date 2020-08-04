package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

public interface VariantsDecoder extends HtsDecoder<VariantsFormat, VCFHeader, VariantContext> { }
