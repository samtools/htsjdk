/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htsjdk.samtools.util;

import htsjdk.samtools.SAMException;

import java.io.File;
import java.math.RoundingMode;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple class used to format object values into a standard format for printing.
 *
 * @author Tim Fennell
 */
public class FormatUtil {
    public static final int DECIMAL_DIGITS_TO_PRINT = 6;
    private final DateFormat dateFormat;
    private final NumberFormat integerFormat;
    private final NumberFormat floatFormat;

    /** Constructs a new FormatUtil and initializes various internal formatters. 
    * This is necessary because SimpleDateFormat and other formatters are not threadsafe.
    */
    public FormatUtil() {
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        this.integerFormat = NumberFormat.getIntegerInstance();
        this.integerFormat.setGroupingUsed(false);

        this.floatFormat = NumberFormat.getNumberInstance();
        this.floatFormat.setGroupingUsed(false);
        this.floatFormat.setMaximumFractionDigits(DECIMAL_DIGITS_TO_PRINT);
        this.floatFormat.setRoundingMode(RoundingMode.HALF_DOWN);
        if (this.floatFormat instanceof DecimalFormat) {
            final DecimalFormat decimalFormat = (DecimalFormat)this.floatFormat;
            final DecimalFormatSymbols decimalFormatSymbols = decimalFormat.getDecimalFormatSymbols();
            decimalFormatSymbols.setNaN("?");
            decimalFormatSymbols.setInfinity("?");
            decimalFormat.setDecimalFormatSymbols(decimalFormatSymbols);
        }
    }

    /** Formats a short to an integer string. */
    public String format(short value) { return this.integerFormat.format(value); }

    /** Formats an int to an integer string. */
    public String format(int value) { return this.integerFormat.format(value); }

    /** Formats a long to an integer string. */
    public String format(long value) { return this.integerFormat.format(value); }

    /** Formats a float to a floating point string. */
    public String format(float value) {return this.floatFormat.format(value); }

    /** Formats a double to a floating point string. */
    public String format(double value) {return this.floatFormat.format(value); }

    /** Formats a char as a string. */
    public String format(char value) { return Character.toString(value); }

    /** Formats an enum to the String representation of an enum. */
    public String format(Enum value) { return value.name(); }

    /** Formats a date to a date string without time. */
    public String format(Date value) { return this.dateFormat.format(value); }

    /** Formats date & time */
    public String format(final Iso8601Date value) { return value.toString(); }

    /** Formats a boolean value to a String. */
    public String format(boolean value) { if (value) return "Y"; else return "N"; }

    /** Attempts to determine the type of value and format it appropriately. */
    public String format(Object value) {
        if (value == null) return "";
        if (value instanceof Short)       return format( ((Short) value).shortValue() );
        if (value instanceof Integer)     return format( ((Integer) value).intValue() );
        if (value instanceof Long)        return format( ((Long) value).longValue() );
        if (value instanceof Float)       return format( ((Float) value).floatValue() );
        if (value instanceof Double)      return format( ((Double) value).doubleValue() );
        if (value instanceof Enum)        return format( ((Enum) value) );
        if (value instanceof Iso8601Date) return format((Iso8601Date)value);
        if (value instanceof Date)        return format( ((Date) value) );
        if (value instanceof Boolean)     return format( ((Boolean) value).booleanValue() );
        if (value instanceof Character)   return format( ((Character)value).charValue() );
        return value.toString();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parsing methods
    ///////////////////////////////////////////////////////////////////////////

    /** Parses a String into a short. */
    public short parseShort(String value) { return Short.parseShort(value); }

    /** Parses a String into an int. */
    public int parseInt(String value) { return Integer.parseInt(value); }

    /** Parses a String into a long. */
    public long parseLong(String value) { return Long.parseLong(value); }

    /** Parses a String into a float. */
    public float parseFloat(String value) {
        if ("?".equals(value) || "-?".equals(value)) return Float.NaN;
        else return Float.parseFloat(value);
    }

    /** Parses a String into a double. */
    public double parseDouble(String value) {
        if ("?".equals(value) || "-?".equals(value)) return Double.NaN;        
        else return Double.parseDouble(value);
    }

    /** Parses a String into an Enum of the given type. */
    public <E extends Enum> E parseEnum(String value, Class<E> type) { return (E) Enum.valueOf(type, value); }

    /** Parses a String into a date. */
    public Date parseDate(String value) {
        try {
            return this.dateFormat.parse(value);
        }
        catch (ParseException pe) {
            throw new SAMException("Could not parse value as date: " + value, pe);
        }
    }

    /** Parse a String into an Iso8601 Date */
    public Iso8601Date parseIso8601Date(String value) { return new Iso8601Date(value); }

    /** Parses a String into a boolean, as per the above convention that true = Y and false = N. */
    public boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) return false;
        char ch = Character.toUpperCase(value.charAt(0));
        return (ch == 'Y');
    }

    /** Parses a String into a char. We expect the String to have a length of exactly one, otherwise throw an exception. */
    public char parseChar(String value) {
        if (value == null) {
            throw new SAMException("Cannot parse null string into char");
        } else if (value.length() != 1) {
            throw new SAMException("Cannot parse string into char because length != 1 : " + value);
        } else {
            return value.charAt(0);
        }
    }

    /**
     * Attempts to determine the correct parse method to call based on the desired
     * return type and then parses the String and returns the value.
     *
     * @param value the String value to be parsed
     * @param returnType the desired return type
     * @return an object of the returnType
     */
    public Object parseObject(String value, Class<?> returnType) {
        if (returnType == Short.class     || returnType == Short.TYPE)     return parseShort(value);
        if (returnType == Integer.class   || returnType == Integer.TYPE)   return parseInt(value);
        if (returnType == Long.class      || returnType == Long.TYPE)      return parseLong(value);
        if (returnType == Float.class     || returnType == Float.TYPE)     return parseFloat(value);
        if (returnType == Double.class    || returnType == Double.TYPE)    return parseDouble(value);
        if (returnType == Boolean.class   || returnType == Boolean.TYPE)   return parseBoolean(value);
        if (returnType == Byte.class      || returnType == Byte.TYPE)      return parseInt(value);
        if (returnType == Character.class || returnType == Character.TYPE) return parseChar(value);
        if (returnType == Iso8601Date.class)                               return parseIso8601Date(value);
        if (returnType == Date.class)                                      return parseDate(value);
        if (returnType == File.class)                                      return new File(value);
        if (Enum.class.isAssignableFrom(returnType)) return parseEnum(value, (Class<? extends Enum>)returnType);
        if (returnType == String.class) return value;

        throw new InvalidParameterException("Don't know how to convert a String to a " + returnType.getName());
    }
}
