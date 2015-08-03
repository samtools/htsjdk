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

import htsjdk.samtools.util.Histogram.Bin;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import static java.lang.Math.*;

/**
 * Class for computing and accessing histogram type data.  Stored internally in
 * a sorted Map so that keys can be iterated in order.
 *
 * @author Tim Fennell
 */
public class Histogram<K extends Comparable> extends TreeMap<K, Bin> {
    private String binLabel   = "BIN";
    private String valueLabel = "VALUE";
    private Double mean;

    /** Constructs a new Histogram with default bin and value labels. */
    public Histogram() { }

    /** Constructs a new Histogram with supplied bin and value labels. */
    public Histogram(final String binLabel, final String valueLabel) {
        this.binLabel = binLabel;
        this.valueLabel = valueLabel;
    }

    /** Constructs a new Histogram that'll use the supplied comparator to sort keys. */
    public Histogram(final Comparator<K> comparator) {
        super(comparator);
    }

    /** Constructor that takes labels for the bin and values and a comparator to sort the bins. */
    public Histogram(final String binLabel, final String valueLabel, final Comparator<K> comparator) {
        this(comparator);
        this.binLabel = binLabel;
        this.valueLabel = valueLabel;
    }

    /** Copy constructor for a histogram. */
    public Histogram(final Histogram<K> in) {
        super(in);
        this.binLabel = in.binLabel;
        this.valueLabel = in.valueLabel;
        this.mean = in.mean;
    }

    /** Represents a bin in the Histogram. */
    public class Bin implements Serializable{
        private final K id;
        private double value = 0;

        /** Constructs a new bin with the given ID. */
        private Bin(final K id) { this.id = id; }

        /** Gets the ID of this bin. */
        public K getId() { return id; }

        /** Gets the value in the bin. */
        public double getValue() { return value; }

        /** Returns the String format for the value in the bin. */
        public String toString() { return String.valueOf(this.value); }

        /** Checks the equality of the bin by ID and value. */
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Bin bin = (Bin) o;

            if (Double.compare(bin.value, value) != 0) return false;
            if (!id.equals(bin.id)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result;
            final long temp;
            result = id.hashCode();
            temp = value != +0.0d ? Double.doubleToLongBits(value) : 0L;
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        public double getIdValue() {
            if (id instanceof Number) {
                return ((Number) id).doubleValue();
            } else {
                throw new UnsupportedOperationException("getIdValue only supported for Histogram<? extends Number>");
            }
        }
    }

    /** Prefill the histogram with the supplied set of bins. */
    public void prefillBins(final K... ids) {
        for (final K id : ids) {
            put(id, new Bin(id));
        }
    }

    /** Increments the value in the designated bin by 1. */
    public void increment(final K id) {
        increment(id, 1d);
    }

    /** Increments the value in the designated bin by the supplied increment. */
    public void increment(final K id, final double increment) {
        Bin bin = get(id);
        if (bin == null) {
            bin = new Bin(id);
            put(id, bin);
        }

        bin.value += increment;
        mean = null;
    }

    public String getBinLabel() { return binLabel; }
    public void setBinLabel(final String binLabel) { this.binLabel = binLabel; }

    public String getValueLabel() { return valueLabel; }
    public void setValueLabel(final String valueLabel) { this.valueLabel = valueLabel; }

    /** Checks that the labels and values in the two histograms are identical. */
    public boolean equals(final Object o) {
        return o != null &&
                (o instanceof Histogram) &&
                ((Histogram) o).binLabel.equals(this.binLabel) &&
                ((Histogram) o).valueLabel.equals(this.valueLabel) &&
                super.equals(o);
    }

    public double getMean() {
        if (mean == null) {
            mean = getSum() / getCount();
        }

        return mean;
    }

    /**
     * Returns the sum of the products of the histgram bin ids and the number of entries in each bin.
     */
    public double getSum() {
        double total = 0;
        for (final Bin bin : values()) {
            total += bin.getValue() * bin.getIdValue();
        }

        return total;
    }

    /**
     * Returns the sum of the number of entries in each bin.
     */
    public double getSumOfValues() {
        double total = 0;
        for (final Bin bin : values()) {
            total += bin.getValue();
        }

        return total;
    }

    public double getStandardDeviation() {
        final double mean = getMean();

        double count = 0;
        double total = 0;

        for (final Bin bin : values()) {
            final double localCount = bin.getValue();
            final double value = bin.getIdValue();

            count += localCount;
            total += localCount * pow(value - mean, 2);
        }

        return Math.sqrt(total / (count-1));
    }

    /**
     * Calculates the mean bin size
     */
    public double getMeanBinSize() {
        return (getSumOfValues() / size());
    }

	/**
	 * Calculates the median bin size
	 */
	public double getMedianBinSize() {
		if (size() == 0) {
			return 0;
		}

		final List<Double> binValues = new ArrayList<Double>();
		for (final Bin bin : values()) {
			binValues.add(bin.getValue());
		}
		Collections.sort(binValues);

		final int midPoint = binValues.size() / 2;
		double median = binValues.get(midPoint);
		if (binValues.size() % 2 == 0) {
			median = (median + binValues.get(midPoint-1)) / 2;
		}

		return median;
	}

    /**
     * Calculates the standard deviation of the bin size
     */
    public double getStandardDeviationBinSize(final double mean) {
        double total = 0;
        for(final Bin bin : values()) {
            total += Math.pow(bin.getValue() - mean, 2);
        }
        return Math.sqrt(total / (Math.max(1,values().size()-1)));
    }

    /**
     * Gets the bin in which the given percentile falls.
     *
     * @param percentile a value between 0 and 1
     * @return the bin value in which the percentile falls
     */
    public double getPercentile(double percentile) {
        if (percentile <= 0) throw new IllegalArgumentException("Cannot query percentiles of 0 or below");
        if (percentile >= 1) throw new IllegalArgumentException("Cannot query percentiles of 1 or above");

        double total = getCount();
        double sofar = 0;
        for (Bin bin : values()) {
            sofar += bin.getValue();
            if (sofar / total >= percentile) return bin.getIdValue();
        }

        throw new IllegalStateException("Could not find percentile: " + percentile);
    }

    /**
     * Returns the cumulative probability of observing a value <= v when sampling the
     * distribution represented by this histogram.
     */
    public double getCumulativeProbability(final double v) {
        double count = 0;
        double total = 0;

        for (final Bin bin : values()) {
            final double binValue = bin.getIdValue();
            if (binValue <= v) count += bin.getValue();
            total += bin.getValue();
        }

        return count / total;
    }

    public double getMedian() {
        double total = 0;
        double count = getCount();

        // Base cases
        if (count == 0) return 0;
        if (count == 1) return values().iterator().next().getIdValue();

        final double midLow, midHigh;
        if (count % 2 == 0) {
            midLow = count / 2;
            midHigh = midLow + 1;
        }
        else {
            midLow = Math.ceil(count / 2);
            midHigh = midLow;
        }

        Double midLowValue  = null;
        Double midHighValue = null;
        for (final Bin bin : values()) {
            total += bin.getValue();
            if (midLowValue  == null && total >= midLow)  midLowValue  = bin.getIdValue();
            if (midHighValue == null && total >= midHigh) midHighValue = bin.getIdValue();
            if (midLowValue != null && midHighValue != null) break;
        }

        return (midLowValue + midHighValue) / 2;
    }

    /** Gets the median absolute deviation of the distribution. */
    public double getMedianAbsoluteDeviation() {
        final double median = getMedian();
        final Histogram<Double> deviations = new Histogram<Double>();
        for (final Bin bin : values()) {
            final double dev = abs(bin.getIdValue() - median);
            deviations.increment(dev, bin.getValue());
        }

        return deviations.getMedian();
    }

    /**
     * Returns a value that is intended to estimate the mean of the distribution, if the distribution is
     * essentially normal, by using the median absolute deviation to remove the effect of
     * erroneous massive outliers.
     */
    public double estimateSdViaMad() {
        return 1.4826 * getMedianAbsoluteDeviation();
    }

    /** Returns id of the Bin that's the mode of the distribution (i.e. the largest bin). */
    public double getMode() {

        return getModeBin().getIdValue();
    }

    /** Returns the Bin that's the mode of the distribution (i.e. the largest bin). */
    private Bin getModeBin() {
        Bin modeBin = null;

        for (final Bin bin : values()) {
            if (modeBin == null || modeBin.value < bin.value) {
                modeBin = bin;
            }
        }

        return modeBin;
    }


    public double getMin() {
        return firstEntry().getValue().getIdValue();
    }

    public double getMax() {
        return lastEntry().getValue().getIdValue();
    }

    public double getCount() {
        double count = 0;
        for (final Bin bin : values()) {
            count += bin.value;
        }

        return count;
    }

    /** Gets the geometric mean of the distribution. */
    public double getGeometricMean() {
        double total = 0;
        double count = 0;
        for (final Bin bin : values()) {
            total += bin.value * log(bin.getIdValue());
            count += bin.value;
        }

        return exp(total / count);
    }

    /**
     * Trims the histogram when the bins in the tail of the distribution contain fewer than mode/tailLimit items
     */
    public void trimByTailLimit(final int tailLimit) {
        if (isEmpty()) {
            return;
        }

        final Bin modeBin = getModeBin();
        final double mode = modeBin.getIdValue();
        final double sizeOfModeBin = modeBin.getValue();
        final double minimumBinSize = sizeOfModeBin/tailLimit;
        Histogram<K>.Bin lastBin = null;

        final List<K> binsToKeep = new ArrayList<K>();
        for (Histogram<K>.Bin bin : values()) {
            double binId = ((Number)bin.getId()).doubleValue();

            if (binId <= mode) {
                binsToKeep.add(bin.getId());
            }
            else if ((lastBin != null && ((Number)lastBin.getId()).doubleValue() != binId - 1) || bin.getValue() < minimumBinSize) {
                break;
            }
            else {
                binsToKeep.add(bin.getId());
            }
            lastBin = bin;
        }

        final Object keys[] = keySet().toArray();
        for (Object binId : keys) {
            if (!binsToKeep.contains((K)binId)) {
                remove(binId);
            }
        }
    }

    /**
     * Trims the histogram so that only bins <= width are kept.
     */
    public void trimByWidth(final int width) {
        final Iterator<K> it = descendingKeySet().iterator();
        while (it.hasNext()) {

            if (((Number)it.next()).doubleValue() > width) {
                it.remove();
            } else break;
        }
    }

    /***
     * Immutable method that divides the current Histogram by an input Histogram and generates a new one
     * Throws an exception if the bins don't match up exactly
     * @param divisorHistogram
     * @return
     * @throws IllegalArgumentException
     */
    public Histogram<K> divideByHistogram(final Histogram<K> divisorHistogram) throws IllegalArgumentException{
        Histogram<K> output = new Histogram<K>();
        if (!this.keySet().equals(divisorHistogram.keySet()))  throw new IllegalArgumentException("Attempting to divide Histograms with non-identical bins");
        for (final K key : this.keySet()){
            Bin dividend = this.get(key);
            Bin divisor = divisorHistogram.get(key);
            output.increment(key, dividend.getValue()/divisor.getValue());
        }
        return output;
    }

    /***
     * Mutable method that allows the addition of a Histogram into the current one.
     * @param addHistogram
     */
    public void addHistogram(final Histogram<K> addHistogram) {
        for (final K key : addHistogram.keySet()){
            this.increment(key, addHistogram.get(key).getValue());
        }
    }
}
