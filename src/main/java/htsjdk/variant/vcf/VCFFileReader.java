package htsjdk.variant.vcf;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.*;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.variantcontext.VariantContext;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Simplified interface for reading from VCF/BCF files.
 */
public class VCFFileReader implements Closeable, Iterable<VariantContext> {

    private final FeatureReader<VariantContext> reader;

    /**
     * Returns true if the given file appears to be a BCF file.
     *
     */
    public static boolean isBCF(final File file) {
        return isBCF(file.toPath());
    }

    /**
     * Returns true if the given path appears to be a BCF file.
     */
    public static boolean isBCF(final Path path) {
        return path.toUri().getRawPath().endsWith(IOUtil.BCF_FILE_EXTENSION);
    }

    /**
     * Returns the SAMSequenceDictionary from the provided VCF file.
     */
    public static SAMSequenceDictionary getSequenceDictionary(final File file) {
        try (final VCFFileReader vcfFileReader = new VCFFileReader(file, false)) {
            return vcfFileReader.getFileHeader().getSequenceDictionary();
        }
    }

    /**
     * Constructs a VCFFileReader that requires the index to be present.
     *
     */
    public VCFFileReader(final File file) {
        this(file, true);
    }

    /**
     * Constructs a VCFFileReader with a specified index.
     *
     */
    public VCFFileReader(final File file, final File indexFile) {
        this(file, indexFile, true);
    }

    /**
     * Allows construction of a VCFFileReader that will or will not assert the presence of an index as desired.
     *
     */
    public VCFFileReader(final File file, final boolean requireIndex) {
        // Note how we deal with type safety here, just casting to (FeatureCodec)
        // in the call to getFeatureReader is not enough for Java 8.
        this(file.toPath(), requireIndex);
    }

    /**
     * Allows construction of a VCFFileReader with a specified index file.
     *
     */
    public VCFFileReader(final File file, final File indexFile, final boolean requireIndex) {
        // Note how we deal with type safety here, just casting to (FeatureCodec)
        // in the call to getFeatureReader is not enough for Java 8.
        this(file.toPath(), indexFile.toPath(), requireIndex);
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
     * Returns the SAMSequenceDictionary from the provided VCF file.
     */
    public static SAMSequenceDictionary getSequenceDictionary(final Path path) {
        final SAMSequenceDictionary dict = new VCFFileReader(path, false).getFileHeader().getSequenceDictionary();
        return dict;
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
        this.reader = AbstractFeatureReader.getFeatureReader(
                path.toUri().toString(),
                getCodecForPath(path),
                requireIndex);
    }

    /**
     * Allows construction of a VCFFileReader with a specified index path.
     */
    public VCFFileReader(final Path path, final Path indexPath, final boolean requireIndex) {
        this.reader = AbstractFeatureReader.getFeatureReader(
                path.toUri().toString(),
                indexPath.toUri().toString(),
                getCodecForPath(path),
                requireIndex);
    }


    /**
     * Parse a VCF file and convert to an IntervalList The name field of the IntervalList is taken from the ID field of the variant, if it exists. if not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     *
     * @param path
     * @return
     */
    public static IntervalList toIntervalList(final Path path) {
        return toIntervalList(path, false);
    }

    public static IntervalList toIntervalList(final Path path, final boolean includeFiltered) {
        try(final VCFFileReader vcfReader = new VCFFileReader(path, false)) {
            return vcfReader.toIntervalList(includeFiltered);
        }
    }

    /**
     * Parse a VCF file and convert to an IntervalList The name field of the IntervalList is taken from the ID field of the variant, if it exists. if not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     *
     * @param file
     * @return
     *
     */
    public static IntervalList fromVcf(final File file) {
        return toIntervalList(file.toPath());
    }

    /**
     * Parse a VCF file and convert to an IntervalList The name field of the IntervalList is taken from the ID field of the variant, if it exists. if not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     *
     * @param file
     * @return
     *
     */
    public static IntervalList fromVcf(final File file, final boolean includeFiltered) {
        return toIntervalList(file.toPath(),includeFiltered);
    }

    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     * Will use a "END" tag in the info field as the end of the interval (if exists).
     *
     * @return an IntervalList constructed from input vcf
     *
     */
    public IntervalList toIntervalList() {
        return toIntervalList(false);
    }

    public IntervalList toIntervalList(final boolean includeFiltered) {

        //grab the dictionary from the VCF and use it in the IntervalList
        final SAMSequenceDictionary dict = getFileHeader().getSequenceDictionary();
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSequenceDictionary(dict);
        final IntervalList list = new IntervalList(samFileHeader);

        int intervals = 0;
        for (final VariantContext vc : this) {
            if (includeFiltered || !vc.isFiltered()) {
                String name = vc.getID();
                final Integer intervalEnd = vc.getCommonInfo().getAttributeAsInt("END", vc.getEnd());
                if (".".equals(name) || name == null)
                    name = "interval-" + (++intervals);
                list.add(new Interval(vc.getContig(), vc.getStart(), intervalEnd, false, name));
            }
        }

        return list;
    }


    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     * Will use a "END" tag in the info field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     *
     */
    public static IntervalList fromVcf(final VCFFileReader vcf) {
        return fromVcf(vcf, false);
    }


    /**
     * Converts a vcf to an IntervalList. The name field of the IntervalList is taken from the ID field of the variant, if it exists. If not,
     * creates a name of the format interval-n where n is a running number that increments only on un-named intervals
     * Will use a "END" tag in the info field as the end of the interval (if exists).
     *
     * @param vcf the vcfReader to be used for the conversion
     * @return an IntervalList constructed from input vcf
     *
     */
    public static IntervalList fromVcf(final VCFFileReader vcf, final boolean includeFiltered) {

        //grab the dictionary from the VCF and use it in the IntervalList
        final SAMSequenceDictionary dict = vcf.getFileHeader().getSequenceDictionary();
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSequenceDictionary(dict);
        final IntervalList list = new IntervalList(samFileHeader);

        int intervals = 0;
        for (final VariantContext vc : vcf) {
            if (includeFiltered || !vc.isFiltered()) {
                String name = vc.getID();
                final Integer intervalEnd = vc.getCommonInfo().getAttributeAsInt("END", vc.getEnd());
                if (".".equals(name) || name == null)
                    name = "interval-" + (++intervals);
                list.add(new Interval(vc.getContig(), vc.getStart(), intervalEnd, false, name));
            }
        }

        return list;
    }

    /**
     * Returns the VCFHeader associated with this VCF/BCF file.
     */
    public VCFHeader getFileHeader() {
        return (VCFHeader) reader.getHeader();
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

    public boolean isQueriable() {
        return reader.hasIndex();
    }
}
