/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.variantcontext;

import org.apache.commons.jexl2.JexlContext;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author aaron
 * @author depristo
 *
 * Class VariantJEXLContext
 *
 * implements the JEXL context for VariantContext; this saves us from
 * having to generate a JEXL context lookup map everytime we want to evaluate an expression.
 *
 * This is package protected, only classes in variantcontext should have access to it.
 *
 */

class VariantJEXLContext implements JexlContext {
    // our stored variant context
    private VariantContext vc;

    private interface AttributeGetter {
        public Object get(VariantContext vc);
    }

    private static Map<String, AttributeGetter> attributes = new HashMap<String, AttributeGetter>();

    static {
        attributes.put("vc", (VariantContext vc) -> vc);
        attributes.put("CHROM", VariantContext::getChr);
        attributes.put("POS", VariantContext::getStart);
        attributes.put("TYPE", (VariantContext vc) -> vc.getType().toString());
        attributes.put("QUAL", (VariantContext vc) -> -10 * vc.getLog10PError());
        attributes.put("ALLELES", VariantContext::getAlleles);
        attributes.put("N_ALLELES", VariantContext::getNAlleles);
        attributes.put("FILTER", (VariantContext vc) -> vc.isFiltered() ? "1" : "0");

        attributes.put("homRefCount", VariantContext::getHomRefCount);
        attributes.put("hetCount", VariantContext::getHetCount);
        attributes.put("homVarCount", VariantContext::getHomVarCount);
    }

    public VariantJEXLContext(VariantContext vc) {
        this.vc = vc;
    }

    public Object get(String name) {
        Object result = null;
        if ( attributes.containsKey(name) ) { // dynamic resolution of name -> value via map
            result = attributes.get(name).get(vc);
        } else if ( vc.hasAttribute(name)) {
            result = vc.getAttribute(name);
        } else if ( vc.getFilters().contains(name) ) {
            result = "1";
        }

        //System.out.printf("dynamic lookup %s => %s%n", name, result);

        return result;
    }

    public boolean has(String name) {
        return get(name) != null;
    }

    public void	set(String name, Object value) {
        throw new UnsupportedOperationException("remove() not supported on a VariantJEXLContext");
    }
}




