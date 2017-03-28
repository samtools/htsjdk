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

    // TODO: Should we reject attempts to add two contig header lines with the same contigIndex ?
    // TODO: GATK VcfUtilsUnitTest.createHeaderLines test creates headers with contig lines with identical (0) indices
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
        //TODO: deprecate this (no way to determine the intended version)
        this(getHeaderVersionLineSet(DEFAULT_VCF_VERSION), Collections.emptySet());
    }

    /**
     * Create a VCF header, given a list of meta data and auxiliary tags
     *
     * @param metaData the meta data associated with this header
     */
    //TODO: Note that this now requires the metadata to include a version line
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
        // TODO: this constructor doesn't propagate all of the existing header state (writeEngineHeaders, etc)
        this(toCopy.getMetaDataInInputOrder(), toCopy.mGenotypeSampleNames);
    }

    /**
     * Create a VCF header, given a set of meta data and auxiliary tags
     *
     * @param metaData            set of meta data associated with this header
     * @param genotypeSampleNames the sample names
     */
    //TODO: should these constructors be deprecated and replaced with ones that accept LinkHashSet, or should
    // we just document that order matters (for contig lines sort order) ?
    public VCFHeader(final Set<VCFHeaderLine> metaData, final Set<String> genotypeSampleNames) {
        this(metaData, new ArrayList<>(genotypeSampleNames));
    }

    /**
     * Create a versioned VCF header.
     *
     * @param metaData The metadata lines for this header. The set must include a ##fileformat version, and
     *                the remaining lines must be valid for that version.
     * @param genotypeSampleNames Sample names for this header.
     */
    //TODO: should these constructors be deprecated and replaced with ones that accept LinkHashSet, or should
    // we just document that order matters (for contig lines sort order) ?
    public VCFHeader(final Set<VCFHeaderLine> metaData, final List<String> genotypeSampleNames) {
        ValidationUtils.nonNull(metaData);
        ValidationUtils.nonNull(genotypeSampleNames);

        // Establish the version for this header using the ##fileformat metadata line in the metadata list
        this.vcfHeaderVersion = establishInitialHeaderVersion(metaData);

        //TODO: if the metaData thats passed in has no version metadata line, then this header
        //TODO: won't have one - should that be detected here and manually added if thats the case?
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
        } else if (compareTo > 0) {
            // TODO: This can cause failures in code that used to succeed (i.e. Picard LiftOverVcf tests fail
            // if they're not modified to remove the embedded version line) since we now retain ##fileformat
            // lines in the metadata list; the validation code recognizes and validates the embedded ##fileformat
            // lines against the new version, and throws if they conflict.
            //
            // We might want to add a removeHeaderLine method to VCFHeader so that consumers with this problem
            // such as LiftOverVcf can first manually remove the embedded fileformat line (currently you'd have
            // to create a new header to achieve that).
            if (VCFUtils.getVerboseVCFLogging()) {
                logger.warn(String.format("Changing VCFHeader version from %s to %s",
                        vcfHeaderVersion.getVersionString(),
                        newVCFVersion.getVersionString()));
            }
        }

        mMetaData.setVCFVersion(newVCFVersion);
        this.vcfHeaderVersion = newVCFVersion;
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
     * Obtain a VCFHeaderLine set containing only fileformat/version line for the requestedVersion
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
     * @return all of the VCF ID lines in their original file order, or an empty list if none were present
     */
    //TODO: Note that this returns VCFSimpleHeaderLine instead of VCFIDHeaderLine, since thats more useful
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

    //TODO: NOTE: since this returns all of the metadata lines, including the fileformat version line,
    // in sorted order, the fileformat line is almost certainly not the first line in the list.
    /**
     * Get the metadata associated with this header in sorted order.
     *
     * @return Metadata lines in sorted order (based on lexicographical sort of string encodings).
     */
    public Set<VCFHeaderLine> getMetaDataInSortedOrder() { return mMetaData.getMetaDataInSortedOrder(); }

    // TODO: Is it useful to retain this method ? It returns the first match for the given key. Should we
    // TODO: deprecate it (and add a new one that returns a Collection) or just change it to return a Collection ?
    // TODO: how does this find lines with the key OTHER:key
    /**
     * Get the VCFHeaderLine whose key equals key.  Returns null if no such line exists
     * @param key
     * @return
     */
    public VCFHeaderLine getMetaDataLine(final String key) { return mMetaData.getMetaDataLine(key); }

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

    // TODO: Is this useful ? It returns the first match for the given key, even though there
    // can be multiple lines with the same key should we deprecate this method (and leave it and
    // add the new one) or just change it to return a collection ?
    /**
     * @param key the of the requested other header line
     * @return the meta data line, or null if there is none
     */
    //TODO: deprecate this
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
     * Return a set of header lines resulting from merging the header lines from two or more headers. The
     * headers must be version-compatible as defined by {@link VCFHeaderVersion#versionsAreCompatible}.
     * @param headers
     * @param emitWarnings
     * @return
     * @throws IllegalStateException
     */
    //TODO: this should really return a merged HEADER (or at least the VCFMetaDataLines object that it creates)
    // and let VCFUtils.smartMergeHeader (which should now be deprecated, just extract the header lines from it;
    // will also need to add a VCFHeader(VCFMetaDataLines) constructor if we return VCFMetaDataLines
    //NOTE: These headers must be version >= 4.2 (older headers that are read in via AbstractVCFCodecs are
    // "repaired" and stamped as VCF4.2 when they're read in).
    //TODO: all headers must be v4.2or greater
    // result always has the highet version amongst the merged headers
    // merging can fail if some header lines from the older versions don't conform to the new version
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

                final VCFHeaderLine other = mergedMetaData.hasEquivalentHeaderLine(line);
                if (other != null && !line.equals(other) ) {
                    // TODO: NOTE: In order to be equal, structured header lines must have identical attributes
                    // and values, which is different from the previous implementation for some line types, like
                    // compound header lines.
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
