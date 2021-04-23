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

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.AsciiFeatureCodec;
import htsjdk.tribble.Feature;
import htsjdk.tribble.NameAwareCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.utils.ValidationUtils;
import htsjdk.utils.Utils;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static htsjdk.variant.vcf.VCFConstants.*;


public abstract class AbstractVCFCodec extends AsciiFeatureCodec<VariantContext> implements NameAwareCodec {
    protected final static Log logger = Log.getInstance(AbstractVCFCodec.class);

    public final static int MAX_ALLELE_SIZE_BEFORE_WARNING = (int)Math.pow(2, 20);

    protected final static int NUM_STANDARD_FIELDS = 8;  // INFO is the 8th

    // we have to store the list of strings that make up the header until they're needed
    protected VCFHeader header = null;
    protected VCFHeaderVersion version = null;

    private final static VCFTextTransformer percentEncodingTextTransformer = new VCFPercentEncodedTextTransformer();
    private final static VCFTextTransformer passThruTextTransformer = new VCFPassThruTextTransformer();
    //by default, we use the passThruTextTransformer (assume pre v4.3)
    private VCFTextTransformer vcfTextTransformer = passThruTextTransformer;

    // a mapping of the allele
    protected final Map<String, List<Allele>> alleleMap = new HashMap<>(3);

    // a key optimization -- we need a per thread string parts array, so we don't allocate a big array over and over
    // todo: make this thread safe?
    protected String[] parts = null;
    protected String[] genotypeParts = null;

    // for performance we cache the hashmap of filter encodings for quick lookup
    protected final HashMap<String, Set<String>> filterHash = new HashMap<>();

    // we store a name to give to each of the variant contexts we emit
    protected String name = "Unknown";

    protected int lineNo = 0;

    protected final Map<String, String> stringCache = new HashMap<>();

    protected boolean warnedAboutNoEqualsForNonFlag = false;

    /**
     * If true, then we'll magically fix up VCF headers on the fly when we read them in
     */
    protected boolean doOnTheFlyModifications = true;

    /**
     * If non-null, we will replace the sample name read from the VCF header with this sample name. This feature works
     * only for single-sample VCFs.
     */
    protected String remappedSampleName = null;

    protected AbstractVCFCodec() {
        super(VariantContext.class);
    }

    /**
     * Creates a LazyParser for a LazyGenotypesContext to use to decode
     * our genotypes only when necessary.  We do this instead of eagarly
     * decoding the genotypes just to turn around and reencode in the frequent
     * case where we don't actually want to manipulate the genotypes
     */
    class LazyVCFGenotypesParser implements LazyGenotypesContext.LazyParser {
        final List<Allele> alleles;
        final String contig;
        final int start;

        LazyVCFGenotypesParser(final List<Allele> alleles, final String contig, final int start) {
            this.alleles = alleles;
            this.contig = contig;
            this.start = start;
        }

        @Override
        public LazyGenotypesContext.LazyData parse(final Object data) {
            return createGenotypeMap((String) data, alleles, contig, start);
        }
    }

    /**
     * Return true if this codec can handle the target version
     * @param targetVersion
     * @return true if this codec can handle this version
     */
    public abstract boolean canDecodeVersion(final VCFHeaderVersion targetVersion);

    // TODO: Note: This method was lifted from duplicate methods in the codec subclasses.
    /**
     * Reads all of the header from the provided iterator, but reads no further.
     * @param lineIterator the line reader to take header lines from
     * @return The parsed header
     */
    @Override
    public Object readActualHeader(final LineIterator lineIterator) {
        final List<String> headerStrings = new ArrayList<>();

        // Extract one line and retrieve the file format and version, which must be the first line,
        // and then add it back into the headerLines.
        final VCFHeaderVersion fileFormatVersion = readFormatVersionLine(lineIterator);
        headerStrings.add(VCFHeader.METADATA_INDICATOR + fileFormatVersion.getVersionLine());

        // collect metadata lines until we hit the required header line, or a non-metadata line,
        // in which case throw since there was no header line
        // TODO: Optimization: There is no reason we couldn't just parse the header lines right here
        // instead of accumulating them in a list and then making another pass to convert them
        while (lineIterator.hasNext()) {
            final String line = lineIterator.peek();
            if (line.startsWith(VCFHeader.METADATA_INDICATOR)) {
                lineNo++;
                headerStrings.add(lineIterator.next());
            } else if (line.startsWith(VCFHeader.HEADER_INDICATOR)) {
                lineNo++;
                headerStrings.add(lineIterator.next());
                this.header = parseHeaderFromLines(headerStrings, fileFormatVersion);
                return this.header;
            }
        }
        throw new TribbleException.InvalidHeader(
                "The required header line (starting with one #) is missing in the input VCF file");
    }

    /**
     * Read ahead one line to obtain and return the vcf header version for this file
     *
     * @param headerLineIterator
     * @return VCFHeaderVersion for this file
     * @throws TribbleException if no file format header line is found in the first line or, the version can't
     * be handled by this codec
     */
    protected VCFHeaderVersion readFormatVersionLine(final LineIterator headerLineIterator) {
        if (headerLineIterator.hasNext()) {
            final String headerVersionLine = headerLineIterator.next();
            if (headerVersionLine.startsWith(VCFHeader.METADATA_INDICATOR)) {
                final VCFHeaderVersion vcfFileVersion = VCFHeaderVersion.getHeaderVersion(headerVersionLine);
                if (!canDecodeVersion(vcfFileVersion)) {
                    throw new TribbleException.InvalidHeader(
                            String.format("The \"(%s)\" codec does not support VCF version: %s", getName(), vcfFileVersion));
                } else {
                    return vcfFileVersion;
                }
            }
        }
        throw new TribbleException.InvalidHeader("The VCF version header line is missing");
    }

    /**
     * create a VCF header from a set of header record lines
     *
     * @param headerStrings a list of strings that represent all the ## and # entries
     * @return a VCFHeader object
     */
    protected VCFHeader parseHeaderFromLines( final List<String> headerStrings, final VCFHeaderVersion sourceVersion ) {
        this.version = sourceVersion;

        final Set<VCFHeaderLine> metaData = new LinkedHashSet<>();
        Set<String> sampleNames = new LinkedHashSet<>();
        int contigCounter = 0;

        for ( String headerLine : headerStrings ) {
            if ( !headerLine.startsWith(VCFHeader.METADATA_INDICATOR) ) {
                sampleNames = parsePrimaryHeaderLine(headerLine);
            } else {
                if ( headerLine.startsWith(VCFConstants.INFO_HEADER_START) ) {
                    metaData.add(getInfoHeaderLine(headerLine.substring(INFO_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.FILTER_HEADER_START) ) {
                    metaData.add(getFilterHeaderLine(headerLine.substring(FILTER_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.FORMAT_HEADER_START) ) {
                    metaData.add(getFormatHeaderLine(headerLine.substring(FORMAT_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.CONTIG_HEADER_START) ) {
                    metaData.add(getContigHeaderLine(headerLine.substring(CONTIG_HEADER_OFFSET), sourceVersion, contigCounter++));
                } else if ( headerLine.startsWith(VCFConstants.ALT_HEADER_START) ) {
                    metaData.add(getAltHeaderLine(headerLine.substring(ALT_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.PEDIGREE_HEADER_START) ) {
                    metaData.add(getPedigreeHeaderLine(headerLine.substring(PEDIGREE_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.META_HEADER_START) ) {
                    metaData.add(getMetaHeaderLine(headerLine.substring(META_HEADER_OFFSET), sourceVersion));
                } else if ( headerLine.startsWith(VCFConstants.SAMPLE_HEADER_START) ) {
                    metaData.add(getSampleHeaderLine(headerLine.substring(SAMPLE_HEADER_OFFSET), sourceVersion));
                } else {
                    VCFHeaderLine otherHeaderLine = getOtherHeaderLine(
                            headerLine.substring(VCFHeader.METADATA_INDICATOR.length()),
                            sourceVersion);
                    if (otherHeaderLine != null)
                        metaData.add(otherHeaderLine);
                }
            }
        }

        setVCFHeader(new VCFHeader(version, metaData, sampleNames), version);
        return this.header;
    }

    /**
     * Create and return a VCFInfoHeader object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFInfoHeaderLine object
     */
    public VCFInfoHeaderLine getInfoHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFInfoHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a VCFFormatHeader object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFFormatHeaderLine object
     */
    public VCFFormatHeaderLine getFormatHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFFormatHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a VCFFilterHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFFilterHeaderLine object
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFFilterHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a VCFContigHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be valid for this header version.
     * @return a VCFContigHeaderLine object
     */
    public VCFContigHeaderLine getContigHeaderLine(
            final String headerLineString,
            final VCFHeaderVersion sourceVersion,
            final int contigIndex) {
        return new VCFContigHeaderLine(headerLineString, sourceVersion, contigIndex);
    }

    /**
     * Create and return a VCFAltHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFAltHeaderLine object
     */
    public VCFAltHeaderLine getAltHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFAltHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a VCFPedigreeHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFPedigreeHeaderLine object
     *
     * NOTE:this can't return a VCFPedigreeHeaderLine since for pre-v4.3 PEDIGREE lines must be modeled as
     * VCFHeaderLine due to the lack of a requirement for an ID field
     */
    public VCFHeaderLine getPedigreeHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        if (sourceVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            return new VCFPedigreeHeaderLine(headerLineString, sourceVersion);
        } else {
            return new VCFHeaderLine(PEDIGREE_HEADER_KEY, headerLineString);
        }
    }

    /**
     * Create and return a VCFMetaHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFMetaHeaderLine object
     */
    public VCFMetaHeaderLine getMetaHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFMetaHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a VCFSampleHeaderLine object from a header line string that conforms to the {@code sourceVersion}
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion the VCF header version derived from which the source was retrieved. The resulting header
     *                      line object should be validate for this header version.
     * @return a VCFSampleHeaderLine object
     */
    public VCFSampleHeaderLine getSampleHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        return new VCFSampleHeaderLine(headerLineString, sourceVersion);
    }

    /**
     * Create and return a basic VCFHeaderLine.
     *
     * @param headerLineString VCF header line being parsed without the leading "##"
     * @param sourceVersion VCFHeaderVersion being parsed
     * @return a VCFHeaderLine
     */
    public VCFHeaderLine getOtherHeaderLine(final String headerLineString, final VCFHeaderVersion sourceVersion) {
        final int indexOfEquals = headerLineString.indexOf('=');
        if ( indexOfEquals < 1 ) { // must at least have "?="
            // TODO: NOTE: the old code silently dropped metadata lines with no "="; now we log, or throw for verbose logging
            if (VCFUtils.getStrictVCFVersionValidation()) {
                throw new TribbleException.InvalidHeader("Unrecognized metadata line type: " + headerLineString);
            }
            if (VCFUtils.getVerboseVCFLogging()) {
                // TODO: should this throw
                logger.warn("Dropping unrecognized metadata line type: " + headerLineString);
            }
            return null;
        } else {
            final String headerLineValue = headerLineString.substring(indexOfEquals + 1).trim();
            if (headerLineValue.startsWith("<") && headerLineValue.endsWith(">") &&
                sourceVersion.isAtLeastAsRecentAs((VCFHeaderVersion.VCF4_3))) {
                // Model all "other" header lines as VCFSimpleHeaderLine starting with 4.3, but
                // for pre-v4.3, use VCFHeaderLine. This is to accommodate older files that contain
                // lines with structured header line syntax ("<>" delimited) but which do not contain
                // an ID field. Starting with 4.3, this is prohibited by the spec since an ID is required,
                // but we need to be able to consume such lines in pre-v43 files.
                // i.e., GATK Funcotator uses v4.1 ClinVar test files with lines like:
                // "ID=<Description=\"ClinVar Variation ID\">", where the "ID" is the key and there is
                // no ID attribute
                return new VCFSimpleHeaderLine(
                        headerLineString.substring(0, indexOfEquals),
                        headerLineString.substring(indexOfEquals + 1),
                        sourceVersion);
            } else {
                return new VCFHeaderLine(headerLineString.substring(0, indexOfEquals), headerLineString.substring(indexOfEquals + 1));
            }
        }
    }

    // Parse the primary header line of the form:
    //
    // #CHROM  POS     ID      REF     ALT     QUAL    FILTER  INFO    FORMAT  ...
    //
    // The string passed in is the first non-metadata line we've seen, so it should conform.
    //
    private Set<String> parsePrimaryHeaderLine(final String headerLine) {
        final Set<String> sampleNames = new LinkedHashSet<>();

        final String[] columns = headerLine.substring(1).split(VCFConstants.FIELD_SEPARATOR);
        if ( columns.length < VCFHeader.HEADER_FIELDS.values().length ) {
            throw new TribbleException.InvalidHeader("not enough columns present in header line: " + headerLine);
        }

        int col = 0;
        for (VCFHeader.HEADER_FIELDS field : VCFHeader.HEADER_FIELDS.values()) {
            try {
                if (field != VCFHeader.HEADER_FIELDS.valueOf(columns[col])) {
                    throw new TribbleException.InvalidHeader("expected column headerLineID '" + field + "' but saw '" + columns[col] + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new TribbleException.InvalidHeader("column headerLineID '" + columns[col] + "' is not a legal column header headerLineID.");
            }
            col++;
        }

        boolean sawFormatTag = false;
        if ( col < columns.length ) {
            if ( !columns[col].equals("FORMAT") )
                throw new TribbleException.InvalidHeader("expected column headerLineID 'FORMAT' but  saw '" + columns[col] + "'");
            sawFormatTag = true;
            col++;
        }

        while ( col < columns.length ) {
            sampleNames.add(columns[col++]);
        }

        if ( sawFormatTag && sampleNames.isEmpty())
            throw new TribbleException.InvalidHeader("The FORMAT field was provided but there is no genotype/sample data");

        // If we're performing sample name remapping and there is exactly one sample specified in the header, replace
        // it with the remappedSampleName. Throw an error if there are 0 or multiple samples and remapping was requested
        // for this file.
        if ( remappedSampleName != null ) {
            // We currently only support on-the-fly sample name remapping for single-sample VCFs
            if ( sampleNames.isEmpty() || sampleNames.size() > 1 ) {
                throw new TribbleException(
                        String.format("Cannot remap sample headerLineID to %s because %s samples are specified in the VCF header, " +
                                        "and on-the-fly sample headerLineID remapping is only supported for single-sample VCFs",
                                remappedSampleName, sampleNames.isEmpty() ? "no" : "multiple"));
            }

            sampleNames.clear();
            sampleNames.add(remappedSampleName);
        }

        return sampleNames;
    }

    /**
     * @return the header that was either explicitly set on this codec, or read from the file. May be null.
     * The returned value should not be modified.
     */
    public VCFHeader getHeader() {
        return header;
    }

    /**
     * @return the version number that was either explicitly set on this codec, or read from the file. May be null.
     */
    public VCFHeaderVersion getVersion() {
        return version;
    }

    /**
	 * Explicitly set the VCFHeader on this codec. This will overwrite the header read from the file
	 * and the version state stored in this instance; conversely, reading the header from a file will
	 * overwrite whatever is set here.
     *
     * The returned header may not be identical to, or may even be a complete replacement for, the
     * input header argument, since the header lines may be "repaired" (i.e., rewritten) if
     * doOnTheFlyModifications is set.
	 */
	public VCFHeader setVCFHeader(final VCFHeader newHeader, final VCFHeaderVersion newVersion) {
	    Utils.nonNull(newHeader);
	    Utils.nonNull(newVersion);

        validateHeaderVersionTransition(newHeader, newVersion);

        // force header validation for the target version; hopefully this isn't actually
        // setting a new version on the header
        newHeader.setHeaderVersion(newVersion);
        this.vcfTextTransformer = getTextTransformerForVCFVersion(newVersion);

        if (this.doOnTheFlyModifications) {
            this.header = VCFStandardHeaderLines.repairStandardHeaderLines(newHeader);
        } else {
            this.header = newHeader;
        }
		this.version = newVersion;
        this.vcfTextTransformer = getTextTransformerForVCFVersion(newVersion);

		return this.header;
	}

    /**
     * the fast decode function
     * @param line the line of text for the record
     * @return a feature, (not guaranteed complete) that has the correct start and stop
     */
    public Feature decodeLoc(String line) {
        return decodeLine(line, false);
    }

    /**
     * decode the line into a feature (VariantContext)
     * @param line the line
     * @return a VariantContext
     */
    @Override
    public VariantContext decode(String line) {
        return decodeLine(line, true);
    }

    /**
     * Throw if new a version/header are not compatible with the existing version/header. Generally, any version
     * before v4.2 can be up-converted to v4.2, but not to v4.3. Once a header is established as v4.3, it cannot
     * can not be up or down converted, and it must remain at v4.3.
     * @param newHeader
     * @param newVersion
     * @throws TribbleException if the header conversion is not valid
     */
    private void validateHeaderVersionTransition(final VCFHeader newHeader, final VCFHeaderVersion newVersion) {
        ValidationUtils.nonNull(newHeader);
        ValidationUtils.nonNull(newVersion);

        // Check that we're not trying to transition from 4.3+ to before 4.3
        if (version != null &&
            version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3) &&
            !newVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            throw new TribbleException("VCF version " + version + " cannot be down converted to version " + newVersion);
        }

        // If this codec currently has no header (this happens when the header is being established for
        // the first time during file parsing), establish an initial header and version, and bypass
        // validation.
        if (newHeader.getVCFHeaderVersion() != null) {
            VCFHeader.validateHeaderTransition(header, newHeader.getVCFHeaderVersion());
        }
    }

    /**
     * For v4.3 up, attribute values can contain embedded percent-encoded characters which must be decoded
     * on read. Return a version-aware text transformer that can decode encoded text.
     * @param targetVersion the version for which a transformer is bing requested
     * @return a {@link VCFTextTransformer} suitable for the targetVersion
     */
    private VCFTextTransformer getTextTransformerForVCFVersion(final VCFHeaderVersion targetVersion) {
        return targetVersion != null && targetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3) ?
                percentEncodingTextTransformer :
                passThruTextTransformer;
    }

    private VariantContext decodeLine(final String line, final boolean includeGenotypes) {
        // the same line reader is not used for parsing the header and parsing lines, if we see a #, we've seen a header line
        if (line.startsWith(VCFHeader.HEADER_INDICATOR)) return null;

        // our header cannot be null, we need the genotype sample names and counts
        if (header == null) throw new TribbleException("VCF Header cannot be null when decoding a record");

        if (parts == null)
            parts = new String[Math.min(header.getColumnCount(), NUM_STANDARD_FIELDS+1)];

        final int nParts = ParsingUtils.split(line, parts, VCFConstants.FIELD_SEPARATOR_CHAR, true);

        // if we have don't have a header, or we have a header with no genotyping data check that we
        // have eight columns.  Otherwise check that we have nine (normal columns + genotyping data)
        if (( (header == null || !header.hasGenotypingData()) && nParts != NUM_STANDARD_FIELDS) ||
                (header != null && header.hasGenotypingData() && nParts != (NUM_STANDARD_FIELDS + 1)) )
            throw new TribbleException("Line " + lineNo + ": there aren't enough columns for line " + line + " (we expected " + (header == null ? NUM_STANDARD_FIELDS : NUM_STANDARD_FIELDS + 1) +
                    " tokens, and saw " + nParts + " )");

        return parseVCFLine(parts, includeGenotypes);
    }

    /**
     * parse out the VCF line
     *
     * @param parts the parts split up
     * @return a variant context object
     */
    private VariantContext parseVCFLine(final String[] parts, final boolean includeGenotypes) {
        VariantContextBuilder builder = new VariantContextBuilder();
        builder.source(getName());

        // increment the line count
        // TODO -- because of the way the engine utilizes Tribble, we can parse a line multiple times (especially when
        // TODO --   the first record is far along the contig) and the line counter can get out of sync
        lineNo++;

        // parse out the required fields
        final String chr = getCachedString(parts[0]);
        builder.chr(chr);
        int pos = -1;
        try {
            pos = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            generateException(parts[1] + " is not a valid start position in the VCF format");
        }
        builder.start(pos);

        if ( parts[2].isEmpty() )
            generateException("The VCF specification requires a valid ID field");
        else if ( parts[2].equals(VCFConstants.EMPTY_ID_FIELD) )
            builder.noID();
        else
            builder.id(parts[2]);

        final String ref = parts[3].toUpperCase();
        final String alts = parts[4];
        builder.log10PError(parseQual(parts[5]));

        final Set<String> filters = parseFilters(getCachedString(parts[6]));
        if ( filters != null ) {
            builder.filters(new HashSet<>(filters));
        }
        final Map<String, Object> attrs = parseInfo(parts[7]);
        builder.attributes(attrs);

        if ( attrs.containsKey(VCFConstants.END_KEY) ) {
            // update stop with the end key if provided
            try {
                builder.stop(Integer.parseInt(attrs.get(VCFConstants.END_KEY).toString()));
            } catch (NumberFormatException e) {
                generateException("the END value in the INFO field is not valid");
            }
        } else {
            builder.stop(pos + ref.length() - 1);
        }

        // get our alleles, filters, and setup an attribute map
        final List<Allele> alleles = parseAlleles(ref, alts, lineNo);
        builder.alleles(alleles);

        // do we have genotyping data
        if (parts.length > NUM_STANDARD_FIELDS && includeGenotypes) {
            final LazyGenotypesContext.LazyParser lazyParser = new LazyVCFGenotypesParser(alleles, chr, pos);
            final int nGenotypes = header.getNGenotypeSamples();
            LazyGenotypesContext lazy = new LazyGenotypesContext(lazyParser, parts[8], nGenotypes);

            // did we resort the sample names?  If so, we need to load the genotype data
            if ( !header.samplesWereAlreadySorted() )
                lazy.decode();

            builder.genotypesNoValidation(lazy);
        }

        VariantContext vc = null;
        try {
            vc = builder.make();
        } catch (Exception e) {
            generateException(e.getMessage());
        }

        return vc;
    }

    /**
     * get the name of this codec
     * @return our set name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * set the name of this codec
     * @param name new name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return a cached copy of the supplied string.
     *
     * @param str string
     * @return interned string
     */
    protected String getCachedString(String str) {
        String internedString = stringCache.get(str);
        if ( internedString == null ) {
            internedString = new String(str);
            stringCache.put(internedString, internedString);
        }
        return internedString;
    }

    // TODO: Note: This method was lifted from duplicate methods in the codec subclasses.
    /**
     * parse the filter string, first checking to see if we already have parsed it in a previous attempt
     * @param filterString the string to parse
     * @return a set of the filters applied
     */
    protected Set<String> parseFilters(final String filterString) {
        // null for unfiltered
        if ( filterString.equals(VCFConstants.UNFILTERED) )
            return null;

        if ( filterString.equals(VCFConstants.PASSES_FILTERS_v4) )
            return Collections.emptySet();
        if ( filterString.equals(VCFConstants.PASSES_FILTERS_v3) )
            generateException(VCFConstants.PASSES_FILTERS_v3 + " is an invalid filter headerLineID in vcf4", lineNo);
        if (filterString.isEmpty())
            generateException("The VCF specification requires a valid filter status: filter was " + filterString, lineNo);

        // do we have the filter string cached?
        if ( filterHash.containsKey(filterString) )
            return filterHash.get(filterString);

        // empty set for passes filters
        final Set<String> fFields = new HashSet<>();
        // otherwise we have to parse and cache the value
        if ( !filterString.contains(VCFConstants.FILTER_CODE_SEPARATOR) )
            fFields.add(filterString);
        else {
            // Variant context uses a Set to store these, so duplicates were getting dropped anyway
            // in previous versions. Warn for old version; throw for V43+.
            String[] filters = filterString.split(VCFConstants.FILTER_CODE_SEPARATOR);
            for (int i = 0; i < filters.length; i++) {
                if (!fFields.add(filters[i])) {
                    String message = String.format(
                            "Filters must be unique; filter field \"%s\" in the vicinity of " +
                                    "line %d has duplicate filters", filterString, lineNo);
                    reportDuplicateFilterIDs(message);
                }
            }
        }

        filterHash.put(filterString, Collections.unmodifiableSet(fFields));

        return fFields;
    }

    /**
     * parse out the info fields
     * @param infoField the fields
     * @return a mapping of keys to objects
     */
    protected Map<String, Object> parseInfo(String infoField) {
        Map<String, Object> attributes = new HashMap<>();

        if ( infoField.isEmpty() )
            generateException("The VCF specification requires a valid (non-zero length) info field");

        if ( !infoField.equals(VCFConstants.EMPTY_INFO_FIELD) ) {
            if ( infoField.indexOf('\t') != -1 ) {
                generateException("The VCF specification does not allow for tab characters in the INFO field. Offending field value was \"" + infoField + "\"");
            }

            List<String> infoFields = ParsingUtils.split(infoField, VCFConstants.INFO_FIELD_SEPARATOR_CHAR);
            for (int i = 0; i < infoFields.size(); i++) {
                String key;
                Object value;

                int eqI = infoFields.get(i).indexOf("=");
                if ( eqI != -1 ) {
                    key = infoFields.get(i).substring(0, eqI);
                    String valueString = infoFields.get(i).substring(eqI + 1);

                    // split on the INFO field separator
                    List<String> infoValueSplit = ParsingUtils.split(valueString, VCFConstants.INFO_FIELD_ARRAY_SEPARATOR_CHAR);
                    if ( infoValueSplit.size() == 1 ) {
                        value = vcfTextTransformer.decodeText(infoValueSplit.get(0));
                        final VCFInfoHeaderLine headerLine = header.getInfoHeaderLine(key);
                        if ( headerLine != null && headerLine.getType() == VCFHeaderLineType.Flag && value.equals("0") ) {
                            // deal with the case where a flag field has =0, such as DB=0, by skipping the add
                            continue;
                        }
                    } else {
                        value = vcfTextTransformer.decodeText(infoValueSplit);
                    }
                } else {
                    key = infoFields.get(i);
                    final VCFInfoHeaderLine headerLine = header.getInfoHeaderLine(key);
                    if ( headerLine != null && headerLine.getType() != VCFHeaderLineType.Flag ) {
                        if ( warnedAboutNoEqualsForNonFlag ) {
                            logger.warn("Found info key " + key + " without a = value, but the header says the field is of type "
                                               + headerLine.getType() + " but this construct is only value for FLAG type fields");
                            warnedAboutNoEqualsForNonFlag = true;
                        }

                        value = VCFConstants.MISSING_VALUE_v4;
                    } else {
                        value = true;
                    }
                }

                // this line ensures that key/value pairs that look like key=; are parsed correctly as MISSING
                if ( "".equals(value) ) value = VCFConstants.MISSING_VALUE_v4;

                if (attributes.containsKey(key)) {
                    reportDuplicateInfoKeyValue(key, infoField);
                }

                attributes.put(key, value);
            }
        }

        return attributes;
    }

    /**
     * Handle reporting of duplicate filter IDs
     * @param duplicateFilterMessage
     */
    protected abstract void reportDuplicateFilterIDs(final String duplicateFilterMessage);

    /**
     * Handle report of duplicate info line field values
     * @param key
     * @param infoLine
     */
    public abstract void reportDuplicateInfoKeyValue(final String key, final String infoLine);

    /**
     * create a an allele from an index and an array of alleles
     * @param index the index
     * @param alleles the alleles
     * @return an Allele
     */
    protected static Allele oneAllele(String index, List<Allele> alleles) {
        if ( index.equals(VCFConstants.EMPTY_ALLELE) )
            return Allele.NO_CALL;
        final int i;
        try {
            i = Integer.parseInt(index);
        } catch ( NumberFormatException e ) {
            throw new TribbleException.InternalCodecException("The following invalid GT allele index was encountered in the file: " + index);
        }
        if ( i >= alleles.size() )
            throw new TribbleException.InternalCodecException("The allele with index " + index + " is not defined in the REF/ALT columns in the record");
        return alleles.get(i);
    }


    /**
     * parse genotype alleles from the genotype string
     * @param GT         GT string
     * @param alleles    list of possible alleles
     * @param cache      cache of alleles for GT
     * @return the allele list for the GT string
     */
    protected static List<Allele> parseGenotypeAlleles(String GT, List<Allele> alleles, Map<String, List<Allele>> cache) {
        // cache results [since they are immutable] and return a single object for each genotype
        List<Allele> GTAlleles = cache.get(GT);

        if ( GTAlleles == null ) {
            StringTokenizer st = new StringTokenizer(GT, VCFConstants.PHASING_TOKENS);
            GTAlleles = new ArrayList<Allele>(st.countTokens());
            while ( st.hasMoreTokens() ) {
                String genotype = st.nextToken();
                GTAlleles.add(oneAllele(genotype, alleles));
            }
            cache.put(GT, GTAlleles);
        }

        return GTAlleles;
    }

    /**
     * parse out the qual value
     * @param qualString the quality string
     * @return return a double
     */
    protected static Double parseQual(String qualString) {
        // if we're the VCF 4 missing char, return immediately
        if ( qualString.equals(VCFConstants.MISSING_VALUE_v4))
            return VariantContext.NO_LOG10_PERROR;

        Double val = VCFUtils.parseVcfDouble(qualString);

        // check to see if they encoded the missing qual score in VCF 3 style, with either the -1 or -1.0.  check for val < 0 to save some CPU cycles
        if ((val < 0) && (Math.abs(val - VCFConstants.MISSING_QUALITY_v3_DOUBLE) < VCFConstants.VCF_ENCODING_EPSILON))
            return VariantContext.NO_LOG10_PERROR;

        // scale and return the value
        return val / -10.0;
    }

    /**
     * parse out the alleles
     * @param ref the reference base
     * @param alts a string of alternates to break into alleles
     * @param lineNo  the line number for this record
     * @return a list of alleles, and a pair of the shortest and longest sequence
     */
    protected static List<Allele> parseAlleles(String ref, String alts, int lineNo) {
        List<Allele> alleles = new ArrayList<Allele>(2); // we are almost always biallelic
        // ref
        checkAllele(ref, true, lineNo);
        Allele refAllele = Allele.create(ref, true);
        alleles.add(refAllele);

        if ( alts.indexOf(',') == -1 ) // only 1 alternatives, don't call string split
            parseSingleAltAllele(alleles, alts, lineNo);
        else
            for ( String alt : alts.split(",") )
                parseSingleAltAllele(alleles, alt, lineNo);

        return alleles;
    }

    /**
     * check to make sure the allele is an acceptable allele
     * @param allele the allele to check
     * @param isRef are we the reference allele?
     * @param lineNo  the line number for this record
     */
    private static void checkAllele(String allele, boolean isRef, int lineNo) {
        if ( allele == null || allele.isEmpty() )
            generateException(generateExceptionTextForBadAlleleBases(""), lineNo);

        if ( GeneralUtils.DEBUG_MODE_ENABLED && MAX_ALLELE_SIZE_BEFORE_WARNING != -1 && allele.length() > MAX_ALLELE_SIZE_BEFORE_WARNING ) {
            System.err.println(String.format("Allele detected with length %d exceeding max size %d at approximately line %d, likely resulting in degraded VCF processing performance", allele.length(), MAX_ALLELE_SIZE_BEFORE_WARNING, lineNo));
        }

        if (Allele.wouldBeSymbolicAllele(allele.getBytes())) {
            if ( isRef ) {
                generateException("Symbolic alleles not allowed as reference allele: " + allele, lineNo);
            }
        } else {
            // check for VCF3 insertions or deletions
            if ( (allele.charAt(0) == VCFConstants.DELETION_ALLELE_v3) || (allele.charAt(0) == VCFConstants.INSERTION_ALLELE_v3) )
                generateException("Insertions/Deletions are not supported when reading 3.x VCF's. Please" +
                        " convert your file to VCF4 using VCFTools, available at http://vcftools.sourceforge.net/index.html", lineNo);

            if (!Allele.acceptableAlleleBases(allele, isRef))
                generateException(generateExceptionTextForBadAlleleBases(allele), lineNo);

            if ( isRef && allele.equals(VCFConstants.EMPTY_ALLELE) )
                generateException("The reference allele cannot be missing", lineNo);
        }
    }

    /**
     * Generates the exception text for the case where the allele string contains unacceptable bases.
     *
     * @param allele   non-null allele string
     * @return non-null exception text string
     */
    private static String generateExceptionTextForBadAlleleBases(final String allele) {
        if ( allele.isEmpty() )
            return "empty alleles are not permitted in VCF records";
        if ( allele.contains("[") || allele.contains("]") || allele.contains(":") || allele.contains(".") )
            return "VCF support for complex rearrangements with breakends has not yet been implemented";
        return "unparsable vcf record with allele " + allele;
    }

    /**
     * parse a single allele, given the allele list
     * @param alleles the alleles available
     * @param alt the allele to parse
     * @param lineNo  the line number for this record
     */
    private static void parseSingleAltAllele(List<Allele> alleles, String alt, int lineNo) {
        checkAllele(alt, false, lineNo);

        Allele allele = Allele.create(alt, false);
        if ( ! allele.isNoCall() )
            alleles.add(allele);
    }

    // TODO: What is the intended meaning of a return value of true ? This class is abstract and can't
    // decode anything directly, but it will return true for ANY 4.x file when passed a string with
    // the prefix "##fileformat=VCFv4" (or worse, for any vcf file if passed "##fileformat=VCFv")
    public static boolean canDecodeFile(final String potentialInput, final String MAGIC_HEADER_LINE) {
        try {
            Path path = IOUtil.getPath(potentialInput);
            //isVCFStream closes the stream that's passed in
            return isVCFStream(Files.newInputStream(path), MAGIC_HEADER_LINE) ||
                    isVCFStream(new GZIPInputStream(Files.newInputStream(path)), MAGIC_HEADER_LINE) ||
                    isVCFStream(new BlockCompressedInputStream(Files.newInputStream(path)), MAGIC_HEADER_LINE);
        } catch ( FileNotFoundException e ) {
            return false;
        } catch ( IOException e ) {
            return false;
        }
    }

    private static boolean isVCFStream(final InputStream stream, final String MAGIC_HEADER_LINE) {
        try {
            byte[] buff = new byte[MAGIC_HEADER_LINE.length()];
            int nread = stream.read(buff, 0, MAGIC_HEADER_LINE.length());
            boolean eq = Arrays.equals(buff, MAGIC_HEADER_LINE.getBytes());
            return eq;
        } catch ( IOException e ) {
            return false;
        } catch ( RuntimeException e ) {
            return false;
        } finally {
            try { stream.close(); } catch ( IOException e ) {}
        }
    }


    /**
     * create a genotype map
     *
     * @param str the string
     * @param alleles the list of alleles
     * @return a mapping of sample name to genotype object
     */
    public LazyGenotypesContext.LazyData createGenotypeMap(final String str,
                                                              final List<Allele> alleles,
                                                              final String chr,
                                                              final int pos) {
        if (genotypeParts == null)
            genotypeParts = new String[header.getColumnCount() - NUM_STANDARD_FIELDS];

        int nParts = ParsingUtils.split(str, genotypeParts, VCFConstants.FIELD_SEPARATOR_CHAR);
        if ( nParts != genotypeParts.length )
            generateException("there are " + (nParts-1) + " genotypes while the header requires that " + (genotypeParts.length-1) + " genotypes be present for all records at " + chr + ":" + pos, lineNo);

        ArrayList<Genotype> genotypes = new ArrayList<Genotype>(nParts);

        // get the format keys
        List<String> genotypeKeys = ParsingUtils.split(genotypeParts[0], VCFConstants.GENOTYPE_FIELD_SEPARATOR_CHAR);

        // cycle through the sample names
        Iterator<String> sampleNameIterator = header.getGenotypeSamples().iterator();

        // clear out our allele mapping
        alleleMap.clear();

        // cycle through the genotype strings
        boolean PlIsSet = false;
        for (int genotypeOffset = 1; genotypeOffset < nParts; genotypeOffset++) {
            List<String> genotypeValues = ParsingUtils.split(genotypeParts[genotypeOffset], VCFConstants.GENOTYPE_FIELD_SEPARATOR_CHAR);
            genotypeValues = vcfTextTransformer.decodeText(genotypeValues);

            final String sampleName = sampleNameIterator.next();
            final GenotypeBuilder gb = new GenotypeBuilder(sampleName);

            // check to see if the value list is longer than the key list, which is a problem
            if (genotypeKeys.size() < genotypeValues.size())
                generateException("There are too many keys for the sample " + sampleName + ", keys = " + parts[8] + ", values = " + parts[genotypeOffset]);

            int genotypeAlleleLocation = -1;
            if (!genotypeKeys.isEmpty()) {
                gb.maxAttributes(genotypeKeys.size() - 1);

                for (int i = 0; i < genotypeKeys.size(); i++) {
                    final String gtKey = genotypeKeys.get(i);
                    boolean missing = i >= genotypeValues.size();

                    // todo -- all of these on the fly parsing of the missing value should be static constants
                    if (gtKey.equals(VCFConstants.GENOTYPE_KEY)) {
                        genotypeAlleleLocation = i;
                    } else if ( missing ) {
                        // if its truly missing (there no provided value) skip adding it to the attributes
                    } else if (gtKey.equals(VCFConstants.GENOTYPE_FILTER_KEY)) {
                        final Set<String> filters = parseFilters(getCachedString(genotypeValues.get(i)));
                        if ( filters != null ) gb.filters(new ArrayList<>(filters));
                    } else if ( genotypeValues.get(i).equals(VCFConstants.MISSING_VALUE_v4) ) {
                        // don't add missing values to the map
                    } else {
                        if (gtKey.equals(VCFConstants.GENOTYPE_QUALITY_KEY)) {
                            if ( genotypeValues.get(i).equals(VCFConstants.MISSING_GENOTYPE_QUALITY_v3) )
                                gb.noGQ();
                            else
                                gb.GQ((int)Math.round(VCFUtils.parseVcfDouble(genotypeValues.get(i))));
                        } else if (gtKey.equals(VCFConstants.GENOTYPE_ALLELE_DEPTHS)) {
                            gb.AD(decodeInts(genotypeValues.get(i)));
                        } else if (gtKey.equals(VCFConstants.GENOTYPE_PL_KEY)) {
                            gb.PL(decodeInts(genotypeValues.get(i)));
                            PlIsSet = true;
                        } else if (gtKey.equals(VCFConstants.GENOTYPE_LIKELIHOODS_KEY)) {
                            // Do not overwrite PL with data from GL
                            if (!PlIsSet) {
                                gb.PL(GenotypeLikelihoods.fromGLField(genotypeValues.get(i)).getAsPLs());
                            }
                        } else if (gtKey.equals(VCFConstants.DEPTH_KEY)) {
                            gb.DP(Integer.parseInt(genotypeValues.get(i)));
                        } else {
                            gb.attribute(gtKey, genotypeValues.get(i));
                        }
                    }
                }
            }

            // check to make sure we found a genotype field if our version is less than 4.1 file
            if ( ! version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_1) && genotypeAlleleLocation == -1 )
                generateException("Unable to find the GT field for the record; the GT field is required before VCF4.1");
            if ( genotypeAlleleLocation > 0 )
                generateException("Saw GT field at position " + genotypeAlleleLocation + ", but it must be at the first position for genotypes when present");

            final List<Allele> GTalleles = (genotypeAlleleLocation == -1 ? new ArrayList<Allele>(0) : parseGenotypeAlleles(genotypeValues.get(genotypeAlleleLocation), alleles, alleleMap));
            gb.alleles(GTalleles);
            gb.phased(genotypeAlleleLocation != -1 && genotypeValues.get(genotypeAlleleLocation).indexOf(VCFConstants.PHASED) != -1);

            // add it to the list
            try {
                genotypes.add(gb.make());
            } catch (TribbleException e) {
                throw new TribbleException.InternalCodecException(e.getMessage() + ", at position " + chr+":"+pos);
            }
        }

        return new LazyGenotypesContext.LazyData(genotypes, header.getSampleNamesInOrder(), header.getSampleNameToOffset());
    }

    private static final int[] decodeInts(final String string) {
        List<String> split = ParsingUtils.split(string, ',');
        int [] values = new int[split.size()];
        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = Integer.parseInt(split.get(i));
            }
        } catch (final NumberFormatException e) {
            return null;
        }
        return values;
    }

    /**
     * Forces all VCFCodecs to not perform any on the fly modifications to the VCF header
     * of VCF records.  Useful primarily for raw comparisons such as when comparing
     * raw VCF records
     */
    public final void disableOnTheFlyModifications() {
        doOnTheFlyModifications = false;
    }

    /**
     * Replaces the sample name read from the VCF header with the remappedSampleName. Works
     * only for single-sample VCFs -- attempting to perform sample name remapping for multi-sample
     * VCFs will produce an Exception.
     *
     * @param remappedSampleName replacement sample name for the sample specified in the VCF header
     */
    public void setRemappedSampleName( final String remappedSampleName ) {
        this.remappedSampleName = remappedSampleName;
    }

    protected void generateException(String message) {
        throw new TribbleException(String.format("Failure parsing VCF file at (approximately) line number %d: %s", lineNo, message));
    }

    protected static void generateException(String message, int lineNo) {
        throw new TribbleException(String.format("Failure parsing VCF file at (approximately) line number %d: %s", lineNo, message));
    }

    @Override
    public TabixFormat getTabixFormat() {
        return TabixFormat.VCF;
    }
}
