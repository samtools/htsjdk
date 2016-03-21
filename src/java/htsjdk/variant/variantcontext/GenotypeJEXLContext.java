package htsjdk.variant.variantcontext;

import htsjdk.variant.vcf.VCFConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 3/21/2016.
 */
public class GenotypeJEXLContext extends VariantJEXLContext {
    private Genotype g;

    private interface AttributeGetter {
        public Object get(Genotype g);
    }

    private static Map<String, AttributeGetter> attrs = new HashMap<String, AttributeGetter>();

    static {
        attrs.put("g", new AttributeGetter() { public Object get(Genotype g) { return g; }});
        attrs.put(VCFConstants.GENOTYPE_KEY, new AttributeGetter() { public Object get(Genotype g) { return g.getGenotypeString(); }});

        attrs.put("isHom", new AttributeGetter() { public Object get(Genotype g) { return g.isHom() ? "1" : "0"; }});
        attrs.put("isHomRef", new AttributeGetter() { public Object get(Genotype g) { return g.isHomRef() ? "1" : "0"; }});
        attrs.put("isHet", new AttributeGetter() { public Object get(Genotype g) { return g.isHet() ? "1" : "0"; }});
        attrs.put("isHomVar", new AttributeGetter() { public Object get(Genotype g) { return g.isHomVar() ? "1" : "0"; }});
        attrs.put("isCalled", new AttributeGetter() { public Object get(Genotype g) { return g.isCalled() ? "1" : "0"; }});
        attrs.put("isNoCall", new AttributeGetter() { public Object get(Genotype g) { return g.isNoCall() ? "1" : "0"; }});
        attrs.put("isMixed", new AttributeGetter() { public Object get(Genotype g) { return g.isMixed() ? "1" : "0"; }});
        attrs.put("isAvailable", new AttributeGetter() { public Object get(Genotype g) { return g.isAvailable() ? "1" : "0"; }});
        attrs.put("isPassFT", new AttributeGetter() { public Object get(Genotype g) { return g.isFiltered() ? "0" : "1"; }});
        attrs.put(VCFConstants.GENOTYPE_FILTER_KEY, new AttributeGetter() { public Object get(Genotype g) { return g.isFiltered()?  g.getFilters() : "PASS"; }});
        attrs.put(VCFConstants.GENOTYPE_QUALITY_KEY, new AttributeGetter() { public Object get(Genotype g) { return g.getGQ(); }});
    }

    public GenotypeJEXLContext(VariantContext vc, Genotype g) {
        super(vc);
        this.g = g;
    }

    public Object get(String name) {
        //should matching genotype attributes always supersede vc?
        if ( attrs.containsKey(name) ) { // dynamic resolution of name -> value via map
            return attrs.get(name).get(g);
        } else if ( g.hasAnyAttribute(name) ) {
            return g.getAnyAttribute(name);
        } else if ( g.getFilters().contains(name) ) {
            return "1";
        } else
            return super.get(name);
    }
}
