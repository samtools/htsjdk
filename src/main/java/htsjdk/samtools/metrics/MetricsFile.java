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

package htsjdk.samtools.metrics;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FormatUtil;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.StringUtil;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Contains a set of metrics that can be written to a file and parsed back
 * again. The set of metrics is composed of zero or more instances of a class,
 * BEAN, that extends {@link MetricBase} (all instances must be of the same type)
 * and may optionally include one or more histograms that share the same key set.
 *
 * @author Tim Fennell
 */
public class MetricsFile<BEAN extends MetricBase, HKEY extends Comparable> implements Serializable {
    public static final String MAJOR_HEADER_PREFIX = "## ";
    public static final String MINOR_HEADER_PREFIX = "# ";
    public static final String SEPARATOR = "\t";
    public static final String HISTO_HEADER = "## HISTOGRAM\t";
    public static final String METRIC_HEADER = "## METRICS CLASS\t";

    private final Set<String> columnLabels = new HashSet<String>();
    private final List<Header> headers = new ArrayList<Header>();
    private final List<BEAN> metrics = new ArrayList<BEAN>();
    private final List<Histogram<HKEY>> histograms = new ArrayList<Histogram<HKEY>>();

    /** Adds a header to the collection of metrics. */
    public void addHeader(Header h) { this.headers.add(h); }

    /** Returns the list of headers. */
    public List<Header> getHeaders() { return Collections.unmodifiableList(this.headers); }

    /** Adds a bean to the collection of metrics. */
    public void addMetric(final BEAN bean) { this.metrics.add(bean); }

    /** Add multiple metric beans at once. */
    public void addAllMetrics(final Iterable<BEAN> beanz) {
        for (final BEAN bean : beanz) { this.addMetric(bean); }
    }

    /** Returns the list of headers. */
    public List<BEAN> getMetrics() { return Collections.unmodifiableList(this.metrics); }

    public Set<String> getMetricsColumnLabels() { return Collections.unmodifiableSet(this.columnLabels); }

    /** Returns the histogram contained in the metrics file if any. */
    public Histogram<HKEY> getHistogram() {
        if (!histograms.isEmpty()) return this.histograms.get(0);
        else return null;
    }

    /** Sets the histogram contained in the metrics file. */
    public void setHistogram(final Histogram<HKEY> histogram) {
        if (this.histograms.isEmpty()) {
            if (histogram != null) this.histograms.add(histogram);
        }
        else {
            this.histograms.set(0, histogram);
        }
    }

    /** Adds a histogram to the list of histograms in the metrics file. */
    public void addHistogram(final Histogram<HKEY> histogram) {
        this.histograms.add(histogram);
    }

    //** Returns an unmodifiable version of the histogram list */
    public List<Histogram<HKEY>> getAllHistograms() {
        return Collections.unmodifiableList(histograms);
    }

    /** Returns the number of histograms added to the metrics file. */
    public int getNumHistograms() 
    {
    	return this.histograms.size();
    }
    
    /** Returns the list of headers with the specified type. */
    public List<Header> getHeaders(final Class<? extends Header> type) {
        List<Header> tmp = new ArrayList<Header>();
        for (final Header h : this.headers) {
            if (h.getClass().equals(type)) {
                tmp.add(h);
            }
        }

        return tmp;
    }

    /**
     * Writes out the metrics file to the supplied file. The file is written out
     * headers first, metrics second and histogram third.
     *
     * @param f a File into which to write the metrics
     */
    public void write(final File f) {
        FileWriter w = null;
        try {
            w = new FileWriter(f);
            write(w);
        }
        catch (IOException ioe) {
            throw new SAMException("Could not write metrics to file: " + f.getAbsolutePath(), ioe);
        }
        finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Writes out the metrics file to the supplied writer. The file is written out
     * headers first, metrics second and histogram third.
     *
     * @param w a Writer into which to write the metrics
     */
    public void write(final Writer w) {
        try {
            final FormatUtil formatter = new FormatUtil();
            final BufferedWriter out = new BufferedWriter(w);
            printHeaders(out);
            out.newLine();

            printBeanMetrics(out, formatter);
            out.newLine();

            printHistogram(out, formatter);
            out.newLine();
            out.flush();
        }
        catch (IOException ioe) {
            throw new SAMException("Could not write metrics file.", ioe);
        }
    }

    /** Prints the headers into the provided PrintWriter. */
    private void printHeaders(final BufferedWriter out) throws IOException {
        for (final Header h : this.headers) {
            out.append(MAJOR_HEADER_PREFIX);
            out.append(h.getClass().getName());
            out.newLine();
            out.append(MINOR_HEADER_PREFIX);
            out.append(h.toString());
            out.newLine();
        }
    }

    /** Prints each of the metrics entries into the provided PrintWriter. */
    private void printBeanMetrics(final BufferedWriter out, final FormatUtil formatter) throws IOException {
        if (this.metrics.isEmpty()) {
            return;
        }

        // Write out a header row with the type of the metric class
        out.append(METRIC_HEADER + getBeanType().getName());
        out.newLine();

        // Write out the column headers
        final Field[] fields = getBeanType().getFields();
        final int fieldCount = fields.length;

        // Write out the column headers
        for (int i=0; i<fieldCount; ++i) {
            out.append(fields[i].getName());
            if (i < fieldCount - 1) {
                out.append(MetricsFile.SEPARATOR);
            }
            else {
                out.newLine();
            }
        }

        // Write out each of the data rows
        for (final BEAN bean : this.metrics) {
            for (int i=0; i<fieldCount; ++i) {
                try {
                    final Object value = fields[i].get(bean);
                    out.append(StringUtil.assertCharactersNotInString(formatter.format(value), '\t', '\n'));

                    if (i < fieldCount - 1) {
                        out.append(MetricsFile.SEPARATOR);
                    }
                    else {
                        out.newLine();
                    }
                }
                catch (IllegalAccessException iae) {
                    throw new SAMException("Could not read property " + fields[i].getName()
                            + " from class of type " + bean.getClass());
                }
            }
        }

        out.flush();
    }

    /** Prints the histogram if one is present. */
    private void printHistogram(final BufferedWriter out, final FormatUtil formatter) throws IOException {
        final List<Histogram<HKEY>> nonEmptyHistograms = new ArrayList<Histogram<HKEY>>();
        for (final Histogram<HKEY> histo : this.histograms) {
            if (!histo.isEmpty()) nonEmptyHistograms.add(histo);
        }

        if (nonEmptyHistograms.isEmpty()) {
            return;
        }

        // Build a combined key set.  Assume comparator is the same for all Histograms
        final java.util.Set<HKEY> keys = new TreeSet<HKEY>(nonEmptyHistograms.get(0).comparator());
        for (final Histogram<HKEY> histo : nonEmptyHistograms) {
            if (histo != null) keys.addAll(histo.keySet());
        }

        // Add a header for the histogram key type
        out.append(HISTO_HEADER + nonEmptyHistograms.get(0).keySet().iterator().next().getClass().getName());
        out.newLine();

        // Output a header row
        out.append(StringUtil.assertCharactersNotInString(nonEmptyHistograms.get(0).getBinLabel(), '\t', '\n'));
        for (final Histogram<HKEY> histo : nonEmptyHistograms) {
            out.append(SEPARATOR);
            out.append(StringUtil.assertCharactersNotInString(histo.getValueLabel(), '\t', '\n'));
        }
        out.newLine();

        for (final HKEY key : keys) {
            out.append(key.toString());

            for (final Histogram<HKEY> histo : nonEmptyHistograms) {
                final Histogram<HKEY>.Bin bin = histo.get(key);
                final double value = (bin == null ? 0 : bin.getValue());

                out.append(SEPARATOR);
                out.append(formatter.format(value));
            }

            out.newLine();
        }
    }

    /** Gets the type of the metrics bean being used. */
    private Class<?> getBeanType() {
        if (this.metrics.isEmpty()) {
            return null;
        } else {
            return this.metrics.get(0).getClass();
        }
    }

    /** Reads the Metrics in from the given reader. */
    public void read(final Reader r) {
        final BufferedReader in = new BufferedReader(r);
        final FormatUtil formatter = new FormatUtil();
        String line = null;

        try {
            // First read the headers
            Header header = null;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if ("".equals(line)) {
                    // Do nothing! Nothing to be done!
                }
                else if (line.startsWith(METRIC_HEADER) || line.startsWith(HISTO_HEADER)) {
                    // A line that starts with "## METRICS CLASS" heralds the start of the actual
                    // data. Bounce our butts out of header parsing without reading the next line.
                    // This isn't in the while loop's conditional because we want to trim() first.
                    break;
                }
                else if (line.startsWith(MAJOR_HEADER_PREFIX)) {
                    if (header != null) {
                        throw new IllegalStateException("Consecutive header class lines encountered.");
                    }
                    
                    final String className = line.substring(MAJOR_HEADER_PREFIX.length()).trim();
                    try {
                        header = (Header) loadClass(className, true).newInstance();
                    }
                    catch (final Exception e) {
                        throw new SAMException("Error load and/or instantiating an instance of " + className, e);
                    }
                }
                else if (line.startsWith(MINOR_HEADER_PREFIX)) {
                    if (header == null) {
                        throw new IllegalStateException("Header class must precede header value:" + line);
                    }
                    header.parse(line.substring(MINOR_HEADER_PREFIX.length()));
                    this.headers.add(header);
                    header = null;
                }
                else {
                    throw new SAMException("Illegal state. Found following string in metrics file header: " + line);
                }
            }

            // Read space between headers and metrics, if any
            while (line != null && ! line.trim().startsWith(MAJOR_HEADER_PREFIX)) {
                line = in.readLine();
            }


            if (line != null) {
                line = line.trim();
            
                // Then read the metrics if there are any
                if (line.startsWith(METRIC_HEADER)) {
                    // Get the metric class from the header
                    final String className = line.split(SEPARATOR)[1];
                    Class<?> type = null;
                    try {
                        type = loadClass(className, true);
                    }
                    catch (final ClassNotFoundException cnfe) {
                        throw new SAMException("Could not locate class with name " + className, cnfe);
                    }

                    // Read the next line with the column headers
                    final String[] fieldNames = in.readLine().split(SEPARATOR);
                    Collections.addAll(columnLabels, fieldNames);
                    final Field[] fields = new Field[fieldNames.length];
                    for (int i=0; i<fieldNames.length; ++i) {
                        try {
                            fields[i] = type.getField(fieldNames[i]);
                        }
                        catch (final Exception e) {
                            throw new SAMException("Could not get field with name " + fieldNames[i] +
                                " from class " + type.getName());
                        }
                    }

                    // Now read the values
                    while ((line = in.readLine()) != null) {
                        if ("".equals(line.trim())) {
                            break;
                        }
                        else {
                            final String[] values = line.split(SEPARATOR, -1);
                            BEAN bean = null;

                            try { bean = (BEAN) type.newInstance(); }
                            catch (final Exception e) { throw new SAMException("Error instantiating a " + type.getName(), e); }

                            for (int i=0; i<fields.length; ++i) {
                                Object value = null;
                                if (values[i] != null && !values[i].isEmpty()) {
                                    value = formatter.parseObject(values[i], fields[i].getType());
                                }

                                try { fields[i].set(bean, value); }
                                catch (final Exception e) {
                                    throw new SAMException("Error setting field " + fields[i].getName() +
                                            " on class of type " + type.getName(), e);
                                }
                            }

                            this.metrics.add(bean);
                        }
                    }
                }
            }

            // Read away any blank lines between metrics and histograms
            while (line != null && ! line.trim().startsWith(MAJOR_HEADER_PREFIX)) {
                line = in.readLine();
            }

            // Then read the histograms if any are present
            if (line != null) {
                line = line.trim();

                if (line.startsWith(HISTO_HEADER)) {
                    // Get the key type of the histogram
                    final String keyClassName = line.split(SEPARATOR)[1].trim();
                    Class<?> keyClass = null;

                    try { keyClass = loadClass(keyClassName, true); }
                    catch (final ClassNotFoundException cnfe) { throw new SAMException("Could not load class with name " + keyClassName); }

                    // Read the next line with the bin and value labels
                    final String[] labels = in.readLine().split(SEPARATOR);
                    for (int i=1; i<labels.length; ++i) {
                        this.histograms.add(new Histogram<HKEY>(labels[0], labels[i]));
                    }

                    // Read the entries in the histograms
                    while ((line = in.readLine()) != null && !"".equals(line)) {
                        final String[] fields = line.trim().split(SEPARATOR);
                        final HKEY key = (HKEY) formatter.parseObject(fields[0], keyClass);

                        for (int i=1; i<fields.length; ++i) {
                            final double value = formatter.parseDouble(fields[i]);
                            this.histograms.get(i-1).increment(key, value);
                        }
                    }
                }
            }
        }
        catch (final IOException ioe) {
            throw new SAMException("Could not read metrics from reader.", ioe);
        }
        finally{
            CloserUtil.close(in);
        }
    }

    /** Attempts to load a class, taking into account that some classes have "migrated" from the broad to sf. */
    private Class<?> loadClass(final String className, final boolean tryOtherPackages) throws ClassNotFoundException {
        // List of alternative packages to check in case classes moved around
        final String[] packages = new String[] {
                "edu.mit.broad.picard.genotype.concordance",
                "edu.mit.broad.picard.genotype.fingerprint",
                "edu.mit.broad.picard.ic",
                "edu.mit.broad.picard.illumina",
                "edu.mit.broad.picard.jumping",
                "edu.mit.broad.picard.quality",
                "edu.mit.broad.picard.samplevalidation",
                "htsjdk.samtools.analysis",
                "htsjdk.samtools.analysis.directed",
                "htsjdk.samtools.sam",
                "htsjdk.samtools.metrics",
                "picard.sam",
                "picard.metrics",
                "picard.illumina",
                "picard.analysis",
                "picard.analysis.directed",
                "picard.vcf"
        };

        try { return Class.forName(className); }
        catch (ClassNotFoundException cnfe) {
            if (tryOtherPackages) {
                for (final String p : packages) {
                    try {
                        return loadClass(p + className.substring(className.lastIndexOf('.')), false);
                    }
                    catch (ClassNotFoundException cnf2) {/* do nothing */}
                    // If it ws an inner class, try and see if it's a stand-alone class now
                    if (className.indexOf('$') > -1) {
                        try {
                            return loadClass(p + "." + className.substring(className.lastIndexOf('$') + 1), false);
                        }
                        catch (ClassNotFoundException cnf2) {/* do nothing */}
                    }
                }
            }

            throw cnfe;
        }
    }

    /** Checks that the headers, metrics and histogram are all equal. */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        final MetricsFile that = (MetricsFile) o;

        if (!areHeadersEqual(that)) {
            return false;
        }
        if (!areMetricsEqual(that)) {
            return false;
        }
        if (!areHistogramsEqual(that)) {
            return false;
        }

        return true;
    }

    public boolean areHeadersEqual(final MetricsFile that) {
        return this.headers.equals(that.headers);
    }

    public boolean areMetricsEqual(final MetricsFile that) {
        return this.metrics.equals(that.metrics);
    }

    public boolean areHistogramsEqual(final MetricsFile that) {
        return this.histograms.equals(that.histograms);
    }

    @Override
    public int hashCode() {
        int result = headers.hashCode();
        result = 31 * result + metrics.hashCode();
        return result;
    }

    /**
     * Convenience method to read all the Metric beans from a metrics file.
     * @param file to be read.
     * @return list of beans from the file.
     */
    public static <T extends MetricBase> List<T> readBeans(final File file) {
        final MetricsFile<T, Comparable<?>> metricsFile = new MetricsFile<T, Comparable<?>>();
        final Reader in = IOUtil.openFileForBufferedReading(file);
        metricsFile.read(in);
        CloserUtil.close(in);
        return metricsFile.getMetrics();
    }

    /**
     * Method to read the header from a metrics file.
     */
    public static List<Header> readHeaders(final File file) {
        try {
            final MetricsFile<MetricBase, Comparable<?>> metricsFile = new MetricsFile<MetricBase, Comparable<?>>();
            metricsFile.read(new FileReader(file));
            return metricsFile.getHeaders();
        } catch (FileNotFoundException e) {
            throw new SAMException(e.getMessage(), e);
        }
    }

    /**
     * Compare the metrics in two files, ignoring headers and histograms.
     */
    public static boolean areMetricsEqual(final File file1, final File file2) {
        try {
            final MetricsFile<MetricBase, Comparable<?>> mf1 = new MetricsFile<MetricBase, Comparable<?>>();
            final MetricsFile<MetricBase, Comparable<?>> mf2 = new MetricsFile<MetricBase, Comparable<?>>();
            mf1.read(new FileReader(file1));
            mf2.read(new FileReader(file2));
            return mf1.areMetricsEqual(mf2);
        } catch (FileNotFoundException e) {
            throw new SAMException(e.getMessage(), e);
        }

    }

    /**
     * Compare the metrics and histograms in two files, ignoring headers.
     */
    public static boolean areMetricsAndHistogramsEqual(final File file1, final File file2) {
        try {
            final MetricsFile<MetricBase, Comparable<?>> mf1 = new MetricsFile<MetricBase, Comparable<?>>();
            final MetricsFile<MetricBase, Comparable<?>> mf2 = new MetricsFile<MetricBase, Comparable<?>>();
            mf1.read(new FileReader(file1));
            mf2.read(new FileReader(file2));

            return mf1.areMetricsEqual(mf2) && mf1.areHistogramsEqual(mf2);

        } catch (FileNotFoundException e) {
            throw new SAMException(e.getMessage(), e);
        }
    }
}
