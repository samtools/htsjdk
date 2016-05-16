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

package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.VariantContextComparator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


/**
 * A class to represent a VCF header
 *
 * @author aaron
 * NOTE: This class stores header lines in lots of places. The original author noted that this should
 * be cleaned up at some point in the future (jgentry - 5/2013)
 */
public class VCFHeader implements Serializable {
    public static final long serialVersionUID = 1L;

    // the mandatory header fields
    public enum HEADER_FIELDS {
        CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
    }

    // the associated meta data
    private final Set<VCFHeaderLine> mMetaData = new LinkedHashSet<VCFHeaderLine>();
    private final Map<String, VCFInfoHeaderLine> mInfoMetaData = new LinkedHashMap<String, VCFInfoHeaderLine>();
    private final Map<String, VCFFormatHeaderLine> mFormatMetaData = new LinkedHashMap<String, VCFFormatHeaderLine>();
    private final Map<String, VCFFilterHeaderLine> mFilterMetaData = new LinkedHashMap<String, VCFFilterHeaderLine>();
    private final Map<String, VCFHeaderLine> mOtherMetaData = new LinkedHashMap<String, VCFHeaderLine>();
    private final List<VCFContigHeaderLine> contigMetaData = new ArrayList<VCFContigHeaderLine>();

    // the list of auxillary tags
    private final List<String> mGenotypeSampleNames = new ArrayList<String>();

    // the character string that indicates meta data
    public static final String METADATA_INDICATOR = "##";

    // the header string indicator
    public static final String HEADER_INDICATOR = "#";

    public static final String SOURCE_KEY = "source";
    public static final String REFERENCE_KEY = "reference";
    public static final String CONTIG_KEY = "contig";
    public static final String INTERVALS_KEY = "intervals";
    public static final String EXCLUDE_INTERVALS_KEY = "excludeIntervals";
    public static final String INTERVAL_MERGING_KEY = "interval_merging";
    public static final String INTERVAL_SET_RULE_KEY = "interval_set_rule";
    public static final String INTERVAL_PADDING_KEY = "interval_padding";

    // were the input samples sorted originally (or are we sorting them)?
    private boolean samplesWereAlreadySorted = true;

    // cache for efficient conversion of VCF -> VariantContext
    private ArrayList<String> sampleNamesInOrder = null;
    private HashMap<String, Integer> sampleNameToOffset = null;

    private boolean writeEngineHeaders = true;
    private boolean writeCommandLine = true;

    /**
     * Create an empty VCF header with no header lines and no samples
     */
    public VCFHeader() {
        this(Collections.<VCFHeaderLine>emptySet(), Collections.<String>emptySet());
    }

    /**
     * create a VCF header, given a list of meta data and auxiliary tags
     *
     * @param metaData     the meta data associated with this header
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData) {
        mMetaData.addAll(metaData);
        removeVCFVersionLines(mMetaData);
        createLookupEntriesForAllHeaderLines();
        checkForDeprecatedGenotypeLikelihoodsKey();
    }

    /**
     * Creates a deep copy of the given VCFHeader, duplicating all its metadata and
     * sample names.
     */
    public VCFHeader(final VCFHeader toCopy) {
        this(toCopy.mMetaData, toCopy.mGenotypeSampleNames);
    }

    /**
     * create a VCF header, given a list of meta data and auxillary tags
     *
     * @param metaData            the meta data associated with this header
     * @param genotypeSampleNames the sample names
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData, final Set<String> genotypeSampleNames) {
        this(metaData, new ArrayList<String>(genotypeSampleNames));
    }

    public VCFHeader(final Set<VCFHeaderLine> metaData, final List<String> genotypeSampleNames) {
        this(metaData);

        if ( genotypeSampleNames.size() != new HashSet<String>(genotypeSampleNames).size() )
            throw new TribbleException.InvalidHeader("BUG: VCF header has duplicate sample names");

        mGenotypeSampleNames.addAll(genotypeSampleNames);
        samplesWereAlreadySorted = ParsingUtils.isSorted(genotypeSampleNames);
        buildVCFReaderMaps(genotypeSampleNames);
    }

    /**
     * Tell this VCF header to use pre-calculated sample name ordering and the
     * sample name -> offset map.  This assumes that all VariantContext created
     * using this header (i.e., read by the VCFCodec) will have genotypes
     * occurring in the same order
     *
     * @param genotypeSampleNamesInAppearenceOrder genotype sample names, must iterator in order of appearance
     */
    private void buildVCFReaderMaps(final Collection<String> genotypeSampleNamesInAppearenceOrder) {
        sampleNamesInOrder = new ArrayList<String>(genotypeSampleNamesInAppearenceOrder.size());
        sampleNameToOffset = new HashMap<String, Integer>(genotypeSampleNamesInAppearenceOrder.size());

        int i = 0;
        for (final String name : genotypeSampleNamesInAppearenceOrder) {
            sampleNamesInOrder.add(name);
            sampleNameToOffset.put(name, i++);
        }
        Collections.sort(sampleNamesInOrder);
    }


    /**
     * Adds a new line to the VCFHeader. If there is an existing header line of the
     * same type with the same key, the new line is not added and the existing line
     * is preserved.
     *
     * @param headerLine header line to attempt to add
     */
    public void addMetaDataLine(final VCFHeaderLine headerLine) {
        // Try to create a lookup entry for the new line. If this succeeds (because there was
        // no line of this type with the same key), add the line to our master list of header
        // lines in mMetaData.
        if ( addMetadataLineLookupEntry(headerLine) ) {
            mMetaData.add(headerLine);
            checkForDeprecatedGenotypeLikelihoodsKey();
        }
    }

    /**
     * @return all of the VCF header lines of the ##contig form in order, or an empty list if none were present
     */
    public List<VCFContigHeaderLine> getContigLines() {
        return Collections.unmodifiableList(contigMetaData);
    }

    /**
     * Returns the contigs in this VCF file as a SAMSequenceDictionary. Returns null if contigs lines are
     * not present in the header. Throws SAMException if one or more contig lines do not have length
     * information.
     */
    public SAMSequenceDictionary getSequenceDictionary() {
        final List<VCFContigHeaderLine> contigHeaderLines = this.getContigLines();
        if (contigHeaderLines.isEmpty()) return null;

        final List<SAMSequenceRecord> sequenceRecords = new ArrayList<SAMSequenceRecord>(contigHeaderLines.size());
        for (final VCFContigHeaderLine contigHeaderLine : contigHeaderLines) {
            sequenceRecords.add(contigHeaderLine.getSAMSequenceRecord());
        }

        return new SAMSequenceDictionary(sequenceRecords);
    }

    /**
     * Completely replaces the contig records in this header with those in the given SAMSequenceDictionary.
     */
    public void setSequenceDictionary(final SAMSequenceDictionary dictionary) {
        this.contigMetaData.clear();

        // Also need to remove contig record lines from mMetaData
        final List<VCFHeaderLine> toRemove = new ArrayList<VCFHeaderLine>();
        for (final VCFHeaderLine line : mMetaData) {
            if (line instanceof VCFContigHeaderLine) {
                toRemove.add(line);
            }
        }
        mMetaData.removeAll(toRemove);
        for (final SAMSequenceRecord record : dictionary.getSequences()) {
            contigMetaData.add(new VCFContigHeaderLine(record, record.getAssembly()));
        }

        this.mMetaData.addAll(contigMetaData);
    }

    public VariantContextComparator getVCFRecordComparator() {
        return new VariantContextComparator(this.getContigLines());
    }

    /**
     * @return all of the VCF FILTER lines in their original file order, or an empty list if none were present
     */
    public List<VCFFilterHeaderLine> getFilterLines() {
        final List<VCFFilterHeaderLine> filters = new ArrayList<VCFFilterHeaderLine>();
        for (final VCFHeaderLine line : mMetaData) {
            if ( line instanceof VCFFilterHeaderLine )  {
                filters.add((VCFFilterHeaderLine)line);
            }
        }
        return filters;
    }

    /**
     * @return all of the VCF FILTER lines in their original file order, or an empty list if none were present
     */
    public List<VCFIDHeaderLine> getIDHeaderLines() {
        final List<VCFIDHeaderLine> filters = new ArrayList<VCFIDHeaderLine>();
        for (final VCFHeaderLine line : mMetaData) {
            if (line instanceof VCFIDHeaderLine)  {
                filters.add((VCFIDHeaderLine)line);
            }
        }
        return filters;
    }

    /**
     * Remove all lines with a VCF version tag from the provided set of header lines
     */
    private void removeVCFVersionLines( final Set<VCFHeaderLine> headerLines ) {
        final List<VCFHeaderLine> toRemove = new ArrayList<VCFHeaderLine>();
        for (final VCFHeaderLine line : headerLines) {
            if (VCFHeaderVersion.isFormatString(line.getKey())) {
                toRemove.add(line);
            }
        }
        headerLines.removeAll(toRemove);
    }

    /**
     * Creates lookup table entries for all header lines in mMetaData.
     */
    private void createLookupEntriesForAllHeaderLines() {
        for (final VCFHeaderLine line : mMetaData) {
            addMetadataLineLookupEntry(line);
        }
    }

    /**
     * Add a single header line to the appropriate type-specific lookup table (but NOT to the master
     * list of lines in mMetaData -- this must be done separately if desired).
     *
     * If a header line is present that has the same key as an existing line, it will not be added.  A warning
     * will be shown if this occurs when GeneralUtils.DEBUG_MODE_ENABLED is true, otherwise this will occur
     * silently.
     *
     * @param line header line to attempt to add to its type-specific lookup table
     * @return true if the line was added to the appropriate lookup table, false if there was an existing
     *         line with the same key and the new line was not added
     */
    private boolean addMetadataLineLookupEntry(final VCFHeaderLine line) {
        if ( line instanceof VCFInfoHeaderLine )  {
            final VCFInfoHeaderLine infoLine = (VCFInfoHeaderLine)line;
            return addMetaDataLineMapLookupEntry(mInfoMetaData, infoLine.getID(), infoLine);
        } else if ( line instanceof VCFFormatHeaderLine ) {
            final VCFFormatHeaderLine formatLine = (VCFFormatHeaderLine)line;
            return addMetaDataLineMapLookupEntry(mFormatMetaData, formatLine.getID(), formatLine);
        } else if ( line instanceof VCFFilterHeaderLine ) {
            final VCFFilterHeaderLine filterLine = (VCFFilterHeaderLine)line;
            return addMetaDataLineMapLookupEntry(mFilterMetaData, filterLine.getID(), filterLine);
        } else if ( line instanceof VCFContigHeaderLine ) {
            return addContigMetaDataLineLookupEntry((VCFContigHeaderLine) line);
        } else {
            return addMetaDataLineMapLookupEntry(mOtherMetaData, line.getKey(), line);
        }
    }

    /**
     * Add a contig header line to the lookup list for contig lines (contigMetaData). If there's
     * already a contig line with the same ID, does not add the line.
     *
     * Note: does not add the contig line to the master list of header lines in mMetaData --
     *       this must be done separately if desired.
     *
     * @param line contig header line to add
     * @return true if line was added to the list of contig lines, otherwise false
     */
    private boolean addContigMetaDataLineLookupEntry(final VCFContigHeaderLine line) {
        for (VCFContigHeaderLine vcfContigHeaderLine : contigMetaData) {
            // if we are trying to add a contig for the same ID
            if (vcfContigHeaderLine.getID().equals(line.getID())) {
                if ( GeneralUtils.DEBUG_MODE_ENABLED ) {
                    System.err.println("Found duplicate VCF contig header lines for " + line.getID() + "; keeping the first only" );
                }
                // do not add this contig if it exists
                return false;
            }
        }

        contigMetaData.add(line);
        return true;
    }

    /**
     * Add a header line to the provided map at a given key.  If the key already exists, it will not be replaced.
     * If it does already exist and GeneralUtils.DEBUG_MODE_ENABLED is true, it will issue warnings about duplicates,
     * otherwise it will silently leave the existing key/line pair as is.
     *
     * Note: does not add the header line to the master list of header lines in mMetaData --
     *       this must be done separately if desired.
     *
     * @param map a map from each key to the associated VCFHeaderLine
     * @param key the key to insert this line at
     * @param line the line to insert at this key
     * @param <T> a type of vcf header line that extends VCFHeaderLine
     * @return true if the line was added to the map, false if it was not added because there's already a line with that key
     */
    private <T extends VCFHeaderLine> boolean addMetaDataLineMapLookupEntry(final Map<String, T> map, final String key, final T line) {
        if ( map.containsKey(key) ) {
            if ( GeneralUtils.DEBUG_MODE_ENABLED ) {
                System.err.println("Found duplicate VCF header lines for " + key + "; keeping the first only" );
            }
            return false;
        }

        map.put(key, line);
        return true;
    }

    /**
     * Check for the presence of a format line with the deprecated key {@link VCFConstants#GENOTYPE_LIKELIHOODS_KEY}.
     * If one is present, and there isn't a format line with the key {@link VCFConstants#GENOTYPE_PL_KEY}, adds
     * a new format line with the key {@link VCFConstants#GENOTYPE_PL_KEY}.
     */
    private void checkForDeprecatedGenotypeLikelihoodsKey() {
        if ( hasFormatLine(VCFConstants.GENOTYPE_LIKELIHOODS_KEY) && ! hasFormatLine(VCFConstants.GENOTYPE_PL_KEY) ) {
            if ( GeneralUtils.DEBUG_MODE_ENABLED ) {
                System.err.println("Found " + VCFConstants.GENOTYPE_LIKELIHOODS_KEY + " format, but no "
                        + VCFConstants.GENOTYPE_PL_KEY + " field.  We now only manage PL fields internally"
                        + " automatically adding a corresponding PL field to your VCF header");
            }
            addMetaDataLine(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_PL_KEY, VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "Normalized, Phred-scaled likelihoods for genotypes as defined in the VCF specification"));
        }
    }

    /**
     * get the header fields in order they're presented in the input file (which is now required to be
     * the order presented in the spec).
     *
     * @return a set of the header fields, in order
     */
    public Set<HEADER_FIELDS> getHeaderFields() {
        return new LinkedHashSet<HEADER_FIELDS>(Arrays.asList(HEADER_FIELDS.values()));
    }

    /**
     * get the meta data, associated with this header, in sorted order
     *
     * @return a set of the meta data
     */
    public Set<VCFHeaderLine> getMetaDataInInputOrder() {
        return makeGetMetaDataSet(mMetaData);
    }

    public Set<VCFHeaderLine> getMetaDataInSortedOrder() {
        return makeGetMetaDataSet(new TreeSet<VCFHeaderLine>(mMetaData));
    }

    private static Set<VCFHeaderLine> makeGetMetaDataSet(final Set<VCFHeaderLine> headerLinesInSomeOrder) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<VCFHeaderLine>();
        lines.add(new VCFHeaderLine(VCFHeaderVersion.VCF4_2.getFormatString(), VCFHeaderVersion.VCF4_2.getVersionString()));
        lines.addAll(headerLinesInSomeOrder);
        return Collections.unmodifiableSet(lines);
    }

    /**
     * Get the VCFHeaderLine whose key equals key.  Returns null if no such line exists
     * @param key
     * @return
     */
    public VCFHeaderLine getMetaDataLine(final String key) {
        for (final VCFHeaderLine line: mMetaData) {
            if ( line.getKey().equals(key) )
                return line;
        }

        return null;
    }

    /**
     * get the genotyping sample names
     *
     * @return a list of the genotype column names, which may be empty if hasGenotypingData() returns false
     */
    public List<String> getGenotypeSamples() {
        return mGenotypeSampleNames;
    }

    public int getNGenotypeSamples() {
        return mGenotypeSampleNames.size();
    }

    /**
     * do we have genotyping data?
     *
     * @return true if we have genotyping columns, false otherwise
     */
    public boolean hasGenotypingData() {
        return getNGenotypeSamples() > 0;
    }

    /**
     * were the input samples sorted originally?
     *
     * @return true if the input samples were sorted originally, false otherwise
     */
    public boolean samplesWereAlreadySorted() {
        return samplesWereAlreadySorted;
    }

    /** @return the column count */
    public int getColumnCount() {
        return HEADER_FIELDS.values().length + (hasGenotypingData() ? mGenotypeSampleNames.size() + 1 : 0);
    }

    /**
     * Returns the INFO HeaderLines in their original ordering
     */
    public Collection<VCFInfoHeaderLine> getInfoHeaderLines() {
        return mInfoMetaData.values();
    }

    /**
     * Returns the FORMAT HeaderLines in their original ordering
     */
    public Collection<VCFFormatHeaderLine> getFormatHeaderLines() {
        return mFormatMetaData.values();
    }

    /**
     * @param id the header key name
     * @return the meta data line, or null if there is none
     */
    public VCFInfoHeaderLine getInfoHeaderLine(final String id) {
        return mInfoMetaData.get(id);
    }

    /**
     * @param id    the header key name
     * @return the meta data line, or null if there is none
     */
    public VCFFormatHeaderLine getFormatHeaderLine(final String id) {
        return mFormatMetaData.get(id);
    }

    /**
     * @param id    the header key name
     * @return the meta data line, or null if there is none
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String id) {
        return mFilterMetaData.get(id);
    }

    public boolean hasInfoLine(final String id) {
        return getInfoHeaderLine(id) != null;
    }

    public boolean hasFormatLine(final String id) {
        return getFormatHeaderLine(id) != null;
    }

    public boolean hasFilterLine(final String id) {
        return getFilterHeaderLine(id) != null;
    }

    /**
     * @param key    the header key name
     * @return the meta data line, or null if there is none
     */
    public VCFHeaderLine getOtherHeaderLine(final String key) {
        return mOtherMetaData.get(key);
    }

    /**
     * Returns the other HeaderLines in their original ordering
     */
    public Collection<VCFHeaderLine> getOtherHeaderLines() {
        return mOtherMetaData.values();
    }

    /**
     * If true additional engine headers will be written to the VCF, otherwise only the walker headers will be output.
     * @return true if additional engine headers will be written to the VCF
     */
    public boolean isWriteEngineHeaders() {
        return writeEngineHeaders;
    }

    /**
     * If true additional engine headers will be written to the VCF, otherwise only the walker headers will be output.
     * @param writeEngineHeaders true if additional engine headers will be written to the VCF
     */
    public void setWriteEngineHeaders(final boolean writeEngineHeaders) {
        this.writeEngineHeaders = writeEngineHeaders;
    }

    /**
     * If true, and isWriteEngineHeaders also returns true, the command line will be written to the VCF.
     * @return true if the command line will be written to the VCF
     */
    public boolean isWriteCommandLine() {
        return writeCommandLine;
    }

    /**
     * If true, and isWriteEngineHeaders also returns true, the command line will be written to the VCF.
     * @param writeCommandLine true if the command line will be written to the VCF
     */
    public void setWriteCommandLine(final boolean writeCommandLine) {
        this.writeCommandLine = writeCommandLine;
    }

    public ArrayList<String> getSampleNamesInOrder() {
        return sampleNamesInOrder;
    }

    public HashMap<String, Integer> getSampleNameToOffset() {
        return sampleNameToOffset;
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append("[VCFHeader:");
        for ( final VCFHeaderLine line : mMetaData )
            b.append("\n\t").append(line);
        return b.append("\n]").toString();
    }
}
