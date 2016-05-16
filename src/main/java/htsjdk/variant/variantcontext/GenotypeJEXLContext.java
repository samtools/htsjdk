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

        attributes.put("isHom", (Genotype g) -> g.isHom() ? "1" : "0");
        attributes.put("isHomRef", (Genotype g) -> g.isHomRef() ? "1" : "0");
        attributes.put("isHet", (Genotype g) -> g.isHet() ? "1" : "0");
        attributes.put("isHomVar", (Genotype g) -> g.isHomVar() ? "1" : "0");
        attributes.put("isCalled", (Genotype g) -> g.isCalled() ? "1" : "0");
        attributes.put("isNoCall", (Genotype g) -> g.isNoCall() ? "1" : "0");
        attributes.put("isMixed", (Genotype g) -> g.isMixed() ? "1" : "0");
        attributes.put("isAvailable", (Genotype g) -> g.isAvailable() ? "1" : "0");
        attributes.put("isPassFT", (Genotype g) -> g.isFiltered() ? "0" : "1");
        attributes.put(VCFConstants.GENOTYPE_FILTER_KEY, (Genotype g) -> g.isFiltered()?  g.getFilters() : "PASS");
        attributes.put(VCFConstants.GENOTYPE_QUALITY_KEY, Genotype::getGQ);
    }

    public GenotypeJEXLContext(VariantContext vc, Genotype g) {
        super(vc);
        this.g = g;
    }

    public Object get(String name) {
        //should matching genotype attributes always supersede vc?
        if ( attributes.containsKey(name) ) { // dynamic resolution of name -> value via map
            return attributes.get(name).get(g);
        } else if ( g.hasAnyAttribute(name) ) {
            return g.getAnyAttribute(name);
        } else if ( g.getFilters().contains(name) ) {
            return "1";
        } else
            return super.get(name);
    }
}
