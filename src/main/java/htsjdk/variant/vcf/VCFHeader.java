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
import htsjdk.utils.Utils;
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
 *  Any attempt to add metadata lines, or change the header version via {@link #setHeaderVersion} will
 *  trigger a validation pass against the metadata lines to ensure they conform to the rules defined by
 *  the VCF specification for that version.
 */
public class VCFHeader implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFHeader.class);
    public final static VCFHeaderVersion DEFAULT_VCF_VERSION = VCFHeaderVersion.VCF4_3;

    // the mandatory header fields
    public enum HEADER_FIELDS {
        CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
    }

    /**
     * The VCF version for this header; once a header version is established, it can only be
     * changed subject to version transition rules defined by
     * {@link #validateHeaderTransition(VCFHeader, VCFHeaderVersion)}
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
        this(null, Collections.<VCFHeaderLine>emptySet(), Collections.<String>emptySet());
    }

    /**
     * Create a VCF header, given a list of meta data and auxiliary tags
     *
     * @param metaData the meta data associated with this header
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData) {
        this(null, metaData, Collections.<String>emptySet());
    }

    /**
     * Creates a copy of the given VCFHeader, duplicating all it's metadata and
     * sample names.
     */
    public VCFHeader(final VCFHeader toCopy) {
        // TODO: this constructor doesn't propagate all existing state (writeEngineHeaders, etc)
        this(toCopy.getVCFHeaderVersion(), toCopy.getMetaDataInInputOrder(), toCopy.mGenotypeSampleNames);
    }

    /**
     * Create a VCF header, given a set of meta data and auxiliary tags
     *
     * @param metaData            set of meta data associated with this header
     * @param genotypeSampleNames the sample names
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData, final Set<String> genotypeSampleNames) {
        this(null, metaData, new ArrayList<>(genotypeSampleNames));
    }

    public VCFHeader(final Set<VCFHeaderLine> metaData, final List<String> genotypeSampleNames) {
        this(null, metaData, genotypeSampleNames);
    }

    /**
     * create a VCF header, given a set of meta data and auxiliary tags
     *
     * @param vcfHeaderVersion    vcfHeader version (against which the header lines will be validated)
     * @param metaData            set of meta data associated with this header
     * @param genotypeSampleNames the sample names
     */
    public VCFHeader(
            final VCFHeaderVersion vcfHeaderVersion,
            final Set<VCFHeaderLine> metaData,
            final Set<String> genotypeSampleNames) {
        this(vcfHeaderVersion, metaData, new ArrayList<>(genotypeSampleNames));
    }

    /**
     * Create a versioned VCF header.
     *
     * @param vcfHeaderVersion requested header version. The header version for this header. Can be null, in which
     *                         case the header version is determined by an embedded ##fileformat metadata line, if
     *                         any, or the default vcf version. If non null, the version defined by any embedded
     *                         ##fileformat lines has precedence.
     * @param metaData The metada lines for this header. The lines must be valid for the version for this header.
     * @param genotypeSampleNames
     */
    public VCFHeader(
            final VCFHeaderVersion vcfHeaderVersion,
            final Set<VCFHeaderLine> metaData,
            final List<String> genotypeSampleNames)
    {
        Utils.nonNull(metaData);
        Utils.nonNull(genotypeSampleNames);

        // Establish the version for this header using the following precedence:
        // 1) the version defined by any ##fileformat metadata line in the metadata list
        // 2) the requested version argument, if any (warn if this conflicts with the embedded fileformat)
        // 3) the default VCFHeaderVersion
        this.vcfHeaderVersion = establishHeaderVersion(vcfHeaderVersion, metaData);

        //TODO: if the metaData thats passed in has no version metadata line, then this header
        //TODO: won't have one - should that be detected here and manually added if thats the case?
        mMetaData.addAllMetaDataLines(metaData);
        mMetaData.validateMetaDataLines(this.vcfHeaderVersion);

        checkForDeprecatedGenotypeLikelihoodsKey();

        if ( genotypeSampleNames.size() != new HashSet<>(genotypeSampleNames).size() )
            throw new TribbleException.InvalidHeader("BUG: VCF header has duplicate sample names");

        mGenotypeSampleNames.addAll(genotypeSampleNames);
        samplesWereAlreadySorted = ParsingUtils.isSorted(genotypeSampleNames);
        buildVCFReaderMaps(genotypeSampleNames);
    }

    /**
     * Establish the header version for this header. If the header version has already been established
     * for this header, the new version will be subject to version transition validation.
     * @param vcfHeaderVersion
     * @throws TribbleException if the requested header version is not compatible with the existing version
     */
    public void setVCFHeaderVersion(final VCFHeaderVersion vcfHeaderVersion) {
        validateHeaderTransition(this, vcfHeaderVersion);
        this.vcfHeaderVersion = vcfHeaderVersion;
    }

    /**
     * Throw if {@code fromVersion} is not compatible with a {@code toVersion}. Generally, any version before
     * version 4.2 can be up-converted to version 4.2, but not to version 4.3. Once a header is established as
     * version 4.3, it cannot be up or down converted, and it must remain at version 4.3.
     * @param fromVersion current version. May be null, in which case {@code toVersion} can be any version
     * @param toVersion new version. Cannot be null.
     * @throws TribbleException if {@code fromVersion} is not compatible with {@code toVersion}
     */
    @Deprecated
    public static void validateVersionTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        ValidationUtils.nonNull(toVersion);

        final String errorMessageFormatString = "VCF cannot be automatically promoted from %s to %s";

        // fromVersion can be null, in which case anything goes (any transition from null is legal)
        if (fromVersion != null) {
            if (toVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
                if (!fromVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
                    // we're trying to go from pre-v4.3 to v4.3+
                    throw new TribbleException(String.format(errorMessageFormatString, fromVersion, toVersion));
                }

            } else if (fromVersion.equals(VCFHeaderVersion.VCF4_3)) {
                // we're trying to go from v4.3 to pre-v4.3
                throw new TribbleException(String.format(errorMessageFormatString, fromVersion, toVersion));
            }
        }
    }

    /**
     * Establish the header version using the following precedence:
     *   1) the version defined by any ##fileformat metadata line in the metadata list
     *   2) the requested version argument, if any (warn if this conflicts with the embedded fileformat)
     *   3) the default VCFHeaderVersion
     * @param requestedVCFHeaderVersion
     * @param metaData
     * @return vcfHeaderVersion to be used for the header
     */
    private VCFHeaderVersion establishHeaderVersion(
            final VCFHeaderVersion requestedVCFHeaderVersion,
            final Set<VCFHeaderLine> metaData)
    {
        VCFHeaderLine embeddedVersionLine = getVersionLineFromHeaderLineSet(metaData);
        if (embeddedVersionLine == null) {
            return requestedVCFHeaderVersion == null ?
                    DEFAULT_VCF_VERSION :
                    requestedVCFHeaderVersion;           // use the requested version
        } else { // embeddedVersionLine not null, reconcile with requested version
            VCFHeaderVersion embeddedHeaderVersion = VCFHeaderVersion.toHeaderVersion(embeddedVersionLine.getValue());
            if (requestedVCFHeaderVersion != null &&
                    !requestedVCFHeaderVersion.equals(embeddedHeaderVersion)) {
                final String message = String.format("VCFHeader metadata version (%s) is inconsistent with requested version (%s). " +
                                "Falling back to metadata version.",
                                embeddedHeaderVersion,
                                requestedVCFHeaderVersion);
                if (VCFUtils.getStrictVCFVersionValidation()) {
                    throw new IllegalArgumentException(message);
                }
                if (VCFUtils.getVerboseVCFLogging()) {
                    logger.warn(message);
                }
            }
            return embeddedHeaderVersion;
        }
    }

   /**
    * Get the header version for this header.
    * @return the VCFHeaderVersion for this header.
    *
    * Throw if {@code fromHeader} is not compatible with a {@code toVersion}. Generally, any version before
    * version 4.2 can be up-converted to version 4.2, but not to version 4.3.
    * If such a conversion is attempted, this method will validate that the header is compatible with 4.3
    * Once a header is established as version 4.3, it cannot be up or down converted, and it must remain at version 4.3.
    * @param fromHeader current version. May be null, in which case {@code toVersion} can be any version
    * @param toVersion new version. Cannot be null.
    * @throws TribbleException if {@code fromVersion} is not compatible with {@code toVersion}
    */
    public static void validateHeaderTransition(final VCFHeader fromHeader, final VCFHeaderVersion toVersion) {
        ValidationUtils.nonNull(toVersion);

        // fromHeader can be null, in which case anything goes (any transition from null is legal)
        if (fromHeader == null) return;
        final VCFHeaderVersion fromVersion = fromHeader.getVCFHeaderVersion();

        final String errorMessageFormatString = "VCF cannot be automatically promoted from %s to %s";

        if (toVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            // If fromHeader does not have a set version or is pre 4.3, validate
            if (fromVersion == null || !fromVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
                fromHeader.mMetaData.validateMetaDataLines(toVersion);
            }
        } else if (fromVersion != null && fromVersion.equals(VCFHeaderVersion.VCF4_3)) {
            // we're trying to go from v4.3 to pre-v4.3
            throw new TribbleException(String.format(errorMessageFormatString, fromVersion, toVersion));
        }
    }

    /**
     * @return the VCFHeaderVersion for this header. Can be null.
     */
    public VCFHeaderVersion getVCFHeaderVersion() {
        return vcfHeaderVersion;
    }

    // TODO this has a confusing name, as it does not actually touch the existing version
    //  Is the version of a VCFHeader meant to be immutable once it's set in the constructor?
    //  This seems to be what is intended and what is current implemented.
    //  Writers/mergers that want to perform version transition are responsible for either
    //  constructing new headers with the new versions or validating that a transition is possible
    //  then writing out the new version, ignoring the header's existing version
    /**
     * Set the version header for this class.
     *
     * Validates all metadata line to ensure they conform to the target header version (i.e, if the metadata lines
     * contain a ##fileformat line that specifies a version that is different than the {@code newVCFVersion}.
     *
     * @param newVCFVersion
     */
    public void setHeaderVersion(final VCFHeaderVersion newVCFVersion) {
        Utils.nonNull(newVCFVersion, "A non-null VCFHeaderVersion must be provided");
        if (!newVCFVersion.equals(vcfHeaderVersion)) {
            logger.warn(String.format("Changing VCFHeader version from %s to %s",
                    vcfHeaderVersion.getVersionString(),
                    newVCFVersion.getVersionString()));
            // TODO: This can cause failures in code that used to succeed (i.e. Picard LiftOverVcf tests fail
            // if they're not modified to remove the embedded version line) since we now retain ##fileformat
            // lines in the metadata list; the validation code recognizes and validates the embedded ##fileformat
            // lines against the new version, and throws if they conflict.
            //
            // We might want to add a removeHeaderLine method to VCFHeader so that consumers with this problem
            // such as LiftOverVcf can first manually remove the embedded fileformat line (currently you'd have
            // to create a new header to achieve that).
            mMetaData.validateMetaDataLines(newVCFVersion);
        }
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
                //TODO: should this throw, or log, or remove all but one (if the duplicates are consistent) ?
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
    // deprecate it (and add a new one that returns a Collection) or just change it to return a Collection ?
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
    public static Set<VCFHeaderLine> getMergedHeaderLines(final Collection<VCFHeader> headers, final boolean emitWarnings) {

        final VCFMetaDataLines mergedMetaData = new VCFMetaDataLines();
        final HeaderConflictWarner conflictWarner = new HeaderConflictWarner(emitWarnings);
        final Set<VCFHeaderVersion> vcfVersions = new HashSet<>(headers.size());

        for ( final VCFHeader source : headers ) {
            validateAllowedVersionMerger(vcfVersions, source.getVCFHeaderVersion());
            vcfVersions.add(source.getVCFHeaderVersion());
            for ( final VCFHeaderLine line : source.getMetaDataInSortedOrder()) {

                String key = line.getKey();
                if (VCFHeaderVersion.isFormatString(key)) {
                    continue; // drop file format strings
                }

                final VCFHeaderLine other = mergedMetaData.hasEquivalentHeaderLine(line);
                if (other != null && !line.equals(other) ) {
                    // TODO: NOTE: In order to be equal, structured header lines must have identical attributes
                    // and values, which is different from the previous implementation for some line types like
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
        // returning a LinkedHashSet so that ordering will be preserved. Ensures the contig lines do not get scrambled.
        return new LinkedHashSet<>(mergedMetaData.getMetaDataInInputOrder());
    }

    /**
     * Pairwise compare the new version we found with every other version we've seen so far and see if any
     * are mutually incompatible.
     *
     * @param sourceVersions
     * @param targetVersion
     */
    private static void validateAllowedVersionMerger(Set<VCFHeaderVersion> sourceVersions, VCFHeaderVersion targetVersion) {
        Utils.nonNull(sourceVersions);
        Utils.nonNull(targetVersion);

        // TODO this is too strict to allow merging v4.3 headers with pre v4.3
        Set<VCFHeaderVersion> incompatibleVersions = sourceVersions.stream()
                .filter(v -> !VCFHeaderVersion.versionsAreCompatible(v, targetVersion))
                .collect(Collectors.toSet());
        if (!incompatibleVersions.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "Attempt to merge a version %s header with incompatible vcf headers from versions:",
                    targetVersion.getVersionString()));
            sb.append(incompatibleVersions.stream()
                    .map(v -> v.getVersionString())
                    .collect(Collectors.joining(","))
            );
            //TODO: this is TribbleException to maintain compatibility with existing code and tests, but
            // should it be IllegalArgumentException or something else ?
            throw new TribbleException(sb.toString());
        }
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
