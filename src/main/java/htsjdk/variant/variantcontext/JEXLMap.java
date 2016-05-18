package htsjdk.variant.variantcontext;

import htsjdk.variant.variantcontext.VariantContextUtils.JexlVCMatchExp;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.MapContext;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * this is an implementation of a Map of JexlVCMatchExp to true or false values.  It lazy initializes each value
 * as requested to save as much processing time as possible.
 *
 * Compatible with JEXL 1.1 (this code will be easier if we move to 2.0, all of the functionality can go into the
 * JexlContext's get()
 *
 */

class JEXLMap implements Map<JexlVCMatchExp, Boolean> {
    // our variant context and/or Genotype
    private final VariantContext vc;
    private final Genotype g;

    // our context
    private JexlContext jContext = null;

    // our mapping from JEXLVCMatchExp to Booleans, which will be set to NULL for previously uncached JexlVCMatchExp
    private Map<JexlVCMatchExp,Boolean> jexl;


    public JEXLMap(Collection<JexlVCMatchExp> jexlCollection, VariantContext vc, Genotype g) {
        this.vc = vc;
        this.g = g;
        initialize(jexlCollection);
    }

    public JEXLMap(Collection<JexlVCMatchExp> jexlCollection, VariantContext vc) {
        this(jexlCollection, vc, null);
    }

    private void initialize(Collection<JexlVCMatchExp> jexlCollection) {
        jexl = new HashMap<JexlVCMatchExp,Boolean>();
        for (JexlVCMatchExp exp: jexlCollection) {
            jexl.put(exp, null);
        }
    }

    /**
     * create the internal JexlContext, only when required.  This code is where new JEXL context variables
     * should get added.
     *
     */
    private void createContext() {
        if ( vc == null ) {
            jContext = new MapContext(Collections.emptyMap());
        }
        else if (g == null) {
            jContext = new VariantJEXLContext(vc);
        }
        else {
            jContext = new GenotypeJEXLContext(vc, g);
        }
    }

    /**
     * @return the size of the internal data structure
     */
    public int size() {
        return jexl.size();
    }

    /**
     * @return true if we're empty
     */
    public boolean isEmpty() { return this.jexl.isEmpty(); }

    /**
     * do we contain the specified key
     * @param o the key
     * @return true if we have a value for that key
     */
    public boolean containsKey(Object o) { return jexl.containsKey(o); }

    public Boolean get(Object o) {
        // if we've already determined the value, return it
        if (jexl.containsKey(o) && jexl.get(o) != null) return jexl.get(o);

        // try and cast the expression
        JexlVCMatchExp e = (JexlVCMatchExp) o;
        evaluateExpression(e);
        return jexl.get(e);
    }

    /**
     * get the keyset of map
     * @return a set of keys of type JexlVCMatchExp
     */
    public Set<JexlVCMatchExp> keySet() {
        return jexl.keySet();
    }

    /**
     * get all the values of the map.  This is an expensive call, since it evaluates all keys that haven't
     * been evaluated yet.  This is fine if you truely want all the keys, but if you only want a portion, or  know
     * the keys you want, you would be better off using get() to get them by name.
     * @return a collection of boolean values, representing the results of all the variants evaluated
     */
    public Collection<Boolean> values() {
        // this is an expensive call
        for (JexlVCMatchExp exp : jexl.keySet())
            if (jexl.get(exp) == null)
                evaluateExpression(exp);
        return jexl.values();
    }

    /**
     * evaulate a JexlVCMatchExp's expression, given the current context (and setup the context if it's null)
     * @param exp the JexlVCMatchExp to evaluate
     */
    private void evaluateExpression(JexlVCMatchExp exp) {
        // if the context is null, we need to create it to evaluate the JEXL expression
        if (this.jContext == null) createContext();
        try {
            final Boolean value = (Boolean) exp.exp.evaluate(jContext);
            // treat errors as no match
            jexl.put(exp, value == null ? false : value);
        } catch (Exception e) {
            // if exception happens because variable is undefined (i.e. field in expression is not present), evaluate to FALSE
            // todo - might be safer if we explicitly checked for an exception type, but Apache's API doesn't seem to have that ability
            if (e.getMessage() != null && e.getMessage().contains("undefined variable"))
                jexl.put(exp,false);
            else
                throw new IllegalArgumentException(String.format("Invalid JEXL expression detected for %s with message %s", exp.name, (e.getMessage() == null ? "no message" : e.getMessage())));
        }
    }

    /**
     * helper function: adds the list of attributes to the information map we're building
     * @param infoMap the map
     * @param attributes the attributes
     */
    private static void addAttributesToMap(Map<String, Object> infoMap, Map<String, ?> attributes ) {
        for (Entry<String, ?> e : attributes.entrySet()) {
            infoMap.put(e.getKey(), String.valueOf(e.getValue()));
        }
    }

    public Boolean put(JexlVCMatchExp jexlVCMatchExp, Boolean aBoolean) {
        return jexl.put(jexlVCMatchExp,aBoolean);
    }

    public void putAll(Map<? extends JexlVCMatchExp, ? extends Boolean> map) {
        jexl.putAll(map);
    }

    // //////////////////////////////////////////////////////////////////////////////////////
    // The Following are unsupported at the moment
    // //////////////////////////////////////////////////////////////////////////////////////

    // this doesn't make much sense to implement, boolean doesn't offer too much variety to deal
    // with evaluating every key in the internal map.
    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException("containsValue() not supported on a JEXLMap");
    }

    // this doesn't make much sense
    public Boolean remove(Object o) {
        throw new UnsupportedOperationException("remove() not supported on a JEXLMap");
    }


    public Set<Entry<JexlVCMatchExp, Boolean>> entrySet() {
        throw new UnsupportedOperationException("clear() not supported on a JEXLMap");
    }

    // nope
    public void clear() {
        throw new UnsupportedOperationException("clear() not supported on a JEXLMap");
    }
}
