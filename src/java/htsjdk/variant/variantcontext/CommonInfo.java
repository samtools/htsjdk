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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
    private String name = null;
    private Set<String> filters = null;
    private Map<String, Object> attributes = NO_ATTRIBUTES;

    public CommonInfo(final String name, final double log10PError, final Set<String> filters, final Map<String, Object> attributes) {
        this.name = name;
        setLog10PError(log10PError);
        this.filters = filters;
        if ( attributes != null && ! attributes.isEmpty() ) {
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
    public void setName(final String name) {
        if ( name == null ) throw new IllegalArgumentException("Name cannot be null " + this);
        this.name = name;
    }


    // ---------------------------------------------------------------------------------------------------------
    //
    // Filter
    //
    // ---------------------------------------------------------------------------------------------------------

    /** @return a <b>modifiable</b> Set of filters. Can be  null. All changes in this set will be reflected in the CommonInfo */
    public Set<String> getFiltersMaybeNull() {
        return filters;
    }

    /** @return an unmodifiable Set of filters. Can be empty by never null */
    public Set<String> getFilters() {
        return filters == null ? NO_FILTERS : Collections.unmodifiableSet(filters);
    }

    /** @return true if no filter has been defined  <code>(getFiltersMaybeNull()==null)</code> */
    public boolean filtersWereApplied() {
        return filters != null;
    }

    /** @return true if any filter been defined  <code>(!filters.isEmpty())</code> */
    public boolean isFiltered() {
        return filters == null ? false : !filters.isEmpty();
    }

    /**
     * @param filter the filter ID 
     * @return true if the filters contains 'filter' */
    public boolean hasFilter(final String filter) {
        return filters == null ? false : filters.contains(filter);
    }
    
    /** shortcut of <code>!isFiltered()</code> */
    public boolean isNotFiltered() {
        return ! isFiltered();
    }

    /** adds a FILTER 
     * @param filter filter ID to add
     * @throws IllegalArgumentException if filter is null or if filter already exists.
     * */
    public void addFilter(final String filter) {
        if ( filters == null ) // immutable -> mutable
            filters = new HashSet<String>();

        if ( filter == null ) throw new IllegalArgumentException("BUG: Attempting to add null filter " + this);
        if ( hasFilter(filter) ) throw new IllegalArgumentException("BUG: Attempting to add duplicate filter " + filter + " at " + this);
        filters.add(filter);
    }

    /** add a collection of FILTER, calling <code>addFilter(filter)</code>
     * @param filters the filters to be added
     * @throws IllegalArgumentException if filters is null
     * */
    public void addFilters(final Collection<String> filters) {
        if ( filters == null ) throw new IllegalArgumentException("BUG: Attempting to add null filters at" + this);
        for ( final String f : filters )
            addFilter(f);
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // Working with log error rates
    //
    // ---------------------------------------------------------------------------------------------------------

    /** @return true if log10-based error estimate has been set */
    public boolean hasLog10PError() {
        return getLog10PError() != NO_LOG10_PERROR;
    }

    /**
     * @return the -1 * log10-based error estimate
     */
    public double getLog10PError() { return log10PError; }

    /**
     * Floating-point arithmetic allows signed zeros such as +0.0 and -0.0.
     * Adding the constant 0.0 to the result ensures that the returned value is never -0.0
     * since (-0.0) + 0.0 = 0.0.
     *
     * When this is set to '0.0', the resulting VCF would be 0 instead of -0.
     *
     * @return double - Phred scaled quality score
     */
    public double getPhredScaledQual() { return (getLog10PError() * -10) + 0.0; }

    /** set the Phred scaled quality score */
    public void setLog10PError(final double log10PError) {
        if ( log10PError > 0 && log10PError != NO_LOG10_PERROR)
            throw new IllegalArgumentException("BUG: log10PError cannot be > 0 : " + this.log10PError);
        if ( Double.isInfinite(this.log10PError) )
            throw new IllegalArgumentException("BUG: log10PError should not be Infinity");
        if ( Double.isNaN(this.log10PError) )
            throw new IllegalArgumentException("BUG: log10PError should not be NaN");
        this.log10PError = log10PError;
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // Working with attributes
    //
    // ---------------------------------------------------------------------------------------------------------
    /** removes all attributes */
    public void clearAttributes() {
        attributes = new HashMap<String, Object>();
    }

    /**
     * return an unmodifiable Map of attributes
     * @return the attribute map
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // todo -- define common attributes as enum
    /** clear all attributes, and set 'map' as attributes */
    public void setAttributes(final Map<String, ?> map) {
        clearAttributes();
        putAttributes(map);
    }

    /** shortcut of <code>putAttribute(key, value, false)</code> */
    public void putAttribute(final String key, final Object value) {
        putAttribute(key, value, false);
    }

    /** insert a new attribute, raises an exception if allowOverwrites==true and the attribute exists */
    public void putAttribute(final String key, final Object value, final boolean allowOverwrites) {
        if ( ! allowOverwrites && hasAttribute(key) )
            throw new IllegalStateException("Attempting to overwrite key->value binding: key = " + key + " this = " + this);

        if ( attributes == NO_ATTRIBUTES ) // immutable -> mutable
            attributes = new HashMap<String, Object>();

        attributes.put(key, value);
    }

    /** removes the attribute identified by key */
    public void removeAttribute(final String key) {
        if ( attributes == NO_ATTRIBUTES ) // immutable -> mutable
            attributes = new HashMap<String, Object>();
        attributes.remove(key);
    }

    /** insert the attibutes as map using <code>putAttribute(key,value,false)</code> */
    public void putAttributes(final Map<String, ?> map) {
        if ( map != null ) {
            // for efficiency, we can skip the validation if the map is empty
            if (attributes.isEmpty()) {
                if ( attributes == NO_ATTRIBUTES ) // immutable -> mutable
                    attributes = new HashMap<String, Object>();
                attributes.putAll(map);
            } else {
                for ( final Map.Entry<String, ?> elt : map.entrySet() ) {
                    putAttribute(elt.getKey(), elt.getValue(), false);
                }
            }
        }
    }

    /** @return true if the key is present */
    public boolean hasAttribute(final String key) {
        return attributes.containsKey(key);
    }

    /** @return the number of attributes */
    public int getNumAttributes() {
        return attributes.size();
    }

    /**
     * @param key    the attribute key
     *
     * @return the attribute value for the given key (or null if not set)
     */
    public Object getAttribute(final String key) {
        return attributes.get(key);
    }

    /**
     * @param key    the attribute key
     * @param defaultValue    the default value
     *
     * @return the attribute value for the given key (or defaultValue if not set)
     */
   public Object getAttribute(final String key, final Object defaultValue) {
        if ( hasAttribute(key) )
            return attributes.get(key);
        else
            return defaultValue;
    }

    /** returns the value as an empty list if the key was not found,
        as a java.util.List if the value is a List or an Array,
        as a Collections.singletonList if there is only one value */
    @SuppressWarnings("unchecked")
    public List<Object> getAttributeAsList(final String key) {
        final Object o = getAttribute(key);
        if ( o == null ) return Collections.emptyList();
        else if ( o instanceof List ) return (List<Object>)o;
        else if ( o instanceof int[]) {
            final int array[]=(int[])o;
            final List<Object> list = new ArrayList<>( array.length );
            for( int i : array) list.add(i);
            return list;
        } else if ( o instanceof double[]) {
            final double array[]=(double[])o;
            final List<Object> list = new ArrayList<>( array.length );
            for( double i : array) list.add(i);
            return list;
        }
        else if ( o.getClass().isArray() ) return Arrays.asList((Object[])o);
        return Collections.singletonList(o);
    }

    /**
     * return an attribute as a String.
     * if given key is not found the defaultValue is returned.
     * if the value is not a String <code> String.valueOf(x)</code> is returned.
     * 
     * @param key the attribute key
     * @param defaultValue  the default value
     * @return the attribute as a String
     */
    public String getAttributeAsString(final String key, final String defaultValue) {
        final Object x = getAttribute(key);
        if ( x == null ) return defaultValue;
        if ( x instanceof String ) return (String)x;
        return String.valueOf(x);
    }

    /**
     * return an attribute as an integer.
     * if given key is not found the defaultValue is returned.
     * if the value is a String, the value of <code>Integer.parseInt((String)x)</code> is returned.
     * If the value is not a String or an Integer, an exception is thrown
     * 
     * @param key the attribute key
     * @param defaultValue the default value
     *
     * @return the attribute as an integer
     */
    public int getAttributeAsInt(final String key, final int defaultValue) {
        final Object x = getAttribute(key);
        if ( x == null || x == VCFConstants.MISSING_VALUE_v4 ) return defaultValue;
        if ( x instanceof Integer ) return (Integer)x;
        if ( !( x instanceof String ) ) throw new IllegalArgumentException(
                "Cannot get attribute(key=\"" + key + ") as an integer because it's not a Number or a String. Class = " + x.getClass());
        try {
            return Integer.parseInt((String)x);
        } catch (final NumberFormatException err) {
            throw new IllegalArgumentException( "Cannot convert attribute(key=\"" + key + ") = "+ x + " as an integer.", err);
        }
    }
    
    /**
     * return an attribute as a double.
     * if given key is not found the defaultValue is returned.
     * if the value is as an Integer, this value is returned.
     * if the value is a String, the value of <code>Double.parseDouble((String)x)</code> is returned.
     * If the value is not a Double, a String or an Integer, an exception is thrown
     * 
     * @param key the attribute key
     * @param defaultValue the default value
     * @return the attribute as a double
     */
    public double getAttributeAsDouble(final String key, final double defaultValue) {
        final Object x = getAttribute(key);
        if ( x == null ) return defaultValue;
        if ( x instanceof Double ) return (Double)x;
        if ( x instanceof Integer ) return (Integer)x;
        if ( !( x instanceof String ) ) throw new IllegalArgumentException(
                "Cannot get attribute(key=\"" + key + ") as a double because it's not a Double, a Integer or a String. Class = " + x.getClass());
        try {
            return Double.parseDouble((String)x);
        } catch (final NumberFormatException err) {
            throw new IllegalArgumentException( "Cannot convert attribute(key=\"" + key + ") = "+ x + " as a double.", err);
        }
    }

    /**
     * return an attribute as a boolean.
     * if given key is not found the defaultValue is returned.
     * if the value is a String, the value of <code>Boolean.parseBoolean((String)x)</code> is returned.
     * If the value is not a Boolean or a String an exception is thrown
     * 
     * @param key the attribute key
     * @param defaultValue the default value
     * @return the attribute as a boolean
     */
    public boolean getAttributeAsBoolean(final String key, final boolean defaultValue) {
        final Object x = getAttribute(key);
        if ( x == null ) return defaultValue;
        if ( x instanceof Boolean ) return (Boolean)x;
        if ( !( x instanceof String ) ) throw new IllegalArgumentException(
                "Cannot get attribute(key=\"" + key + ") as a boolean because it's not a Boolean or a String. Class = " + x.getClass());
        return Boolean.parseBoolean((String)x);
    }

    @Override
    public String toString() {
        return new StringBuilder().
            append("CommonInfo [log10PError=").
            append(log10PError).
            append(", name=").
            append(name).
            append(", filters=").
            append(filters).
            append(", attributes=").
            append(attributes).
            append("]").
            toString();
    }

//    public String getAttributeAsString(String key)      { return (String.valueOf(getExtendedAttribute(key))); } // **NOTE**: will turn a null Object into the String "null"
//    public int getAttributeAsInt(String key)            { Object x = getExtendedAttribute(key); return x instanceof Integer ? (Integer)x : Integer.valueOf((String)x); }
//    public double getAttributeAsDouble(String key)      { Object x = getExtendedAttribute(key); return x instanceof Double ? (Double)x : Double.valueOf((String)x); }
//    public boolean getAttributeAsBoolean(String key)      { Object x = getExtendedAttribute(key); return x instanceof Boolean ? (Boolean)x : Boolean.valueOf((String)x); }
//    public Integer getAttributeAsIntegerNoException(String key)  { try {return getAttributeAsInt(key);} catch (Exception e) {return null;} }
//    public Double getAttributeAsDoubleNoException(String key)    { try {return getAttributeAsDouble(key);} catch (Exception e) {return null;} }
//    public String getAttributeAsStringNoException(String key)    { if (getExtendedAttribute(key) == null) return null; return getAttributeAsString(key); }
//    public Boolean getAttributeAsBooleanNoException(String key)  { try {return getAttributeAsBoolean(key);} catch (Exception e) {return null;} }
}
