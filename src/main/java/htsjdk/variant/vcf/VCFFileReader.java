/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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

package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFFileReader;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified interface for reading from VCF/BCF files.
 */
public class VCFFileReader implements VCFReader {

    private final FeatureReader<VariantContext> reader;

    /**
     * Returns true if the given path appears to be a BCF file.
     */
    public static boolean isBCF(final Path path) {
        return path.toUri().getRawPath().endsWith(FileExtensions.BCF);
    }

    /**
     * returns Correct Feature codec for Path depending whether
     * the name seems to indicate that it's a BCF.
     *
     * @param path to vcf/bcf
     * @return FeatureCodec for input Path
     */
    private static FeatureCodec<VariantContext, ?> getCodecForPath(Path path) {
        return isBCF(path) ? new BCF2Codec() : new VCFCodec();
    }

    /**
     * Tries to open a BCF file through {@link BCFFileReader}, which handles BGZF BCF with or without a CSI index.
     * Returns null if the file is not BGZF-compressed (raw BCF stays on the Tribble path).  A BGZF BCF with a CSI
     * is queryable; one without a CSI iterates sequentially and {@code isQueryable()} returns false.
     */
    private static FeatureReader<VariantContext> openBcf(
            final Path path, final Path explicitIndex, final boolean requireIndex) {
        // Check whether the file is BGZF by reading its first bytes
        final boolean isBgzf;
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(path))) {
            isBgzf = BlockCompressedInputStream.isValidFile(raw);
        } catch (final IOException e) {
            return null;
        }
        if (!isBgzf) {
            return null;
        }

        // Look for a CSI index
        final Path csiPath;
        if (explicitIndex != null) {
            csiPath = explicitIndex;
        } else {
            csiPath = findCsiIndex(path);
        }

        if (csiPath == null && requireIndex) {
            throw new TribbleException(
                    "A BGZF-compressed BCF requires a CSI index for region queries but none was found."
                            + " Expected " + path.getFileName() + FileExtensions.CSI + " or "
                            + stripExtension(path.getFileName().toString()) + FileExtensions.CSI
                            + " beside " + path);
        }

        // Open with BCFFileReader for either indexed or sequential access
        return new BCFFileReader(path, csiPath);
    }

    /** Finds a CSI index beside a BCF file: tries x.bcf.csi first, then x.csi. */
    private static Path findCsiIndex(final Path bcfPath) {
        final Path primary = bcfPath.resolveSibling(bcfPath.getFileName() + FileExtensions.CSI);
        if (Files.exists(primary)) {
            return primary;
        }
        final String name = bcfPath.getFileName().toString();
        final String base = stripExtension(name);
        final Path secondary = bcfPath.resolveSibling(base + FileExtensions.CSI);
        if (Files.exists(secondary)) {
            return secondary;
        }
        return null;
    }

    private static String stripExtension(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Returns the SAMSequenceDictionary from the provided VCF path.
     */
    public static SAMSequenceDictionary getSequenceDictionary(final Path path) {
        try (final VCFFileReader r = new VCFFileReader(path, false)) {
            return r.getFileHeader().getSequenceDictionary();
        }
    }

    /**
     * Constructs a VCFFileReader that requires the index to be present.
     */
    public VCFFileReader(final Path path) {
        this(path, true);
    }

    /**
     * Constructs a VCFFileReader with a specified index.
     */
    public VCFFileReader(final Path path, final Path indexPath) {
        this(path, indexPath, true);
    }

    /**
     * Allows construction of a VCFFileReader that will or will not assert the presence of an index as desired.
     */
    public VCFFileReader(final Path path, final boolean requireIndex) {
        if (isBCF(path)) {
            final FeatureReader<VariantContext> bcfReader = openBcf(path, null, requireIndex);
            if (bcfReader != null) {
                this.reader = bcfReader;
                return;
            }
        }
        this.reader =
                AbstractFeatureReader.getFeatureReader(path.toUri().toString(), getCodecForPath(path), requireIndex);
    }

    /**
     * Allows construction of a VCFFileReader with a specified index path.
     */
    public VCFFileReader(final Path path, final Path indexPath, final boolean requireIndex) {
        if (isBCF(path)) {
            final FeatureReader<VariantContext> bcfReader = openBcf(path, indexPath, requireIndex);
            if (bcfReader != null) {
                this.reader = bcfReader;
                return;
            }
        }
        this.reader = AbstractFeatureReader.getFeatureReader(
                path.toUri().toString(), indexPath.toUri().toString(), getCodecForPath(path), requireIndex);
    }

    /**
     * Parse a VCF file and convert to an IntervalList The name field of the IntervalList is taken from the ID field of the variant, if it exists. if not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     *
     * @param path a VCF
     * @return
     */
    public static IntervalList toIntervalList(final Path path) {
        return toIntervalList(path, false);
    }

    public static IntervalList toIntervalList(final Path path, final boolean includeFiltered) {
        try (final VCFFileReader vcfReader = new VCFFileReader(path, false)) {
            return vcfReader.toIntervalList(includeFiltered);
        }
    }

    /**
     * Converts the underlying VCFFileReader to an IntervalList. The name field of the IntervalList is taken from the
     * ID field of the variant, if it exists. If not, creates a name of the format 'interval-n' where n is a running
     * number that increments on un-named intervals. Will use the "END" tag in the INFO field as the end of the interval
     * (if exists).
     *
     * @return an IntervalList constructed from input vcf
     */
    public IntervalList toIntervalList() {
        return toIntervalList(false);
    }

    public IntervalList toIntervalList(final boolean includeFiltered) {
        return toIntervalList(this, includeFiltered);
    }

    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals.
     * Will use a "END" tag in the INFO field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     *
     * @deprecated since July 2018 since use {@link #toIntervalList(VCFFileReader)} instead
     */
    @Deprecated
    public static IntervalList fromVcf(final VCFFileReader vcf) {
        return fromVcf(vcf, false);
    }

    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals.
     * Will use a "END" tag in the INFO field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     */
    public static IntervalList toIntervalList(final VCFFileReader vcf) {
        return toIntervalList(vcf, false);
    }

    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals.
     * Will use a "END" tag in the INFO field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     *
     * @deprecated since July 2018 since use {@link #toIntervalList(VCFFileReader, boolean)} instead
     */
    @Deprecated
    public static IntervalList fromVcf(final VCFFileReader vcf, final boolean includeFiltered) {
        return toIntervalList(vcf, includeFiltered);
    }

    /**
     * Converts a {@link VCFFileReader} to an IntervalList. The name field of the Interval is taken from the ID field
     * of the variant, if it exists. If not, creates a name of the format interval-n where n is a running number that increments
     * only on un-named intervals. Will use a "END" tag in the INFO field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     */
    public static IntervalList toIntervalList(final VCFFileReader vcf, final boolean includeFiltered) {
        final IntervalList intervalList = new IntervalList(vcf.getFileHeader().getSequenceDictionary());
        toIntervals(vcf, includeFiltered).forEachRemaining(intervalList::add);
        return intervalList;
    }

    /**
     * Converts a {@link VCFFileReader} to an {@link Iterator<Interval>}
     * The name field of the Interval is taken from the ID field
     * of the variant, if it exists. If not, creates a name of the format interval-n where n is a running number that increments
     * only on un-named intervals. Will use a "END" tag in the INFO field as the end of the interval (if exists).
     *
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return a Iterator<Interval> constructed from input vcf
     */
    public static Iterator<Interval> toIntervals(final VCFFileReader vcf, final boolean includeFiltered) {

        // intervalCount is used and incremented inside the lambda function, so it needs to be a final mutable object.
        final AtomicInteger intervalCount = new AtomicInteger(0);

        return vcf.iterator().stream()
                .filter(vc -> includeFiltered || !vc.isFiltered())
                .map(vc -> {
                    String name = vc.getID();
                    final int intervalEnd = vc.getCommonInfo().getAttributeAsInt(VCFConstants.END_KEY, vc.getEnd());
                    if (VCFConstants.EMPTY_ID_FIELD.equals(name) || name == null) {
                        name = "interval-" + intervalCount.incrementAndGet();
                        ;
                    }
                    return new Interval(vc.getContig(), vc.getStart(), intervalEnd, false, name);
                })
                .iterator();
    }

    /**
     * Returns the VCFHeader associated with this VCF/BCF file.
     */
    @Override
    public VCFHeader getHeader() {
        return (VCFHeader) reader.getHeader();
    }

    /**
     * Synonym of {@link #getHeader()}
     */
    public final VCFHeader getFileHeader() {
        return getHeader();
    }

    /**
     * Returns an iterator over all records in this VCF/BCF file.
     */
    @Override
    public CloseableIterator<VariantContext> iterator() {
        try {
            return reader.iterator();
        } catch (final IOException ioe) {
            throw new TribbleException("Could not create an iterator from a feature reader.", ioe);
        }
    }

    /**
     * Queries for records overlapping the region specified.
     * Note that this method requires VCF files with an associated index.  If no index exists a TribbleException will be thrown.
     *
     * @param chrom the chomosome to query
     * @param start query interval start
     * @param end   query interval end
     * @return non-null iterator over VariantContexts
     */
    public CloseableIterator<VariantContext> query(final String chrom, final int start, final int end) {
        try {
            return reader.query(chrom, start, end);
        } catch (final IOException ioe) {
            throw new TribbleException("Could not create an iterator from a feature reader.", ioe);
        }
    }

    @Override
    public void close() {
        try {
            this.reader.close();
        } catch (final IOException ioe) {
            throw new TribbleException("Could not close a variant context feature reader.", ioe);
        }
    }

    /**
     * A method to check if the reader is query-able, i.e. if a call to {@link VCFFileReader#query(String, int, int)}
     * can be successful
     *
     * @return true if the reader can be queried, i.e. if the underlying Tribble reader is queryable.
     */
    @Override
    public boolean isQueryable() {
        return reader.isQueryable();
    }
}
