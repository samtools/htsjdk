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
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContextComparator;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to represent a VCF header.
 *
 * VCFHeaders maintain a VCFHeaderVersion that is established via the following precedence:
 *
 *  - derived from a ##fileformat line embedded in the metadata lines list
 *  - supplied in a constructor
 *  - the default header version, currently vcfv42
 *
 *  Any attempt to add metadata lines, or change the header version via {@link #setVCFHeaderVersion} will
 *  trigger a validation pass against the metadata lines to ensure they conform to the rules defined by
 *  the VCF specification for that version.
 */
public class VCFHeader implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFHeader.class);
    public final static VCFHeaderVersion DEFAULT_VCF_VERSION = VCFHeaderVersion.VCF4_2;

    // the mandatory header fields
    public enum HEADER_FIELDS {
        CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
    }

    /**
     * The VCF version for this header; once a header version is established, it can only be
     * changed subject to the version transition rules defined by {@link #setVCFHeaderVersion}.
     */
    private VCFHeaderVersion vcfHeaderVersion;

    //TODO: Should we reject attempts to add two contig header lines with the same contigIndex ?
    // GATK VcfUtilsUnitTest.createHeaderLines test creates headers with contig lines with identical (0) indices

    // The associated meta data
    private final VCFMetaDataLines mMetaData = new VCFMetaDataLines();

    // the list of auxiliary tags
    private final List<String> mGenotypeSampleNames = new ArrayList<>();

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
        this(getHeaderVersionLineSet(DEFAULT_VCF_VERSION), Collections.emptySet());
    }

    /**
     * Create a VCF header, given a list of meta data and auxiliary tags. The provided metadata
     * header lin e list MUST contain a version (fileformat) line in order to establish the version
     * for this header.
     *
     * @param metaData the meta data associated with this header
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines
     */
    //TODO: should these constructors be deprecated and replaced with ones that accept LinkHashSet, or should
    // we just document that order matters (for contig lines sort order) ?
    public VCFHeader(final Set<VCFHeaderLine> metaData) {
        this(metaData, Collections.emptySet());
    }

    /**
     * Creates a copy of the given VCFHeader, duplicating all it's metadata and
     * sample names.
     */
    public VCFHeader(final VCFHeader toCopy) {
        this(toCopy.getMetaDataInInputOrder(), toCopy.mGenotypeSampleNames);
    }

    /**
     * Create a VCF header, given a set of meta data and auxiliary tags. The provided metadata
     * header lin e list MUST contain a version (fileformat) line in order to establish the version
     * for this header.
     *
     * @param metaData            set of meta data associated with this header
     * @param genotypeSampleNames the sample names
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines
     */
    //TODO: should these constructors be deprecated and replaced with ones that accept LinkHashSet, or should
    // we just document that order matters (for contig lines sort order) ?
    public VCFHeader(final Set<VCFHeaderLine> metaData, final Set<String> genotypeSampleNames) {
        this(metaData, new ArrayList<>(genotypeSampleNames));
    }

    /**
     * Create a versioned VCF header.
     *
     * @param metaData The metadata lines for this header.The provided metadata
     * header line list MUST contain a version (fileformat) line in order to establish the version
     * for this header.
     * @param genotypeSampleNames Sample names for this header.
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines
     */
    //TODO: should these constructors be deprecated and replaced with ones that accept LinkHashSet, or should
    // we just document that order matters (for contig lines sort order) ?
    public VCFHeader(final Set<VCFHeaderLine> metaData, final List<String> genotypeSampleNames) {
        ValidationUtils.nonNull(metaData);
        ValidationUtils.nonNull(genotypeSampleNames);

        // Establish the version for this header using the ##fileformat metadata line in the metadata list
        this.vcfHeaderVersion = establishInitialHeaderVersion(metaData);
        mMetaData.addAllMetaDataLines(metaData);
        //validate that the provided metadata lines are valid for the version the established version
        mMetaData.validateMetaDataLines(this.vcfHeaderVersion, false);

        checkForDeprecatedGenotypeLikelihoodsKey();

        if ( genotypeSampleNames.size() != new HashSet<>(genotypeSampleNames).size() )
            throw new TribbleException.InvalidHeader("BUG: VCF header has duplicate sample names");

        mGenotypeSampleNames.addAll(genotypeSampleNames);
        samplesWereAlreadySorted = ParsingUtils.isSorted(genotypeSampleNames);
        buildVCFReaderMaps(genotypeSampleNames);
    }

    /**
     * Set the header version for this header, subject to {@link VCFMetaDataLines#validateMetaDataLines}.
     * @param newVCFVersion the new version to use for this header
     * @throws TribbleException if the requested header version is not compatible with the existing header lines
     */
    public void setVCFHeaderVersion(final VCFHeaderVersion newVCFVersion) {
        ValidationUtils.nonNull(newVCFVersion, "A non-null VCFHeaderVersion must be provided");

        // we can't necessarily validate versions older than 4.2, so don't allow the version
        // to ever go backward, only forward
        final int compareTo = newVCFVersion.compareTo(vcfHeaderVersion);
        if (compareTo < 0) {
            throw new IllegalStateException(String.format(
                    "New header version %s must be >= existing version %s",
                    newVCFVersion,
                    vcfHeaderVersion));
        }
        if (compareTo > 0) {
           if (VCFUtils.getVerboseVCFLogging()) {
                logger.warn(String.format("Changing VCFHeader version from %s to %s",
                        vcfHeaderVersion.getVersionString(),
                        newVCFVersion.getVersionString()));
            }
            mMetaData.setVCFVersion(newVCFVersion);
            this.vcfHeaderVersion = newVCFVersion;
        }
    }

    /**
     * Obtain a valid fileformat/version line for the requestedVersion
     * @param requestedVersion the version for which a version line should be obtained
     * @return the version line
     */
    public static VCFHeaderLine getHeaderVersionLine(final VCFHeaderVersion requestedVersion) {
        return new VCFHeaderLine(requestedVersion.getFormatString(), requestedVersion.getVersionString());
    }

    /**
     * Obtain a VCFHeaderLine set containing only a fileformat/version line for the requestedVersion
     * @param requestedVersion the version for which a version line should be obtained
     * @return a VCFHeaderLine set containing only fileformat/version line for the requestedVersion
     */
    public static Set<VCFHeaderLine> getHeaderVersionLineSet(final VCFHeaderVersion requestedVersion) {
        return new LinkedHashSet<VCFHeaderLine>() {{ add(VCFHeader.getHeaderVersionLine(requestedVersion)); }};
    }

   /**
    * Get the header version for this header.
    * @return the VCFHeaderVersion for this header.
    */
    public VCFHeaderVersion getVCFHeaderVersion() {
        return vcfHeaderVersion;
    }

    /**
     * Adds a new line to the VCFHeader. If there is an existing header line of the
     * same type with the same key, and the header version is pre-4.3, the new line is added
     * using a modified key to make it unique for BWC; otherwise (in strict validation mode), an
     * exception is thrown since duplicates are not allowed.
     *
     * @param headerLine header line to attempt to add
     */
    public void addMetaDataLine(final VCFHeaderLine headerLine) {
        mMetaData.validateMetaDataLine(vcfHeaderVersion, headerLine);
        mMetaData.addMetaDataLine(headerLine);
        checkForDeprecatedGenotypeLikelihoodsKey();
    }

    /**
     * Tell this VCF header to use pre-calculated sample name ordering and the
     * sample name -> offset map.  This assumes that all VariantContext created
     * using this header (i.e., read by the VCFCodec) will have genotypes
     * occurring in the same order
     *
     * @param genotypeSampleNamesInAppearanceOrder genotype sample names, must iterator in order of appearance
     */
    private void buildVCFReaderMaps(final Collection<String> genotypeSampleNamesInAppearanceOrder) {
        sampleNamesInOrder = new ArrayList<>(genotypeSampleNamesInAppearanceOrder.size());
        sampleNameToOffset = new HashMap<>(genotypeSampleNamesInAppearanceOrder.size());

        int i = 0;
        for (final String name : genotypeSampleNamesInAppearanceOrder) {
            sampleNamesInOrder.add(name);
            sampleNameToOffset.put(name, i++);
        }
        Collections.sort(sampleNamesInOrder);
    }

    /**
     * Find and return the VCF fileformat/version line
     *
     * Return null if no fileformat/version lines are found
     */
    protected static VCFHeaderLine getVersionLineFromHeaderLineSet(final Set<VCFHeaderLine> metaDataLines) {
        VCFHeaderLine versionLine = null;
        final List<VCFHeaderLine> formatLines = new ArrayList<>();
        for (final VCFHeaderLine headerLine : metaDataLines) {
            if (VCFHeaderVersion.isFormatString(headerLine.getKey())) {
                formatLines.add(headerLine);
            }
        }

        if (!formatLines.isEmpty()) {
            if (formatLines.size() > 1) {
                //throw if there are duplicate version lines
                throw new TribbleException("Multiple version header lines found in header line list");
            }
            return formatLines.get(0);
        }

        return versionLine;
    }

    /**
     * @return all of the VCF header lines of the ##contig form in order, or an empty list if none were present
     */
    public List<VCFContigHeaderLine> getContigLines() {
        // this must preserve input order
        return mMetaData.getContigLines();
   }

    /**
     * Returns the contigs in this VCF Header as a SAMSequenceDictionary.
     *
     * @return Returns null if contig lines are not present in the header.
     * @throws TribbleException if one or more contig lines do not have length
     * information.
     */
    public SAMSequenceDictionary getSequenceDictionary() {
        final List<VCFContigHeaderLine> contigHeaderLines = this.getContigLines();
        return contigHeaderLines.isEmpty() ? null  :
                new SAMSequenceDictionary(
                    contigHeaderLines.stream()
                            .map(contigLine -> contigLine.getSAMSequenceRecord())
                            .collect(Collectors.toCollection(ArrayList::new))
                );
    }

    /**
     * Completely replaces all contig header lines in this header with ones derived from the given SAMSequenceDictionary.
     *
     * @param dictionary SAMSequenceDictionary to use to create VCFContigHeaderLines for this header
     */
    //TODO:this implementation should be delegated to VCFMetaDataLines
    public void setSequenceDictionary(final SAMSequenceDictionary dictionary) {
        getContigLines().forEach(hl -> mMetaData.removeHeaderLine(hl));
        dictionary.getSequences().forEach(r -> addMetaDataLine(new VCFContigHeaderLine(r, r.getAssembly())));
    }

    public VariantContextComparator getVCFRecordComparator() {
        return new VariantContextComparator(this.getContigLines());
    }

    /**
     * @return all of the VCF FILTER lines in their original file order, or an empty list if none were present
     */
    public List<VCFFilterHeaderLine> getFilterLines() { return mMetaData.getFilterLines(); }

    /**
     * @return all of the VCFSimpleHeaderLine (ID)  lines in their original file order, or an empty list if none are present
     */
    public List<VCFSimpleHeaderLine> getIDHeaderLines() { return mMetaData.getIDHeaderLines(); }

    /**
     * Check for the presence of a format line with the deprecated key {@link VCFConstants#GENOTYPE_LIKELIHOODS_KEY}.
     * If one is present, and there isn't a format line with the key {@link VCFConstants#GENOTYPE_PL_KEY}, adds
     * a new format line with the key {@link VCFConstants#GENOTYPE_PL_KEY}.
     */
    private void checkForDeprecatedGenotypeLikelihoodsKey() {
        if ( hasFormatLine(VCFConstants.GENOTYPE_LIKELIHOODS_KEY) && ! hasFormatLine(VCFConstants.GENOTYPE_PL_KEY) ) {
            if ( VCFUtils.getVerboseVCFLogging() ) {
                logger.warn("Found " + VCFConstants.GENOTYPE_LIKELIHOODS_KEY + " format, but no "
                        + VCFConstants.GENOTYPE_PL_KEY + " field.  We now only manage PL fields internally"
                        + " automatically adding a corresponding PL field to your VCF header");
            }
            addMetaDataLine(new VCFFormatHeaderLine(
                    VCFConstants.GENOTYPE_PL_KEY,
                    VCFHeaderLineCount.G,
                    VCFHeaderLineType.Integer,
                    "Normalized, Phred-scaled likelihoods for genotypes as defined in the VCF specification"));
        }
    }

    /**
     * get the header fields in order they're presented in the input file (which is now required to be
     * the order presented in the spec).
     *
     * @return a set of the header fields, in order
     */
    public Set<HEADER_FIELDS> getHeaderFields() {
        return new LinkedHashSet<>(Arrays.asList(HEADER_FIELDS.values()));
    }

    /**
     * get the meta data, associated with this header, in input order
     *
     * @return a set of the meta data
     */
    public Set<VCFHeaderLine> getMetaDataInInputOrder() { return mMetaData.getMetaDataInInputOrder(); }

    /**
     * Get the metadata associated with this header in sorted order.
     *
     * @return Metadata lines in sorted order (based on lexicographical sort of string encodings).
     */
    public Set<VCFHeaderLine> getMetaDataInSortedOrder() { return mMetaData.getMetaDataInSortedOrder(); }

    /**
     * Get the VCFHeaderLine whose key equals key.  Returns null if no such line exists
     * 
     * //Deprecated. Use {@link #getMetaDataLines(String)}.
     * 
     * @param key the key to use to find header lines to return
     * @return the header line with key "key", or null if none is present
     */
    // TODO: decide if we should keep this depending on the the response to https://github.com/samtools/hts-specs/issues/602
    //@Deprecated // starting after version 2.24.1 (and this selects one from what can be many header lines)
    public VCFHeaderLine getMetaDataLine(final String key) { 
        return mMetaData.getMetaDataLines(key).stream().findFirst().orElse(null);
    }

    /**
     * Get the VCFHeaderLines whose key equals key.  Returns an empty list if no such lines exist.
     *
     * @param key the key to use to find header lines to return
     * @return the header lines with key "key"
     */
    public Collection<VCFHeaderLine> getMetaDataLines(final String key) { return mMetaData.getMetaDataLines(key); }
    
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
    public Collection<VCFInfoHeaderLine> getInfoHeaderLines() { return mMetaData.getInfoHeaderLines(); }

    /**
     * Returns the FORMAT HeaderLines in their original ordering
     */
    public Collection<VCFFormatHeaderLine> getFormatHeaderLines() { return mMetaData.getFormatHeaderLines(); }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFInfoHeaderLine getInfoHeaderLine(final String id) {
        return mMetaData.getInfoHeaderLine(id);
    }

    /**
     * @param id  the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFormatHeaderLine getFormatHeaderLine(final String id) { return mMetaData.getFormatHeaderLine(id); }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String id) { return mMetaData.getFilterHeaderLine(id); }

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
     * Deprecated. Use {@link #getOtherHeaderLines()}.
     * @param key the of the requested other header line
     * @return the meta data line, or null if there is none
     */
    // TODO: decide if we should keep this depending on the the response to https://github.com/samtools/hts-specs/issues/602
    //@Deprecated // starting after version 2.24.1 this selects one from what can be many)
    public VCFHeaderLine getOtherHeaderLine(final String key) { return mMetaData.getOtherHeaderLine(key); }

    /**
     * Returns the other HeaderLines in their original ordering, where "other" means any
     * VCFHeaderLine that is not a contig, info, format or filter header line.
     */
    public Collection<VCFHeaderLine> getOtherHeaderLines() { return mMetaData.getOtherHeaderLines(); }

    /**
     * If true additional engine headers will be written to the VCF, otherwise only the walker headers will be output.
     * @return true if additional engine headers will be written to the VCF
     */
    @Deprecated // starting after version 2.24.1
    public boolean isWriteEngineHeaders() {
        return writeEngineHeaders;
    }

    /**
     * If true additional engine headers will be written to the VCF, otherwise only the walker headers will be output.
     * @param writeEngineHeaders true if additional engine headers will be written to the VCF
     */
    @Deprecated // starting after version 2.24.1
    public void setWriteEngineHeaders(final boolean writeEngineHeaders) {
        this.writeEngineHeaders = writeEngineHeaders;
    }

    /**
     * If true, and isWriteEngineHeaders also returns true, the command line will be written to the VCF.
     * @return true if the command line will be written to the VCF
     */
    @Deprecated // starting after version 2.24.1
    public boolean isWriteCommandLine() {
        return writeCommandLine;
    }

    /**
     * If true, and isWriteEngineHeaders also returns true, the command line will be written to the VCF.
     * @param writeCommandLine true if the command line will be written to the VCF
     */
    @Deprecated // starting after version 2.24.1
    public void setWriteCommandLine(final boolean writeCommandLine) {
        this.writeCommandLine = writeCommandLine;
    }

    /**
     * Get the genotype sample names, sorted in ascending order. Note: this will not necessarily match the order in the VCF.
     * @return The sorted genotype samples. May be empty if hasGenotypingData() returns false.
     */
    public ArrayList<String> getSampleNamesInOrder() {
        return sampleNamesInOrder;
    }

    public HashMap<String, Integer> getSampleNameToOffset() {
        return sampleNameToOffset;
    }

    @Override
    public String toString() {
        return mMetaData.toString();
    }

    /**
     * Merge the header lines from all of the header lines in a set of header. The resulting set includes
     * all unique lines that appeared in any header. Lines that are duplicated are removed from the result
     * set. The resulting set is compatible with (and contains a ##fileformat version line for) the highest
     * version seen in any of the headers provided in the input collection.
     *
     * @param headers the headers to merge
     * @param emitWarnings true of warnings should be emitted
     * @return a set of merged VCFHeaderLines
     * @throws IllegalStateException if any header has a version < vcfV4.2, or if any header line in any
     * of the input headers is not compatible the newest version amongst all headers provided
     */
    //TODO: this should really return a merged HEADER (or at least the VCFMetaDataLines object that it creates)
    // and let VCFUtils.smartMergeHeader (which should now be deprecated, just extract the header lines from it;
    // will also need to add a VCFHeader(VCFMetaDataLines) constructor if we return VCFMetaDataLines
    public static Set<VCFHeaderLine> getMergedHeaderLines(final Collection<VCFHeader> headers, final boolean emitWarnings) {
        final VCFMetaDataLines mergedMetaData = new VCFMetaDataLines();
        final HeaderConflictWarner conflictWarner = new HeaderConflictWarner(emitWarnings);
        final Set<VCFHeaderVersion> vcfVersions = new HashSet<>(headers.size());

        VCFHeaderVersion newestVersion = null;
        for ( final VCFHeader source : headers ) {
            final VCFHeaderVersion sourceHeaderVersion = source.getVCFHeaderVersion();
            if (!sourceHeaderVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_2)) {
                throw new TribbleException(String.format("Cannot merge a VCFHeader with version (%s) that is older than version %s",
                        sourceHeaderVersion, VCFHeaderVersion.VCF4_2));
            }
            vcfVersions.add(sourceHeaderVersion);
            for ( final VCFHeaderLine line : source.getMetaDataInSortedOrder()) {
                final String key = line.getKey();
                if (VCFHeaderVersion.isFormatString(key)) {
                    if (newestVersion == null || (source.getVCFHeaderVersion().ordinal() > newestVersion.ordinal())) {
                        newestVersion = sourceHeaderVersion;
                    }
                    // don't add any version lines yet; wait until the end and we'll add the highest version,
                    // and then validate all lines against that
                    continue;
                }

                // NOTE: Structured header lines are only equal if they have identical attributes
                // and values (which is different from the previous implementation for some line types, like
                // compound header lines). So we use a more discriminating "hasEquivalentHeaderLine" to determine
                // equivalence, and delegate to the actual lines and to do a smart reconciliation.
                final VCFHeaderLine other = mergedMetaData.hasEquivalentHeaderLine(line);
                if (other != null && !line.equals(other) ) {
                    if (!line.getKey().equals(other.getKey())) {
                        throw new IllegalArgumentException(
                                String.format("Attempt to merge incompatible header lines %s/%s", line.getKey(), other.getKey()));
                    } else if (key.equals(VCFConstants.FORMAT_HEADER_KEY)) {
                        // Delegate to the resolver function
                        mergedMetaData.addMetaDataLine(VCFCompoundHeaderLine.getSmartMergedCompoundHeaderLine(
                                (VCFCompoundHeaderLine) line,
                                (VCFCompoundHeaderLine) other,
                                conflictWarner,
                                (l1, l2) -> new VCFFormatHeaderLine(
                                                l1.getID(),
                                                VCFHeaderLineCount.UNBOUNDED,
                                                l1.getType(),
                                                l1.getDescription())
                                )
                        );
                    } else if (key.equals(VCFConstants.INFO_HEADER_KEY)) {
                        // Delegate to the resolver function
                        mergedMetaData.addMetaDataLine(VCFCompoundHeaderLine.getSmartMergedCompoundHeaderLine(
                                (VCFCompoundHeaderLine) line,
                                (VCFCompoundHeaderLine) other,
                                conflictWarner,
                                (l1, l2) -> new VCFInfoHeaderLine(
                                        l1.getID(),
                                        VCFHeaderLineCount.UNBOUNDED,
                                        l1.getType(),
                                        l1.getDescription())
                                )
                        );
                    } else {
                        // same type of header line; not equal; but not compound(format/info)
                        // preserve the existing one; this may drop attributes/values
                        conflictWarner.warn(line, "Ignoring header line already in map: this header line = " +
                                line + " already present header = " + other);
                    }
                } else {
                    mergedMetaData.addMetaDataLine(line);
                }
            }
        }
        // this will validate all of the lines against the version line included
        mergedMetaData.setVCFVersion(newestVersion);

        // returning a LinkedHashSet so that ordering will be preserved. Ensures the contig lines do not get scrambled.
        return new LinkedHashSet<>(mergedMetaData.getMetaDataInInputOrder());
    }

    /**
     * Establish the version for this header using the (required) ##fileformat metadata line in the metadata list.
     * @param metaData
     * @throws TribbleException if no fileformat line is included in the metadata lines
     */
    private VCFHeaderVersion establishInitialHeaderVersion(final Set<VCFHeaderLine> metaData) {
        VCFHeaderLine embeddedVersionLine = getVersionLineFromHeaderLineSet(metaData);
        if (embeddedVersionLine == null) {
            //TODO: should we relax this/only warn for VCFUtils.getStrictVCFVersionValidation() == false?
            //I'm inclined to say no, since that might cause downstream issues
            throw new TribbleException("The VCFHeader metadata must include a ##fileformat (version) header line");
        }
        // embeddedVersionLine not null, validate against the provided lines
        return VCFHeaderVersion.toHeaderVersion(embeddedVersionLine.getValue());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final VCFHeader vcfHeader = (VCFHeader) o;

        if (samplesWereAlreadySorted != vcfHeader.samplesWereAlreadySorted) return false;
        if (writeEngineHeaders != vcfHeader.writeEngineHeaders) return false;
        if (writeCommandLine != vcfHeader.writeCommandLine) return false;
        if (vcfHeaderVersion != vcfHeader.vcfHeaderVersion) return false;
        if (!mMetaData.equals(vcfHeader.mMetaData)) return false;
        if (mGenotypeSampleNames != null ? !mGenotypeSampleNames.equals(vcfHeader.mGenotypeSampleNames) :
                vcfHeader.mGenotypeSampleNames != null)
            return false;
        if (sampleNamesInOrder != null ? !sampleNamesInOrder.equals(vcfHeader.sampleNamesInOrder) :
                vcfHeader.sampleNamesInOrder != null)
            return false;
        return sampleNameToOffset != null ? sampleNameToOffset.equals(vcfHeader.sampleNameToOffset) :
                vcfHeader.sampleNameToOffset == null;
    }

    @Override
    public int hashCode() {
        int result = vcfHeaderVersion.hashCode();
        result = 31 * result + mMetaData.hashCode();
        result = 31 * result + (mGenotypeSampleNames != null ? mGenotypeSampleNames.hashCode() : 0);
        result = 31 * result + (samplesWereAlreadySorted ? 1 : 0);
        result = 31 * result + (sampleNamesInOrder != null ? sampleNamesInOrder.hashCode() : 0);
        result = 31 * result + (sampleNameToOffset != null ? sampleNameToOffset.hashCode() : 0);
        result = 31 * result + (writeEngineHeaders ? 1 : 0);
        result = 31 * result + (writeCommandLine ? 1 : 0);
        return result;
    }

    /** Only displays a warning if warnings are enabled and an identical warning hasn't been already issued */
    static final class HeaderConflictWarner {
        boolean emitWarnings;
        Set<String> alreadyIssued = new HashSet<String>();

        protected HeaderConflictWarner( final boolean emitWarnings ) {
            this.emitWarnings = emitWarnings;
        }

        public void warn(final VCFHeaderLine line, final String msg) {
            if ( emitWarnings && ! alreadyIssued.contains(line.getKey()) ) {
                alreadyIssued.add(line.getKey());
                logger.warn(msg);
            }
        }
    }

}
