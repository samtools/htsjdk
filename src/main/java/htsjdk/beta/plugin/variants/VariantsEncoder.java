package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

public interface VariantsEncoder extends HtsEncoder<VariantsFormat, VCFHeader, VariantContext> { }
