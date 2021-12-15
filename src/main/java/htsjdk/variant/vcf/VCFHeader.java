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

import htsjdk.beta.plugin.HtsHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContextComparator;
import htsjdk.variant.variantcontext.writer.VCFVersionUpgradePolicy;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to represent a VCF header.
 *
 * A VCFHeader has a "current" VCFHeaderVersion that is established when the header is constructed. If
 * metadata lines are provided to the constructor, a ##fileformat line must be included, and all lines
 * in that are provided must be valid for the specified version. If no metadata lines are initially
 * provided, the default version {@link VCFHeader#DEFAULT_VCF_VERSION} will be used.
 *
 * Each line in the list is always guaranteed to be valid for the current version, and any line added must
 * conform to the current version (as defined by the VCF specification). If a new line is added that fails to
 * validate against the current version, or a new line that changes the current version, and an existing line
 * in the list fails to validate against the new version, an exception will be thrown.
 *
 * Once a header version is established, it can be changed by adding a new file format/version line (see
 * {@link VCFHeader#makeHeaderVersionLine)} (the new version line will replace any existing line), but only
 * if the new version is newer than the previous version. Attempts to move the version to an older version
 * will result in an exception.
 */
public class VCFHeader implements HtsHeader, Serializable {
    public static final long serialVersionUID = 1L;
    protected static final Log logger = Log.getInstance(VCFHeader.class);
    public static final VCFHeaderVersion DEFAULT_VCF_VERSION = VCFHeaderVersion.VCF4_3;

    // the mandatory header fields
    public enum HEADER_FIELDS {
        CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
    }

    // header meta data
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
     * Create an empty VCF header with no header lines and no samples. Defaults to
     * VCF version {@link VCFHeader#DEFAULT_VCF_VERSION}.
     */
    public VCFHeader() {
        this(makeHeaderVersionLineSet(DEFAULT_VCF_VERSION), Collections.emptySet());
    }

    /**
     * Create a VCF header, given a list of meta data and auxiliary tags. The provided metadata
     * header line list MUST contain a version (fileformat) line in order to establish the version
     * for the header, and each metadata line must be valid for that version.
     *
     * @param metaData the meta data associated with this header
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines, or if any line does not conform to the established
     * version
     */
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
     * list MUST contain a version (fileformat) line in order to establish the version
     * for this header, and each metadata line must be valid for that version.
     *
     * @param metaData            set of meta data associated with this header
     * @param genotypeSampleNames the sample names
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines, or if any line does not conform to the established
     * version
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData, final Set<String> genotypeSampleNames) {
        this(metaData, new ArrayList<>(genotypeSampleNames));
    }

    /**
     * Create a versioned VCF header.
     *
     * @param metaData The metadata lines for this header.The provided metadata
     * header line list MUST contain a version (fileformat) line in order to establish the version
     * for this header, and each metadata line must be valid for that version.
     * @param genotypeSampleNames Sample names for this header.
     * @throws TribbleException if the provided header line metadata does not include a header line that
     * establishes the VCF version for the lines, or if any line does not conform to the established
     * version
     */
    public VCFHeader(final Set<VCFHeaderLine> metaData, final List<String> genotypeSampleNames) {
        ValidationUtils.nonNull(metaData);
        ValidationUtils.nonNull(genotypeSampleNames);

        // propagate the lines and establish the version for this header; note that if multiple version
        // lines are presented in the set, a warning will be issued, only the last one will be retained,
        // and the header version will be established using the last version line encountered
        mMetaData.addMetaDataLines(metaData);
        final VCFHeaderVersion vcfHeaderVersion = initializeHeaderVersion();
        mMetaData.validateMetaDataLinesOrThrow(vcfHeaderVersion);

        checkForDeprecatedGenotypeLikelihoodsKey();
        if ( genotypeSampleNames.size() != new HashSet<>(genotypeSampleNames).size() )
            throw new TribbleException.InvalidHeader("BUG: VCF header has duplicate sample names");

        mGenotypeSampleNames.addAll(genotypeSampleNames);
        samplesWereAlreadySorted = ParsingUtils.isSorted(genotypeSampleNames);
        buildVCFReaderMaps(genotypeSampleNames);
    }

   /**
    * Get the header version for this header.
    * @return the VCFHeaderVersion for this header. will not be null
    */
    public VCFHeaderVersion getVCFHeaderVersion() {
        return mMetaData.getVCFVersion();
    }

    /**
     * Adds a new line to the VCFHeader. If a duplicate line is already exists (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys), the new
     * line will replace the existing line.
     *
     * @param headerLine header line to attempt to add
     */
    public void addMetaDataLine(final VCFHeaderLine headerLine) {
        // propagate the new line to the metadata lines object, and if the version changed, validate
        // the lines against the new version
        final VCFHeaderVersion oldHeaderVersion = mMetaData.getVCFVersion();
        mMetaData.addMetaDataLine(headerLine);
        final VCFHeaderVersion newHeaderVersion = mMetaData.getVCFVersion();
        validateVersionTransition(headerLine, oldHeaderVersion, newHeaderVersion);

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
     * Return all contig line in SORTED order, where the sort order is determined by contig index.
     * Note that this behavior differs from other VCFHeader methods that return lines in input order.
     *
     * @return all of the VCF header lines of the ##contig form in SORTED order, or an empty list if none were present
     */
    public List<VCFContigHeaderLine> getContigLines() {
        // this must return lines in SORTED order
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
        // this must ensure that the lines used to create the dictionary are sorted by contig index
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
        getContigLines().forEach(hl -> mMetaData.removeMetaDataLine(hl));
        if (dictionary != null) {
            dictionary.getSequences().forEach(r -> addMetaDataLine(new VCFContigHeaderLine(r, r.getAssembly())));
        }
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
            logger.warn("Found " + VCFConstants.GENOTYPE_LIKELIHOODS_KEY + " format, but no "
                    + VCFConstants.GENOTYPE_PL_KEY + " field.  We now only manage PL fields internally"
                    + " automatically adding a corresponding PL field to your VCF header");
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
     * Deprecated. Use {@link #getMetaDataLines(String)}. see https://github.com/samtools/hts-specs/issues/602
     * 
     * @param key the key to use to find header lines to return
     * @return the header line with key "key", or null if none is present
     */
    @Deprecated // starting after version 2.24.1
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
     * Deprecated. Use {@link #getOtherHeaderLines(String)}. see https://github.com/samtools/hts-specs/issues/602
     *
     * @param key the of the requested header line
     * @return the meta data line, or null if there is none
     */
    @Deprecated // starting after version 2.24.1 this selects one from what can be many)
    public VCFHeaderLine getOtherHeaderLine(final String key) {
        final Collection<VCFHeaderLine> otherLines = mMetaData.getOtherHeaderLines();
        for (final VCFHeaderLine next: otherLines) {
            if (next.getKey().equals(key)) {
                // note that this returns the first match it finds, which is why this method is deprecated
                return next;
            }
        }
        return null;
    }

    /**
     * Returns all "other" VCFHeaderLines, in their original (input) order, where "other" means any
     * VCFHeaderLine that is not a contig, info, format or filter header line.
     */
    public Collection<VCFHeaderLine> getOtherHeaderLines() { return mMetaData.getOtherHeaderLines(); }

    /**
     * Returns "other" HeaderLines that have the key "key", in their original ordering, where "other"
     * means any VCFHeaderLine that is not a contig, info, format or filter header line.
     */
    public List<VCFHeaderLine> getOtherHeaderLines(final String key) {
        return mMetaData.getOtherHeaderLines().stream().filter(hl -> hl.getKey().equals(key)).collect(Collectors.toList());
    }

    /**
     * Adds a single "other" VCFHeaderLine that has key "key". Any lines with that key that already exist
     * in the header will be removed. This method can only be used to set unique non-structured (non-ID)
     * header lines.
     *
     * @param uniqueLine the unique line to add
     * @throws TribbleException if the line to be added is an ID line.
     */
    public void addOtherHeaderLineUnique(final VCFHeaderLine uniqueLine) {
        if (uniqueLine.isIDHeaderLine()) {
            throw new TribbleException(String.format("Only non-ID header lines can be added using this method: %s", uniqueLine));
        }
        getOtherHeaderLines(uniqueLine.getKey()).forEach(hl -> mMetaData.removeMetaDataLine(hl));
        addMetaDataLine(uniqueLine);
    }

    /**
     * Returns a single "other" VCFHeaderLine that has the key "key", where "other"
     * means any VCFHeaderLine that is not a contig, info, format or filter header line. If more than
     * one such line is available, throws a TribbleException.
     *
     * @param key the key to match
     * @return a single VCHeaderLine, or null if none
     * @throws TribbleException if more than one other line matches the key
     */
    public VCFHeaderLine getOtherHeaderLineUnique(final String key) {
        final List<VCFHeaderLine> lineList = getOtherHeaderLines(key);
        if (lineList.isEmpty()) {
            return null;
        } else if (lineList.size() > 1) {
            throw new TribbleException(
                    String.format(
                            "More than one \"other\" header line matches the key \"%s\" (%s). Use getOtherHeaderLines() to retrieve multiple lines:",
                            key,
                            lineList.stream().map(VCFHeaderLine::toString).collect(Collectors.joining(","))));
        } else {
            return lineList.get(0);
        }
    }

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
     * Obtain a valid fileformat/version line for the requestedVersion
     * @param requestedVersion the version for which a version line should be obtained
     * @return the version line
     */
    public static VCFHeaderLine makeHeaderVersionLine(final VCFHeaderVersion requestedVersion) {
        return new VCFHeaderLine(requestedVersion.getFormatString(), requestedVersion.getVersionString());
    }

    /**
     * Obtain a VCFHeaderLine set containing only a fileformat/version line for the requestedVersion
     * @param requestedVersion the version for which a version line should be obtained
     * @return a VCFHeaderLine set containing only fileformat/version line for the requestedVersion
     */
    public static Set<VCFHeaderLine> makeHeaderVersionLineSet(final VCFHeaderVersion requestedVersion) {
        return new LinkedHashSet<VCFHeaderLine>() {{ add(VCFHeader.makeHeaderVersionLine(requestedVersion)); }};
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final VCFHeader vcfHeader = (VCFHeader) o;

        if (samplesWereAlreadySorted != vcfHeader.samplesWereAlreadySorted) return false;
        if (writeEngineHeaders != vcfHeader.writeEngineHeaders) return false;
        if (writeCommandLine != vcfHeader.writeCommandLine) return false;
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
        int result = mMetaData.hashCode();
        result = 31 * result + (mGenotypeSampleNames != null ? mGenotypeSampleNames.hashCode() : 0);
        result = 31 * result + (samplesWereAlreadySorted ? 1 : 0);
        result = 31 * result + (sampleNamesInOrder != null ? sampleNamesInOrder.hashCode() : 0);
        result = 31 * result + (sampleNameToOffset != null ? sampleNameToOffset.hashCode() : 0);
        result = 31 * result + (writeEngineHeaders ? 1 : 0);
        result = 31 * result + (writeCommandLine ? 1 : 0);
        return result;
    }

    /**
     * Establish the version for this header using the (required) ##fileformat metadata line in the metadata list.
     * @throws TribbleException if no ##fileformat line is included in the metadata lines
     */
    private VCFHeaderVersion initializeHeaderVersion() {
        final VCFHeaderVersion metaDataVersion = mMetaData.getVCFVersion();
        if (metaDataVersion == null) {
            //we dont relax this even if VCFUtils.getStrictVCFVersionValidation() == false, since that
            //would confound subsequent header version management
            throw new TribbleException("The VCFHeader metadata must include a ##fileformat (version) header line");
        }
        return metaDataVersion;
    }

    public Collection<VCFValidationFailure<VCFHeaderLine>> getValidationErrors(final VCFHeaderVersion targetVersion) {
        return mMetaData.getValidationErrors(targetVersion);
    }

    private void validateVersionTransition(
            final VCFHeaderLine newHeaderLine,
            final VCFHeaderVersion currentVersion,
            final VCFHeaderVersion newVersion) {
        final int compareTo = newVersion.compareTo(currentVersion);

        // We only allow going forward to a newer version, not backwards to an older one, since there
        // is really no way to validate old header lines (pre vcfV4.2). If the version moved forward,
        // revalidate all the lines, otherwise only validate the new header line.
        if (compareTo < 0) {
            throw new TribbleException(String.format(
                    "When changing a header version, the new header version %s must be > the previous version %s",
                    newVersion,
                    currentVersion));
        } else if (compareTo > 0) {
            logger.debug(() -> String.format("Updating VCFHeader version from %s to %s",
                    currentVersion.getVersionString(),
                    newVersion.getVersionString()));

            // the version moved forward, so validate ALL of the existing lines in the list to ensure
            // that the transition is valid
            mMetaData.validateMetaDataLinesOrThrow(newVersion);
        } else {
            newHeaderLine.validateForVersionOrThrow(newVersion);
        }
    }

    /**
     * Attempt to upgrade this header based on the given {@link VCFVersionUpgradePolicy}. If no version upgrade
     * is performed based on the given policy (e.g. the header is already at the latest version, or the policy
     * DO_NOT_UPGRADE is requested), then the existing header is returned, otherwise a newly created header is returned.
     * @param policy the {@link VCFVersionUpgradePolicy} to use to upgrade this header
     * @return the current header if no upgrade is performed, otherwise a new header
     */
    public VCFHeader upgradeVersion(final VCFVersionUpgradePolicy policy) {
        switch (policy) {
            case DO_NOT_UPGRADE:
                return this;
            case UPGRADE_OR_FALLBACK: {
                final Collection<VCFValidationFailure<VCFHeaderLine>> errors =
                    this.mMetaData.getValidationErrors(VCFHeader.DEFAULT_VCF_VERSION);
                if (errors.isEmpty()) {
                    final VCFHeader newHeader = new VCFHeader(this);
                    // If validation fails, simply pass the exception through
                    newHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
                    return newHeader;
                } else {
                    logger.info("Header will be kept at original version: " + this.getVCFHeaderVersion()
                        + VCFValidationFailure.createVersionTransitionErrorMessage(errors, this.getVCFHeaderVersion())
                    );
                    return this;
                }
            }
            case UPGRADE_OR_FAIL: {
                final VCFHeader newHeader = new VCFHeader(this);
                // If validation fails, simply pass the exception through
                newHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
                return newHeader;
            }
            default:
                throw new TribbleException("Unrecognized VCF version transition policy: " + policy);
        }
    }

}
