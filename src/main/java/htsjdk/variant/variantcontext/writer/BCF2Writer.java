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

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCF2Dictionary;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.BCF2FieldWriter.BCF2FieldWriter;
import htsjdk.variant.variantcontext.writer.BCF2FieldWriter.BCF2FieldWriterManager;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VariantContextWriter that emits BCF2 binary encoding
 * <p>
 * Overall structure of this writer is complex for efficiency reasons
 * <p>
 * -- The BCF2Writer manages the low-level BCF2 encoder, the mappings
 * from contigs and strings to offsets, the VCF header, and holds the
 * lower-level encoders that map from VC and Genotype fields to their
 * specific encoders.  This class also writes out the standard BCF2 fields
 * like POS, contig, the size of info and genotype data, QUAL, etc.  It
 * has loops over the INFO and GENOTYPES to encode each individual datum
 * with the generic field encoders, but the actual encoding work is
 * done with by the FieldWriters classes themselves.  The piece of code
 * that determines which FieldWriters to associate with each SITE and
 * GENOTYPE field is the BCF2FieldWriterManager.
 * <p>
 * -- BCF2FieldWriter are specialized classes for writing out SITE and
 * genotype information for specific SITE/GENOTYPE fields (like AC for
 * sites and GQ for genotypes).  These are objects in themselves because
 * they manage all of the complexity of relating the types in the VCF header
 * with the proper encoding in BCF as well as the type representing this
 * in java.  Relating all three of these pieces of information together
 * is the main complexity challenge in the encoder.  These classes are
 * responsible for extracting the necessary data from the VariantContext
 * or Genotype, determining its BCF type and size, and writing it out.
 * These FieldWriters are specialized for specific combinations of VCF type
 * and contexts for efficiency, so they smartly manage the writing of PLs
 * (encoded as int[]) directly into the lowest level BCFEncoder.
 * <p>
 * -- At the lowest level is the BCF2Encoder itself.  This provides
 * just the limited encoding methods specified by the BCF2 specification.  This encoder
 * doesn't do anything but make it possible to conveniently write out valid low-level
 * BCF2 constructs.
 *
 * @author Mark DePristo
 * @since 06/12
 */
class BCF2Writer extends IndexingVariantContextWriter {
    public static final int MAJOR_VERSION = 2;
    public static final int MINOR_VERSION = 2;

    public static final BCFVersion VERSION = new BCFVersion(MAJOR_VERSION, MINOR_VERSION);

    final private static boolean ALLOW_MISSING_CONTIG_LINES = false;

    private final OutputStream outputStream;      // Note: do not flush until completely done writing, to avoid issues with eventual BGZF support
    private VCFHeader header;
    private final Map<String, Integer> contigDictionary = new HashMap<>();
    private final Map<String, Integer> stringDictionaryMap = new HashMap<>();
    private final boolean doNotWriteGenotypes;
    private final Map<VariantContext, List<String>> genotypeKeys = new HashMap<>();
    private String[] sampleNames = null;

    private BCF2Encoder encoder; // initialized after the header arrives

    private BCF2FieldWriterManager fieldWriterManager2;

    /**
     * cached results for whether we can write out raw genotypes data.
     */
    private VCFHeader lastVCFHeaderOfUnparsedGenotypes = null;
    private boolean canPassOnUnparsedGenotypeDataForLastVCFHeader = false;

    // is the header or body written to the output stream?
    private boolean outputHasBeenWritten;


    public BCF2Writer(final File location, final OutputStream output, final SAMSequenceDictionary refDict,
                      final boolean enableOnTheFlyIndexing, final boolean doNotWriteGenotypes) {
        this(IOUtil.toPath(location), output, refDict, enableOnTheFlyIndexing, doNotWriteGenotypes);
    }

    public BCF2Writer(final Path location, final OutputStream output, final SAMSequenceDictionary refDict,
                      final boolean enableOnTheFlyIndexing, final boolean doNotWriteGenotypes) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing);
        this.outputStream = getOutputStream();
        this.doNotWriteGenotypes = doNotWriteGenotypes;
    }

    public BCF2Writer(final File location, final OutputStream output, final SAMSequenceDictionary refDict,
                      final IndexCreator indexCreator,
                      final boolean enableOnTheFlyIndexing, final boolean doNotWriteGenotypes) {
        this(IOUtil.toPath(location), output, refDict, indexCreator, enableOnTheFlyIndexing,
            doNotWriteGenotypes);
    }

    public BCF2Writer(final Path location, final OutputStream output, final SAMSequenceDictionary refDict,
                      final IndexCreator indexCreator,
                      final boolean enableOnTheFlyIndexing, final boolean doNotWriteGenotypes) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing, indexCreator);
        this.outputStream = getOutputStream();
        this.doNotWriteGenotypes = doNotWriteGenotypes;
    }

    // --------------------------------------------------------------------------------
    //
    // Interface functions
    //
    // --------------------------------------------------------------------------------

    @Override
    public void writeHeader(final VCFHeader header) {
        setHeader(header);

        try {
            // write out the header into a byte stream, get its length, and write everything to the file
            final ByteArrayOutputStream capture = new ByteArrayOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(capture);
            // Default to VCF 4.3, because our BCF version is 2.2.
            // If the header is incompatible with VCF 4.3, it will be written out as 4.2
            VCFWriter.writeHeader(this.header, writer, VCFHeader.DEFAULT_VCF_VERSION, "BCF2 stream");
            writer.append('\0'); // the header is null terminated by a byte
            writer.close();

            final byte[] headerBytes = capture.toByteArray();
            BCF2Writer.VERSION.write(outputStream);
            BCF2Type.INT32.write(headerBytes.length, outputStream);
            outputStream.write(headerBytes);
            outputHasBeenWritten = true;
        } catch (final IOException e) {
            throw new RuntimeIOException("BCF2 stream: Got IOException while trying to write BCF2 header", e);
        }
    }

    @Override
    public void add(VariantContext vc) {
        if (doNotWriteGenotypes)
            vc = new VariantContextBuilder(vc).noGenotypes().make();
        vc = vc.fullyDecode(header, false);

        super.add(vc); // allow on the fly indexing

        try {
            // Sites data
            buildSitesData(vc);
            final int sitesLength = encoder.getSize();

            // Genotypes data
            final int genotypesLength;
            final BCF2Codec.LazyData lazyData = getLazyData(vc);  // has critical side effects
            if (lazyData != null) {
                // we never decoded any data from this BCF file so we don't need to re-encode the samples data
                genotypesLength = lazyData.bytes.length;
            } else {
                buildSamplesData(vc);
                genotypesLength = encoder.getSize() - sitesLength;
            }

            // Write lengths
            BCF2Type.INT32.write(sitesLength, outputStream);
            BCF2Type.INT32.write(genotypesLength, outputStream);

            // Write the encoder's buffer into the output stream
            // If there was no lazy data, this also contains the genotypes data
            encoder.write(outputStream);
            if (lazyData != null) {
                // The encoder only contained sites data, so we need to write the lazy data
                outputStream.write(lazyData.bytes);
            }
            outputHasBeenWritten = true;
        } catch (final IOException e) {
            throw new RuntimeIOException("Error writing record to BCF2 file: " + vc.toString(), e);
        }
    }

    @Override
    public void close() {
        try {
            outputStream.flush();
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed to flush BCF2 file");
        }
        super.close();
    }

    @Override
    public void setHeader(final VCFHeader header) {
        if (outputHasBeenWritten) {
            throw new IllegalStateException("The header cannot be modified after the header or variants have been written to the output stream.");
        }

        // TODO we default to 2.2 here, is this alright?
        encoder = BCF2Encoder.getEncoder(BCF2Codec.ALLOWED_BCF_VERSION);

        // make sure the header is sorted correctly
        this.header = doNotWriteGenotypes
            ? new VCFHeader(header.getMetaDataInSortedOrder())
            : new VCFHeader(header.getMetaDataInSortedOrder(), header.getGenotypeSamples());

        // TODO should follow up on hts-specs and clarify the relationship between ##dictionary and IDX fields
        // Error on ##dictionary lines, we don't know what to do with them
        if (this.header.getMetaDataInInputOrder().stream().anyMatch(line -> line.getKey().equals("dictionary"))) {
            throw new TribbleException("Use of the ##dictionary line is not supported");
        }

        // create the config offsets map
        if (this.header.getContigLines().isEmpty()) {
            if (ALLOW_MISSING_CONTIG_LINES) {
                if (GeneralUtils.DEBUG_MODE_ENABLED) {
                    System.err.println("No contig dictionary found in header, falling back to reference sequence dictionary");
                }
                // The reference sequence dictionary should never contain IDX fields
                createContigDictionary(VCFUtils.makeContigHeaderLines(getRefDict(), null));
            } else {
                throw new IllegalStateException("Cannot write BCF2 file with missing contig lines");
            }
        } else {
            final BCF2Dictionary dict = BCF2Dictionary.makeBCF2ContigDictionary(header, BCF2Writer.VERSION);
            dict.forEach((offset, string) -> contigDictionary.put(string, offset));
        }

        // Create offset -> string map then turn inside-out
        final BCF2Dictionary dict = BCF2Dictionary.makeBCF2StringDictionary(this.header, BCF2Writer.VERSION);
        dict.forEach((offset, string) -> stringDictionaryMap.put(string, offset));

        sampleNames = this.header.getGenotypeSamples().toArray(new String[this.header.getNGenotypeSamples()]);
        // setup the field encodings
        fieldWriterManager2 = new BCF2FieldWriterManager(header, stringDictionaryMap, encoder);
    }

    // --------------------------------------------------------------------------------
    //
    // implicit block
    //
    // The first four records of BCF are inline untyped encoded data of:
    //
    // 4 byte integer chrom offset
    // 4 byte integer start
    // 4 byte integer ref length
    // 4 byte float qual
    //
    // --------------------------------------------------------------------------------
    private void buildSitesData(final VariantContext vc) throws IOException {
        final int contigIndex = contigDictionary.get(vc.getContig());
        if (contigIndex == -1)
            throw new IllegalStateException(String.format("Contig %s not found in sequence dictionary from reference", vc.getContig()));

        // note use of encodeRawInt to not insert the typing byte
        encoder.encodeRawInt(contigIndex, BCF2Type.INT32);

        // pos.  GATK is 1 based, BCF2 is 0 based
        encoder.encodeRawInt(vc.getStart() - 1, BCF2Type.INT32);

        // ref length.  GATK is closed, but BCF2 is open so the ref length is GATK end - GATK start + 1
        // for example, a SNP is in GATK at 1:10-10, which has ref length 10 - 10 + 1 = 1
        encoder.encodeRawInt(vc.getEnd() - vc.getStart() + 1, BCF2Type.INT32);

        // qual
        if (vc.hasLog10PError())
            encoder.encodeRawFloat((float) vc.getPhredScaledQual());
        else
            encoder.encodeRawMissingValue(BCF2Type.FLOAT);

        // info fields
        final int nAlleles = vc.getNAlleles();
        final int nInfo = vc.getAttributes().size();
        final int nGenotypeFormatFields = getNGenotypeFormatFields(vc);
        final int nSamples = header.getNGenotypeSamples();

        encoder.encodeRawInt((nAlleles << 16) | (nInfo & 0x0000FFFF), BCF2Type.INT32);
        encoder.encodeRawInt((nGenotypeFormatFields << 24) | (nSamples & 0x00FFFFF), BCF2Type.INT32);

        buildID(vc);
        buildAlleles(vc);
        buildFilter(vc);
        buildInfo(vc);
    }


    /**
     * Can we safely write on the raw (undecoded) genotypes of an input VC?
     * <p>
     * The cache depends on the undecoded lazy data header == lastVCFHeaderOfUnparsedGenotypes, in
     * which case we return the previous result.  If it's not cached, we use the BCF2Util to
     * compare the VC header with our header (expensive) and cache it.
     *
     * @param lazyData
     * @return
     */
    private boolean canSafelyWriteRawGenotypesBytes(final BCF2Codec.LazyData lazyData) {
        if (lazyData.header != lastVCFHeaderOfUnparsedGenotypes) {
            // result is already cached
            canPassOnUnparsedGenotypeDataForLastVCFHeader = BCF2Utils.headerLinesAreOrderedConsistently(this.header, lazyData.header);
            lastVCFHeaderOfUnparsedGenotypes = lazyData.header;
        }

        return canPassOnUnparsedGenotypeDataForLastVCFHeader;
    }

    private BCF2Codec.LazyData getLazyData(final VariantContext vc) {
        if (vc.getGenotypes().isLazyWithData()) {
            final LazyGenotypesContext lgc = (LazyGenotypesContext) vc.getGenotypes();

            if (lgc.getUnparsedGenotypeData() instanceof BCF2Codec.LazyData &&
                canSafelyWriteRawGenotypesBytes((BCF2Codec.LazyData) lgc.getUnparsedGenotypeData())) {
                return (BCF2Codec.LazyData) lgc.getUnparsedGenotypeData();
            } else {
                lgc.decode(); // WARNING -- required to avoid keeping around bad lazy data for too long
            }
        }

        return null;
    }

    /**
     * Try to get the nGenotypeFields as efficiently as possible.
     * <p>
     * If this is a lazy BCF2 object just grab the field count from there,
     * otherwise do the whole counting by types test in the actual data
     *
     * @param vc
     * @return
     */
    private int getNGenotypeFormatFields(final VariantContext vc) {
        final BCF2Codec.LazyData lazyData = getLazyData(vc);
        if (lazyData == null) {
            // Calculate genotype keys of a VariantContext and cache result
            // This computation can be expensive as it needs to inspect every genotype in the VC,
            // so we cache the result as it will be needed again when writing the genotype information
            return genotypeKeys.computeIfAbsent(vc, v -> v.calcVCFGenotypeKeys(header)).size();
        } else {
            return lazyData.nGenotypeFields;
        }
    }

    private void buildID(final VariantContext vc) throws IOException {
        encoder.encodeTypedString(vc.getID());
    }

    private void buildAlleles(final VariantContext vc) throws IOException {
        for (final Allele allele : vc.getAlleles()) {
            final byte[] s = allele.getDisplayBases();
            if (s == null)
                throw new IllegalStateException("BUG: BCF2Writer encountered null padded allele" + allele);
            encoder.encodeTypedString(s);
        }
    }

    private void buildFilter(final VariantContext vc) throws IOException {
        if (vc.isFiltered()) {
            encodeStringsByRef(vc.getFilters());
        } else if (vc.filtersWereApplied()) {
            // PASS is always implicitly encoded as 0
            encoder.encodeTypedInt(0, BCF2Type.INT8);
        } else {
            encoder.encodeTypedMissing(BCF2Type.INT8);
        }
    }

    private void buildInfo(final VariantContext vc) throws IOException {
        for (final String field : vc.getAttributes().keySet()) {
            final BCF2FieldWriter.SiteWriter writer = fieldWriterManager2.getInfoWriter(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "INFO");
            writer.start();
            writer.encode(vc);
        }
    }

    private void buildSamplesData(final VariantContext vc) throws IOException {
        // we have to do work to convert the VC into a BCF2 byte stream
        final List<String> genotypeFields = genotypeKeys.get(vc);
        for (final String field : genotypeFields) {
            final BCF2FieldWriter.GenotypeWriter writer = fieldWriterManager2.getFormatWriter(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "FORMAT");
            writer.start();
            writer.encode(vc, sampleNames);
        }
    }

    /**
     * Throws a meaningful error message when a field (INFO or FORMAT) is found when writing out a file
     * but there's no header line for it.
     *
     * @param vc
     * @param field
     * @param fieldType
     */
    private void errorUnexpectedFieldToWrite(final VariantContext vc, final String field, final String fieldType) {
        throw new IllegalStateException("Found field " + field + " in the " + fieldType + " fields of VariantContext at " +
            vc.getContig() + ":" + vc.getStart() + " from " + vc.getSource() + " but this hasn't been defined in the VCFHeader");
    }

    // --------------------------------------------------------------------------------
    //
    // Low-level block encoding
    //
    // --------------------------------------------------------------------------------

    private void encodeStringsByRef(final Collection<String> strings) throws IOException {
        final int[] offsets = new int[strings.size()];
        int i = 0;

        // Map strings to their position in string dictionary
        for (final String string : strings) {
            final Integer got = stringDictionaryMap.get(string);
            if (got == null)
                throw new IllegalStateException("Format error: could not find string " + string + " in header as required by BCF");
            offsets[i] = got;
            i++;
        }

        encoder.encodeTypedVecInt(offsets);
    }

    /**
     * Create the contigDictionary from the contigLines extracted from the VCF header
     *
     * @param contigLines
     */
    private void createContigDictionary(final Collection<VCFContigHeaderLine> contigLines) {
        int offset = 0;
        for (final VCFContigHeaderLine contig : contigLines)
            contigDictionary.put(contig.getID(), offset++);
    }
}
