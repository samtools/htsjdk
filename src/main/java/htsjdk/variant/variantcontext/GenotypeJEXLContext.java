package htsjdk.variant.variantcontext;

import htsjdk.variant.vcf.VCFConstants;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author bbimber
 *
 * implements the JEXL context for Genotype; this saves us from
 * having to generate a JEXL context lookup map everytime we want to evaluate an expression.
 *
 */
public class GenotypeJEXLContext extends VariantJEXLContext {
    private Genotype g;

    private interface AttributeGetter {
        public Object get(Genotype g);
    }

    private static Map<String, AttributeGetter> attributes = new HashMap<String, AttributeGetter>();

    static {
        attributes.put("g", (Genotype g) -> g);
        attributes.put(VCFConstants.GENOTYPE_KEY, Genotype::getGenotypeString);

        attributes.put("isHom", (Genotype g) -> g.isHom() ? true_string : false_string);
        attributes.put("isHomRef", (Genotype g) -> g.isHomRef() ? true_string : false_string);
        attributes.put("isHet", (Genotype g) -> g.isHet() ? true_string : false_string);
        attributes.put("isHomVar", (Genotype g) -> g.isHomVar() ? true_string : false_string);
        attributes.put("isCalled", (Genotype g) -> g.isCalled() ? true_string : false_string);
        attributes.put("isNoCall", (Genotype g) -> g.isNoCall() ? true_string : false_string);
        attributes.put("isMixed", (Genotype g) -> g.isMixed() ? true_string : false_string);
        attributes.put("isAvailable", (Genotype g) -> g.isAvailable() ? true_string : false_string);
        attributes.put("isPassFT", (Genotype g) -> g.isFiltered() ? false_string : true_string);
        attributes.put(VCFConstants.GENOTYPE_FILTER_KEY, (Genotype g) -> g.isFiltered()?  g.getFilters() : VCFConstants.PASSES_FILTERS_v4);
        attributes.put(VCFConstants.GENOTYPE_QUALITY_KEY, Genotype::getGQ);
    }

    public GenotypeJEXLContext(VariantContext vc, Genotype g) {
        super(vc);
        this.g = g;
    }

    @Override
    public Object get(String name) {
        //should matching genotype attributes always supersede vc?
        if ( attributes.containsKey(name) ) { // dynamic resolution of name -> value via map
            return attributes.get(name).get(g);
        } else if ( g.hasAnyAttribute(name) ) {
            return g.getAnyAttribute(name);
        } else if ( g.getFilters() != null && g.getFilters().contains(name) ) {
            return true_string;
        } else
            return super.get(name);
    }
}
