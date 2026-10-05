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

import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFUtils;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Common utility routines for VariantContext and Genotype
 *
 * @author depristo
 */
public final class CommonInfo implements Serializable {
    public static final long serialVersionUID = 1L;

    public static final double NO_LOG10_PERROR = 1.0;

    private static Set<String> NO_FILTERS = Collections.emptySet();
    private static Map<String, Object> NO_ATTRIBUTES = Collections.unmodifiableMap(new HashMap<String, Object>());

    private double log10PError = NO_LOG10_PERROR;
    // Kept alongside log10PError rather than derived from it, so a QUAL read from a file comes back as parsed
    private double phredScaledQual = toPhredScaledQual(NO_LOG10_PERROR);
    private String name = null;
    private Set<String> filters = null;
    private Map<String, Object> attributes = NO_ATTRIBUTES;

    public CommonInfo(String name, double log10PError, Set<String> filters, Map<String, Object> attributes) {
        this(name, log10PError, toPhredScaledQual(log10PError), filters, attributes);
    }

    /**
     * Creates a CommonInfo given its QUAL in both forms, so that neither is recomputed from the other.
     *
     * @param phredScaledQual the phred-scaled form of {@code log10PError}, which is not checked against it
     */
    CommonInfo(
            String name,
            double log10PError,
            double phredScaledQual,
            Set<String> filters,
            Map<String, Object> attributes) {
        this.name = name;
        setQual(log10PError, phredScaledQual);
        this.filters = filters;
        if (attributes != null && !attributes.isEmpty()) {
            this.attributes = attributes;
        }
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name
     *
     * @param name    the name associated with this information
     */
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null " + this);
        this.name = name;
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // Filter
    //
    // ---------------------------------------------------------------------------------------------------------

    public Set<String> getFiltersMaybeNull() {
        return filters;
    }

    public Set<String> getFilters() {
        return filters == null ? NO_FILTERS : Collections.unmodifiableSet(filters);
    }

    public boolean filtersWereApplied() {
        return filters != null;
    }

    public boolean isFiltered() {
        return filters != null && !filters.isEmpty();
    }

    public boolean isNotFiltered() {
        return !isFiltered();
    }

    public void addFilter(String filter) {
        if (filters == null) // immutable -> mutable
        filters = new HashSet<>();

        if (filter == null) throw new IllegalArgumentException("BUG: Attempting to add null filter " + this);
        if (getFilters().contains(filter))
            throw new IllegalArgumentException("BUG: Attempting to add duplicate filter " + filter + " at " + this);
        filters.add(filter);
    }

    public void addFilters(Collection<String> filters) {
        if (filters == null) throw new IllegalArgumentException("BUG: Attempting to add null filters at" + this);
        for (String f : filters) addFilter(f);
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // Working with log error rates
    //
    // ---------------------------------------------------------------------------------------------------------

    public boolean hasLog10PError() {
        return getLog10PError() != NO_LOG10_PERROR;
    }

    /**
     * @return the -1 * log10-based error estimate
     */
    public double getLog10PError() {
        return log10PError;
    }

    /**
     * The phred-scaled quality: exactly the value given to {@link #setPhredScaledQual}, or {@code -10} times the
     * value given to {@link #setLog10PError}. It is never -0.0, so a VCF gets QUAL 0 rather than -0.
     *
     * @return double - Phred scaled quality score
     */
    public double getPhredScaledQual() {
        return phredScaledQual;
    }

    /**
     * Sets the log10 error probability, and the phred-scaled quality to {@code -10} times it.
     *
     * @param log10PError at most 0, or {@link #NO_LOG10_PERROR} for no QUAL
     * @throws IllegalArgumentException if {@code log10PError} is greater than 0 and not {@link #NO_LOG10_PERROR}
     */
    public void setLog10PError(double log10PError) {
        setQual(log10PError, toPhredScaledQual(log10PError));
    }

    /**
     * Sets the phred-scaled quality, which {@link #getPhredScaledQual} then returns exactly, and the log10 error
     * probability to it divided by {@code -10}.
     *
     * @param phredScaledQual at least 0, or {@code -10} for no QUAL
     * @throws IllegalArgumentException if the log10 error probability it gives is greater than 0 and not
     *     {@link #NO_LOG10_PERROR}
     */
    public void setPhredScaledQual(double phredScaledQual) {
        setQual(toLog10PError(phredScaledQual), phredScaledQual);
    }

    private void setQual(final double log10PError, final double phredScaledQual) {
        // NaN and -Infinity pass, as QUAL nan and inf do in htslib
        if (log10PError > 0 && log10PError != NO_LOG10_PERROR)
            throw new IllegalArgumentException("BUG: log10PError cannot be > 0 : " + log10PError);
        this.log10PError = log10PError;
        // (-0.0) + 0.0 = 0.0
        this.phredScaledQual = phredScaledQual + 0.0;
    }

    /** The phred-scaled form of a log10 error probability. */
    static double toPhredScaledQual(final double log10PError) {
        return log10PError * -10;
    }

    /** The log10 error probability of a phred-scaled quality. */
    static double toLog10PError(final double phredScaledQual) {
        return phredScaledQual / -10.0;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // A stream from an htsjdk without this field leaves it 0.0; recomputing it changes nothing when 0.0 is real
        if (phredScaledQual == 0.0) setLog10PError(log10PError);
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // Working with attributes
    //
    // ---------------------------------------------------------------------------------------------------------
    public void clearAttributes() {
        attributes = new HashMap<String, Object>();
    }

    /**
     * @return the attribute map
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // todo -- define common attributes as enum

    public void setAttributes(Map<String, ?> map) {
        clearAttributes();
        putAttributes(map);
    }

    public void putAttribute(String key, Object value) {
        putAttribute(key, value, false);
    }

    public void putAttribute(String key, Object value, boolean allowOverwrites) {
        if (!allowOverwrites && hasAttribute(key))
            throw new IllegalStateException(
                    "Attempting to overwrite key->value binding: key = " + key + " this = " + this);

        if (attributes == NO_ATTRIBUTES) // immutable -> mutable
        attributes = new HashMap<String, Object>();

        attributes.put(key, value);
    }

    public void removeAttribute(String key) {
        if (attributes == NO_ATTRIBUTES) // immutable -> mutable
        attributes = new HashMap<String, Object>();
        attributes.remove(key);
    }

    public void putAttributes(Map<String, ?> map) {
        if (map != null) {
            // for efficiency, we can skip the validation if the map is empty
            if (attributes.isEmpty()) {
                if (attributes == NO_ATTRIBUTES) // immutable -> mutable
                attributes = new HashMap<String, Object>();
                attributes.putAll(map);
            } else {
                for (Map.Entry<String, ?> elt : map.entrySet()) {
                    putAttribute(elt.getKey(), elt.getValue(), false);
                }
            }
        }
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    public int getNumAttributes() {
        return attributes.size();
    }

    /**
     * @param key    the attribute key
     *
     * @return the attribute value for the given key (or null if not set)
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Object getAttribute(String key, Object defaultValue) {
        if (hasAttribute(key)) return attributes.get(key);
        else return defaultValue;
    }

    /**
     * Gets the attributes from a key as a list.
     *
     * Note: int[] and double[] arrays are boxed.
     *
     * @return empty list if the key was not found; {@link Collections#singletonList(Object)} if
     * there is only one value; a list containing the values if the value is a {@link List} or array.
     */
    @SuppressWarnings("unchecked")
    public List<Object> getAttributeAsList(String key) {
        Object o = getAttribute(key);
        if (o == null) return Collections.emptyList();
        if (o instanceof List) return (List<Object>) o;
        if (o.getClass().isArray()) {
            if (o instanceof int[]) {
                return Arrays.stream((int[]) o).boxed().collect(Collectors.toList());
            } else if (o instanceof double[]) {
                return Arrays.stream((double[]) o).boxed().collect(Collectors.toList());
            }
            return Arrays.asList((Object[]) o);
        }
        return Collections.singletonList(o);
    }

    private <T> List<T> getAttributeAsList(String key, Function<Object, T> transformer) {
        return getAttributeAsList(key).stream().map(transformer).collect(Collectors.toList());
    }

    public List<String> getAttributeAsStringList(String key, String defaultValue) {
        return getAttributeAsList(key, x -> (x == null) ? defaultValue : String.valueOf(x));
    }

    public List<Integer> getAttributeAsIntList(String key, Integer defaultValue) {
        return getAttributeAsList(key, x -> {
            if (x == null || VCFConstants.MISSING_VALUE_v4.equals(x)) {
                return defaultValue;
            } else if (x instanceof Number) {
                return ((Number) x).intValue();
            } else {
                return Integer.valueOf((String) x); // throws an exception if this isn't a string
            }
        });
    }

    public List<Double> getAttributeAsDoubleList(String key, Double defaultValue) {
        return getAttributeAsList(key, x -> {
            if (x == null || VCFConstants.MISSING_VALUE_v4.equals(x)) {
                return defaultValue;
            } else if (x instanceof Number) {
                return ((Number) x).doubleValue();
            } else {
                return VCFUtils.parseVcfDouble((String) x); // throws an exception if this isn't a string
            }
        });
    }

    public String getAttributeAsString(String key, String defaultValue) {
        Object x = getAttribute(key);
        if (x == null) return defaultValue;
        if (x instanceof String) return (String) x;
        return String.valueOf(x); // throws an exception if this isn't a string
    }

    public int getAttributeAsInt(String key, int defaultValue) {
        Object x = getAttribute(key);
        if (x == null || VCFConstants.MISSING_VALUE_v4.equals(x)) return defaultValue;
        if (x instanceof Integer) return (Integer) x;
        return Integer.parseInt((String) x); // throws an exception if this isn't a string
    }

    public double getAttributeAsDouble(String key, double defaultValue) {
        Object x = getAttribute(key);
        if (x == null) return defaultValue;
        if (x instanceof Double) return (Double) x;
        if (x instanceof Integer) return (Integer) x;
        return VCFUtils.parseVcfDouble((String) x); // throws an exception if this isn't a string
    }

    public boolean getAttributeAsBoolean(String key, boolean defaultValue) {
        Object x = getAttribute(key);
        if (x == null) return defaultValue;
        if (x instanceof Boolean) return (Boolean) x;
        return Boolean.valueOf((String) x); // throws an exception if this isn't a string
    }

    //    public String getAttributeAsString(String key)      { return (String.valueOf(getExtendedAttribute(key))); } //
    // **NOTE**: will turn a null Object into the String "null"
    //    public int getAttributeAsInt(String key)            { Object x = getExtendedAttribute(key); return x
    // instanceof Integer ? (Integer)x : Integer.valueOf((String)x); }
    //    public double getAttributeAsDouble(String key)      { Object x = getExtendedAttribute(key); return x
    // instanceof Double ? (Double)x : Double.valueOf((String)x); }
    //    public boolean getAttributeAsBoolean(String key)      { Object x = getExtendedAttribute(key); return x
    // instanceof Boolean ? (Boolean)x : Boolean.valueOf((String)x); }
    //    public Integer getAttributeAsIntegerNoException(String key)  { try {return getAttributeAsInt(key);} catch
    // (Exception e) {return null;} }
    //    public Double getAttributeAsDoubleNoException(String key)    { try {return getAttributeAsDouble(key);} catch
    // (Exception e) {return null;} }
    //    public String getAttributeAsStringNoException(String key)    { if (getExtendedAttribute(key) == null) return
    // null; return getAttributeAsString(key); }
    //    public Boolean getAttributeAsBooleanNoException(String key)  { try {return getAttributeAsBoolean(key);} catch
    // (Exception e) {return null;} }
}
