package htsjdk.variant.variantcontext;

import htsjdk.variant.variantcontext.VariantContextUtils.JexlVCMatchExp;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlException;
import org.apache.commons.jexl2.MapContext;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This is an implementation of a Map of {@link JexlVCMatchExp} to true or false values.
 * It lazily initializes each value as requested to save as much processing time as possible.
 */

class JEXLMap implements Map<JexlVCMatchExp, Boolean> {

    // our context
    private JexlContext jContext = null;
    // our variant context and/or Genotype
    private final VariantContext vc;
    private final Genotype g;

    /**
     * our mapping from {@link JexlVCMatchExp} to {@link Boolean}s, which will be set to {@code NULL}
     * for previously un-cached {@link JexlVCMatchExp}.
     */
    private Map<JexlVCMatchExp,Boolean> jexl;

    public JEXLMap(final Collection<JexlVCMatchExp> jexlCollection, final VariantContext vc, final Genotype g) {
        initialize(jexlCollection);
        this.vc = vc;
        this.g = g;
    }

    public JEXLMap(final Collection<JexlVCMatchExp> jexlCollection, final VariantContext vc) {
        this(jexlCollection, vc, null);
    }

    /**
     * Note: due to laziness, this accessor actually modifies the instance by possibly forcing evaluation of an Jexl expression.
     *
     * @throws IllegalArgumentException when {@code o} is {@code null} or
     *                                  when any of the JexlVCMatchExp (i.e. keys) contains invalid Jexl expressions.
     */
    public Boolean get(Object o) {
        if(o == null){
            throw new IllegalArgumentException("Query key is null");
        }

        // if we've already determined the value, return it
        if (jexl.containsKey(o) && jexl.get(o) != null) return jexl.get(o);

        // otherwise cast the expression and try again
        final JexlVCMatchExp e = (JexlVCMatchExp) o;
        evaluateExpression(e);
        return jexl.get(e);
    }

    /**
     * do we contain the specified key
     * @param o the key
     * @return true if we have a value for that key
     */
    public boolean containsKey(Object o) { return jexl.containsKey(o); }

    public Set<JexlVCMatchExp> keySet() {
        return jexl.keySet();
    }

    /**
     * Get all the values of the map, i.e. the {@link Boolean} values.
     * This is an expensive call, since it evaluates all keys that haven't been evaluated yet.
     * This is fine if you truly want all the keys, but if you only want a portion, or  know
     * the keys you want, you would be better off using get() to get them by name.
     *
     * Note: due to laziness, this accessor actually modifies the instance by possibly forcing evaluation of an Jexl expression.
     *
     * @return a collection of boolean values, representing the results of all the variants evaluated
     *
     * @throws IllegalArgumentException when any of the JexlVCMatchExp (i.e. keys) contains invalid Jexl expressions.
     */
    public Collection<Boolean> values() {
        for (final JexlVCMatchExp exp : jexl.keySet()) {
            if (jexl.get(exp) == null) {
                evaluateExpression(exp);
            }
        }
        return jexl.values();
    }

    /**
     * @return the number of keys, i.e. {@link JexlVCMatchExp}'s held by this mapping.
     */
    public int size() {
        return jexl.size();
    }

    public boolean isEmpty() { return this.jexl.isEmpty(); }

    public Boolean put(JexlVCMatchExp jexlVCMatchExp, Boolean aBoolean) {
        return jexl.put(jexlVCMatchExp, aBoolean);
    }

    public void putAll(Map<? extends JexlVCMatchExp, ? extends Boolean> map) {
        jexl.putAll(map);
    }

    /**
     * Initializes all keys with null values indicating that they have not yet been evaluated.
     * The actual value will be computed only when the key is requested via {@link #get(Object)} or {@link #values()}.
     */
    private void initialize(Collection<JexlVCMatchExp> jexlCollection) {
        jexl = new HashMap<>();
        for (final JexlVCMatchExp exp: jexlCollection) {
            jexl.put(exp, null);
        }
    }

    /**
     * Evaluates a {@link JexlVCMatchExp}'s expression, given the current context (and setup the context if it's {@code null}).
     *
     * @param exp the {@link JexlVCMatchExp} to evaluate
     *
     * @throws IllegalArgumentException when {@code exp} is {@code null}, or
     *                                  when the Jexl expression in {@code exp} fails to evaluate the JexlContext
     *                                  constructed with the input VC or genotype.
     */
    private void evaluateExpression(final JexlVCMatchExp exp) {
        // if the context is null, we need to create it to evaluate the JEXL expression
        if (this.jContext == null){
            createContext();
        }

        try {
            final Boolean value = (Boolean) exp.exp.evaluate(jContext);
            // treat errors as no match
            jexl.put(exp, value == null ? false : value);
        } catch (final JexlException.Variable e){
            // if exception happens because variable is undefined (i.e. field in expression is not present), evaluate to FALSE
            jexl.put(exp,false);
        } catch (final JexlException e) {
            // todo - might be better if no exception is caught here but let's user decide how to deal with them; note this will propagate to get() and values()
            throw new IllegalArgumentException(String.format("Invalid JEXL expression detected for %s with message %s",
                    exp.name, e.getMessage()));
        }
    }

    /**
     * Create the internal JexlContext, only when required.
     * This code is where new JEXL context variables should get added.
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

    // //////////////////////////////////////////////////////////////////////////////////////
    // The Following are unsupported at the moment (date: 2016/08/18)
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
