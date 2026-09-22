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

import htsjdk.index.BinningIndex;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.bcf2.BCFDictionary;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFEncoder;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFSimpleHeaderLine;
import htsjdk.variant.vcf.VCFUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes BCF2 records to an output stream. The writer is framing-agnostic: it writes to whatever stream it is given.
 * A caller constructing it directly over a raw stream gets raw BCF at the chosen version (2.2 by default, 2.1 on
 * request); {@link VariantContextWriterBuilder} wraps the stream in BGZF for 2.2.
 *
 * <p>The encoder is layered for efficiency. {@code BCF2Writer} manages the dictionaries, the VCF header and the
 * standard per-record fields (CHROM, POS, REF length, QUAL, allele and filter lists). INFO and FORMAT fields are
 * encoded by {@link BCF2FieldWriter} instances, each backed by a {@link BCF2FieldEncoder} that knows the BCF2 type
 * for its field. At the bottom, {@link BCF2Encoder} writes the raw bytes the BCF2 specification defines.
 *
 * @author Mark DePristo
 * @since 06/12
 */
public class BCF2Writer extends IndexingVariantContextWriter {
    public static final int MAJOR_VERSION = 2;

    /** @deprecated the BCF version is now set at construction time via the builder */
    @Deprecated
    public static final int MINOR_VERSION = 1;

    private static final boolean ALLOW_MISSING_CONTIG_LINES = false;

    private final OutputStream
            outputStream; // Note: do not flush until completely done writing, to avoid issues with eventual BGZF
    // support
    private VCFHeader header;
    private BCFDictionary idDictionary;
    private BCFDictionary contigDictionary;
    private final boolean doNotWriteGenotypes;
    private String[] sampleNames = null;

    private final BCF2Encoder encoder = new BCF2Encoder(); // initialized after the header arrives
    final BCF2FieldWriterManager fieldManager = new BCF2FieldWriterManager();

    // is the header or body written to the output stream?
    private boolean outputHasBeenWritten;

    // The VCF version the caller asked for, or null to take the header's; resolved when the header is set
    private final VCFHeaderVersion explicitVersion;
    private VCFHeaderVersion outputVersion;
    private boolean outputIs45Plus;
    private boolean outputHasLaaFormat;

    // The BCF container version (2.1 or 2.2); resolved at construction time
    private final BCFVersion bcfVersion;

    // CSI index on the fly: set by the builder for BCF 2.2; null for raw BCF, streams, or no indexing
    private final BlockCompressedOutputStream bgzfStream;
    private final Path csiIndexPath;
    private BinningIndex.Builder csiIndexBuilder;
    private boolean anyRecordAdded;
    private boolean csiFailed;
    private int prevRefIdx = -1;
    private int prevStart = -1;

    /** The maximum number of samples that fit in the 24-bit n_sample field of a BCF record header. */
    public static final int MAX_SAMPLES = 0x00FFFFFF;

    private static BCFVersion requireSupportedVersion(final BCFVersion version) {
        if (version.getMajorVersion() != 2 || version.getMinorVersion() < 1 || version.getMinorVersion() > 2) {
            throw new IllegalArgumentException("Only BCF 2.1 and BCF 2.2 are supported, not " + version);
        }
        return version;
    }

    /**
     * Throws if the sample count exceeds the 24-bit BCF record header limit.
     *
     * @param nSamples the number of samples in the header
     * @throws IllegalArgumentException if nSamples exceeds {@link #MAX_SAMPLES}
     */
    public static void requireSampleCountInRange(final int nSamples) {
        if (nSamples > MAX_SAMPLES) {
            throw new IllegalArgumentException(
                    "The header declares " + nSamples + " samples, which exceeds the BCF limit of " + MAX_SAMPLES);
        }
    }

    public BCF2Writer(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final VCFHeaderVersion explicitVersion) {
        this(location, output, refDict, enableOnTheFlyIndexing, doNotWriteGenotypes, explicitVersion, null);
    }

    public BCF2Writer(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final VCFHeaderVersion explicitVersion,
            final BCFVersion bcfVersion) {
        this(location, output, refDict, enableOnTheFlyIndexing, doNotWriteGenotypes, explicitVersion, bcfVersion, null);
    }

    /**
     * Package-private constructor used by the builder to enable CSI indexing on BGZF BCF. When {@code csiIndexPath}
     * is non-null, the writer produces a bare CSI index alongside the BGZF BCF file; the Tribble indexer is not used.
     */
    BCF2Writer(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final VCFHeaderVersion explicitVersion,
            final BCFVersion bcfVersion,
            final Path csiIndexPath) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing);
        if (enableOnTheFlyIndexing && csiIndexPath == null && output instanceof BlockCompressedOutputStream) {
            throw new IllegalArgumentException("On-the-fly Tribble indexing cannot address a BGZF-compressed stream."
                    + " Use the VariantContextWriterBuilder, which produces a CSI index for BGZF BCF.");
        }
        this.outputStream = getOutputStream();
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.explicitVersion = explicitVersion;
        this.bcfVersion = requireSupportedVersion(bcfVersion != null ? bcfVersion : BCFVersion.BCF_2_2);
        this.csiIndexPath = csiIndexPath;
        if (csiIndexPath != null) {
            if (!(output instanceof BlockCompressedOutputStream)) {
                throw new IllegalArgumentException("CSI indexing requires a BlockCompressedOutputStream, but got "
                        + output.getClass().getName());
            }
            this.bgzfStream = (BlockCompressedOutputStream) output;
        } else {
            this.bgzfStream = null;
        }
    }

    public BCF2Writer(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final IndexCreator indexCreator,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final VCFHeaderVersion explicitVersion) {
        this(
                location,
                output,
                refDict,
                indexCreator,
                enableOnTheFlyIndexing,
                doNotWriteGenotypes,
                explicitVersion,
                null);
    }

    public BCF2Writer(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final IndexCreator indexCreator,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final VCFHeaderVersion explicitVersion,
            final BCFVersion bcfVersion) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing, indexCreator);
        this.outputStream = getOutputStream();
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.explicitVersion = explicitVersion;
        this.bcfVersion = requireSupportedVersion(bcfVersion != null ? bcfVersion : BCFVersion.BCF_2_2);
        this.csiIndexPath = null;
        this.bgzfStream = null;
    }

    // --------------------------------------------------------------------------------
    //
    // Interface functions
    //
    // --------------------------------------------------------------------------------

    @Override
    public void writeHeader(VCFHeader header) {
        // Delete any stale CSI from a previous run before anything can fail, so a rejected header or a failed
        // write never leaves an old index beside the new, already truncated BCF
        if (csiIndexPath != null) {
            try {
                Files.deleteIfExists(csiIndexPath);
            } catch (final IOException e) {
                throw new RuntimeIOException("Could not delete stale CSI index at " + csiIndexPath, e);
            }
        }

        setHeader(header);

        try {
            // Build a local header copy with IDX attributes matching the dictionary
            final VCFHeader headerWithIdx = headerWithIdxAttributes(this.header, idDictionary, contigDictionary);

            // write out the header into a byte stream, get its length, and write everything to the file
            final ByteArrayOutputStream capture = new ByteArrayOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(capture, VCFEncoder.VCF_CHARSET);
            // the embedded header text carries the VCF version, not the BCF one
            VCFWriter.writeHeader(headerWithIdx, writer, VCFWriter.makeVersionLine(outputVersion), "BCF2 stream");
            writer.append('\0'); // the header is null terminated by a byte
            writer.close();

            final byte[] headerBytes = capture.toByteArray();
            bcfVersion.write(outputStream);
            BCF2Type.INT32.write(headerBytes.length, outputStream);
            outputStream.write(headerBytes);
            outputHasBeenWritten = true;
        } catch (IOException e) {
            throw new RuntimeIOException("BCF2 stream: Got IOException while trying to write BCF2 header", e);
        }
    }

    /**
     * Builds a local copy of the header with IDX= attributes set from the dictionaries, so the embedded header
     * text carries the dictionary indices, as htslib writes them. The caller's header is untouched.
     */
    private static VCFHeader headerWithIdxAttributes(
            final VCFHeader header, final BCFDictionary idDict, final BCFDictionary contigDict) {
        final Set<VCFHeaderLine> newLines = new LinkedHashSet<>();
        for (final VCFHeaderLine line : header.getMetaDataInSortedOrder()) {
            if (line instanceof VCFContigHeaderLine) {
                final VCFContigHeaderLine contigLine = (VCFContigHeaderLine) line;
                final int idx = contigDict.getIndex(contigLine.getID());
                // Rebuild the contig line from its mapping with IDX added
                final Map<String, String> mapping = new LinkedHashMap<>(contigLine.getGenericFields());
                mapping.put(BCFDictionary.IDX_ATTRIBUTE, String.valueOf(idx));
                newLines.add(new VCFContigHeaderLine(mapping, contigLine.getContigIndex()));
            } else if (line instanceof VCFCompoundHeaderLine) {
                final VCFCompoundHeaderLine compound = (VCFCompoundHeaderLine) line;
                final int idx = idDict.getIndex(compound.getID());
                newLines.add(compound.withGenericFieldValue(BCFDictionary.IDX_ATTRIBUTE, String.valueOf(idx)));
            } else if (line instanceof VCFSimpleHeaderLine && line.shouldBeAddedToDictionary()) {
                final VCFSimpleHeaderLine simple = (VCFSimpleHeaderLine) line;
                final int idx = idDict.getIndex(simple.getID());
                newLines.add(simple.withGenericFieldValue(BCFDictionary.IDX_ATTRIBUTE, String.valueOf(idx)));
            } else {
                newLines.add(line);
            }
        }
        if (header.getGenotypeSamples().isEmpty()) {
            return new VCFHeader(newLines);
        } else {
            return new VCFHeader(newLines, header.getGenotypeSamples());
        }
    }

    @Override
    public void add(VariantContext vc) {
        if (doNotWriteGenotypes)
            vc = new VariantContextBuilder(vc).noGenotypes().make();
        vc = vc.fullyDecode(header, false);

        try {
            final byte[] infoBlock;
            final byte[] genotypesBlock;
            try {
                infoBlock = buildSitesData(vc);
                genotypesBlock = buildSamplesData(vc);
            } catch (final RuntimeException e) {
                // A record can be refused part way through, and a caller may carry on with the next one: fetching
                // the encoder's bytes empties it, so that what was encoded of this one does not lead the next.
                encoder.getRecordBytes();
                throw e;
            }

            // only now, so that a refused record is not indexed; still before any of its bytes reach the output
            super.add(vc);

            if (csiIndexBuilder != null) {
                final int refIdx = contigDictionary.getIndex(vc.getContig());
                // Validate sort order before writing, so the BCF and CSI never disagree
                if (refIdx < prevRefIdx || (refIdx == prevRefIdx && vc.getStart() < prevStart)) {
                    csiFailed = true;
                    throw new IllegalArgumentException(
                            "Records are not coordinate-sorted: " + vc.getContig() + ":" + vc.getStart()
                                    + " follows a record at reference index " + prevRefIdx + " position " + prevStart);
                }
                final long chunkStart = bgzfStream.getFilePointer();
                try {
                    writeBlock(infoBlock, genotypesBlock);
                } catch (final IOException e) {
                    csiFailed = true;
                    throw e;
                }
                final long chunkEnd = bgzfStream.getFilePointer();
                try {
                    csiIndexBuilder.add(refIdx, vc.getStart(), vc.getEnd(), chunkStart, chunkEnd);
                } catch (final RuntimeException e) {
                    csiFailed = true;
                    throw e;
                }
                prevRefIdx = refIdx;
                prevStart = vc.getStart();
                anyRecordAdded = true;
            } else {
                writeBlock(infoBlock, genotypesBlock);
            }
            outputHasBeenWritten = true;
        } catch (IOException e) {
            throw new RuntimeIOException("Error writing record to BCF2 file: " + vc.toString(), e);
        }
    }

    @Override
    public void close() {
        long endOfRecords = 0;
        if (bgzfStream != null && csiIndexBuilder != null) {
            try {
                bgzfStream.flush();
                endOfRecords = bgzfStream.getFilePointer();
                if (anyRecordAdded) {
                    csiIndexBuilder.moveEndOfLastRecord(endOfRecords);
                }
            } catch (final IOException e) {
                csiFailed = true;
                try {
                    super.close();
                } catch (final RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw new RuntimeIOException("Failed to flush BCF2 file before writing CSI index", e);
            }
        } else {
            try {
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeIOException("Failed to flush BCF2 file");
            }
        }
        super.close(); // closes the output stream (writes BGZF EOF block)
        // No CSI unless the header reached the file: a header that failed to write leaves nothing to index
        if (csiIndexBuilder != null && !csiFailed && outputHasBeenWritten) {
            final int nRefs = contigDictionary.size();
            final BinningIndex index = csiIndexBuilder.build(nRefs);
            try (BinaryCodec codec = new BinaryCodec(
                    new BlockCompressedOutputStream(Files.newOutputStream(csiIndexPath), (Path) null))) {
                index.writeCsi(codec, new byte[0]);
            } catch (IOException e) {
                throw new RuntimeIOException("Error writing CSI index to " + csiIndexPath, e);
            }
        }
    }

    @Override
    public void setHeader(final VCFHeader header) {
        if (outputHasBeenWritten) {
            throw new IllegalStateException(
                    "The header cannot be modified after the header or variants have been written to the output stream.");
        }
        // make sure the header is sorted correctly
        this.header = doNotWriteGenotypes
                ? new VCFHeader(header.getMetaDataInSortedOrder())
                : new VCFHeader(header.getMetaDataInSortedOrder(), header.getGenotypeSamples());
        // the writer's own copy carries the output version; the caller's header keeps whatever it declares
        this.outputVersion = VCFWriter.resolveOutputVersion(header, explicitVersion);
        this.header.setVCFHeaderVersion(this.outputVersion);
        this.outputIs45Plus = this.outputVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_5);
        this.outputHasLaaFormat = this.header.hasFormatLine(VCFConstants.LAA_KEY);
        VCFWriter.checkHeaderCompatibility(this.header, this.outputVersion);
        requireSampleCountInRange(this.header.getNGenotypeSamples());

        // BCF 2.1 with >= 4.3 header is an error: percent-encoding differs and old readers cannot handle it
        if (bcfVersion.getMinorVersion() <= 1 && outputVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            throw new IllegalStateException(
                    "BCF 2.1 cannot express the header's VCF " + outputVersion.getVersionString()
                            + ": BCF 2.1 was last specified alongside VCF 4.2. Call"
                            + " setBCFVersion(BCFVersion.BCF_2_2) or use setVCFVersion to lower the VCF version.");
        }

        // Build the dictionaries using BCFDictionary (honours IDX from header lines)
        idDictionary = BCFDictionary.forIDs(this.header);
        final Map<String, Integer> stringDictionaryMap = idDictionary.asMap();

        if (this.header.getContigLines().isEmpty()) {
            if (ALLOW_MISSING_CONTIG_LINES) {
                if (GeneralUtils.DEBUG_MODE_ENABLED) {
                    System.err.println(
                            "No contig dictionary found in header, falling back to reference sequence dictionary");
                }
                final VCFHeader withContigs = new VCFHeader(this.header);
                for (final VCFContigHeaderLine contig : VCFUtils.makeContigHeaderLines(getRefDict(), (Path) null)) {
                    withContigs.addMetaDataLine(contig);
                }
                contigDictionary = BCFDictionary.forContigs(withContigs);
            } else {
                throw new IllegalStateException("Cannot write BCF2 file with missing contig lines");
            }
        } else {
            contigDictionary = BCFDictionary.forContigs(this.header);
        }

        sampleNames = this.header.getGenotypeSamples().toArray(new String[this.header.getNGenotypeSamples()]);
        // setup the field encodings with version awareness
        fieldManager.setup(this.header, encoder, stringDictionaryMap, bcfVersion, outputVersion);

        if (csiIndexPath != null) {
            long longestContig = 0;
            for (final SAMSequenceRecord seq :
                    this.header.getSequenceDictionary().getSequences()) {
                longestContig = Math.max(longestContig, seq.getSequenceLength());
            }
            final BinningIndex.Geometry geo = BinningIndex.shallowestCsiGeometry(14, longestContig);
            csiIndexBuilder = new BinningIndex.Builder(geo.minShift(), geo.depth(), true);
        }
    }

    // --------------------------------------------------------------------------------
    //
    // implicit block
    //
    // The first four records of BCF are inline untype encoded data of:
    //
    // 4 byte integer chrom offset
    // 4 byte integer start
    // 4 byte integer ref length
    // 4 byte float qual
    //
    // --------------------------------------------------------------------------------
    private byte[] buildSitesData(VariantContext vc) throws IOException {
        final int contigIndex = contigDictionary.getIndex(vc.getContig());

        // note use of encodeRawValue to not insert the typing byte
        encoder.encodeRawValue(contigIndex, BCF2Type.INT32);

        // pos.  GATK is 1 based, BCF2 is 0 based
        encoder.encodeRawValue(vc.getStart() - 1, BCF2Type.INT32);

        // ref length.  GATK is closed, but BCF2 is open so the ref length is GATK end - GATK start + 1
        // for example, a SNP is in GATK at 1:10-10, which has ref length 10 - 10 + 1 = 1
        encoder.encodeRawValue(vc.getEnd() - vc.getStart() + 1, BCF2Type.INT32);

        // qual
        if (vc.hasLog10PError()) encoder.encodeRawFloat((float) vc.getPhredScaledQual());
        else encoder.encodeRawMissingValue(BCF2Type.FLOAT);

        // info fields
        final int nAlleles = vc.getNAlleles();
        final int nInfo = vc.getAttributes().size();
        final int nGenotypeFormatFields = getNGenotypeFormatFields(vc);
        final int nSamples = header.getNGenotypeSamples();

        encoder.encodeRawInt((nAlleles << 16) | (nInfo & 0x0000FFFF), BCF2Type.INT32);
        encoder.encodeRawInt((nGenotypeFormatFields << 24) | (nSamples & 0x00FFFFFF), BCF2Type.INT32);

        buildID(vc);
        buildAlleles(vc);
        buildFilter(vc);
        buildInfo(vc);

        return encoder.getRecordBytes();
    }

    /**
     * Can we safely write on the raw (undecoded) genotypes of an input VC?
     *
     * Pass through only when the source BCF version equals this writer's, the VCF header versions are on the same
     * side of the 4.3 percent-encoding boundary, 4.4 leading-phase-indicator boundary and 4.5 LAA-order boundary
     * (when the output header defines LAA), and the source's ID dictionary equals this writer's.
     */
    private boolean canSafelyWriteRawGenotypesBytes(final BCF2Codec.LazyData lazyData) {
        // A LazyData without version or dictionary (from old constructors) must be decoded
        if (lazyData.bcfVersion == null || lazyData.idDictionary == null) {
            return false;
        }

        // BCF version must match (2.1 vs 2.2 differ in padding, string form, missing-string encoding)
        if (!lazyData.bcfVersion.equals(bcfVersion)) {
            return false;
        }

        // The source and output must be on the same side of the 4.3 percent-encoding boundary
        final VCFHeaderVersion sourceVersion = lazyData.header.getVCFHeaderVersion();
        final boolean sourceIs43Plus =
                sourceVersion != null && sourceVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3);
        final boolean outputIs43Plus = outputVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3);
        if (sourceIs43Plus != outputIs43Plus) {
            return false;
        }

        // The source and output must be on the same side of the 4.4 leading-phase-indicator boundary:
        // a 4.4 GT's first allele phase bit is a leading indicator; before 4.4 it is implied by the others.
        final boolean sourceIs44Plus =
                sourceVersion != null && sourceVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_4);
        final boolean outputIs44Plus = outputVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_4);
        if (sourceIs44Plus != outputIs44Plus) {
            return false;
        }

        // At 4.5 LAA must follow GT; a source from the other side of that boundary has a different FORMAT order
        if (outputHasLaaFormat) {
            final boolean sourceIs45Plus =
                    sourceVersion != null && sourceVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_5);
            if (sourceIs45Plus != outputIs45Plus) {
                return false;
            }
        }

        // Dictionary equality: the same index->ID map
        if (!lazyData.idDictionary.equals(idDictionary)) {
            return false;
        }

        // Samples must match
        if (!nullAsEmpty(header.getSampleNamesInOrder()).equals(nullAsEmpty(lazyData.header.getSampleNamesInOrder()))) {
            return false;
        }

        return true;
    }

    private static <T> List<T> nullAsEmpty(List<T> l) {
        return l == null ? Collections.emptyList() : l;
    }

    private BCF2Codec.LazyData getLazyData(final VariantContext vc) {
        if (vc.getGenotypes().isLazyWithData()) {
            final LazyGenotypesContext lgc = (LazyGenotypesContext) vc.getGenotypes();

            if (lgc.getUnparsedGenotypeData() instanceof BCF2Codec.LazyData
                    && canSafelyWriteRawGenotypesBytes((BCF2Codec.LazyData) lgc.getUnparsedGenotypeData())) {
                return (BCF2Codec.LazyData) lgc.getUnparsedGenotypeData();
            } else {
                lgc.decode(); // WARNING -- required to avoid keeping around bad lazy data for too long
            }
        }

        return null;
    }

    /**
     * Try to get the nGenotypeFields as efficiently as possible.
     *
     * If this is a lazy BCF2 object just grab the field count from there,
     * otherwise do the whole counting by types test in the actual data
     *
     * @param vc
     * @return
     */
    private int getNGenotypeFormatFields(final VariantContext vc) {
        final BCF2Codec.LazyData lazyData = getLazyData(vc);
        return lazyData != null
                ? lazyData.nGenotypeFields
                : vc.calcVCFGenotypeKeys(header).size();
    }

    private void buildID(VariantContext vc) throws IOException {
        encoder.encodeTypedString(vc.getID());
    }

    private void buildAlleles(VariantContext vc) throws IOException {
        for (Allele allele : vc.getAlleles()) {
            final byte[] s = allele.getDisplayBases();
            if (s == null) throw new IllegalStateException("BUG: BCF2Writer encountered null padded allele" + allele);
            encoder.encodeTypedString(s);
        }
    }

    private void buildFilter(VariantContext vc) throws IOException {
        if (vc.isFiltered()) {
            encodeStringsByRef(vc.getFilters());
        } else if (vc.filtersWereApplied()) {
            encodeStringsByRef(Collections.singleton(VCFConstants.PASSES_FILTERS_v4));
        } else {
            encoder.encodeTypedMissing(BCF2Type.INT8);
        }
    }

    private void buildInfo(VariantContext vc) throws IOException {
        for (Map.Entry<String, Object> infoFieldEntry : vc.getAttributes().entrySet()) {
            final String field = infoFieldEntry.getKey();
            final BCF2FieldWriter.SiteWriter writer = fieldManager.getSiteFieldWriter(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "INFO");
            writer.start(encoder, vc);
            writer.site(encoder, vc);
            writer.done(encoder, vc);
        }
    }

    private byte[] buildSamplesData(final VariantContext vc) throws IOException {
        final BCF2Codec.LazyData lazyData = getLazyData(vc); // has critical side effects
        if (lazyData != null) {
            // we never decoded any data from this BCF file, so just pass it back
            return lazyData.bytes;
        }

        // we have to do work to convert the VC into a BCF2 byte stream
        final List<String> genotypeFields = vc.calcVCFGenotypeKeys(header);
        for (final String field : genotypeFields) {
            final BCF2FieldWriter.GenotypesWriter writer = fieldManager.getGenotypeFieldWriter(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "FORMAT");

            assert writer != null;

            writer.start(encoder, vc);
            for (final String name : sampleNames) {
                Genotype g = vc.getGenotype(name);
                if (g == null) g = GenotypeBuilder.createMissing(name, writer.nValuesPerGenotype);
                writer.addGenotype(encoder, vc, g);
            }
            writer.done(encoder, vc);
        }
        return encoder.getRecordBytes();
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
        throw new IllegalStateException(
                "Found field " + field + " in the " + fieldType + " fields of VariantContext at " + vc.getContig() + ":"
                        + vc.getStart() + " from " + vc.getSource() + " but this hasn't been defined in the VCFHeader");
    }

    // --------------------------------------------------------------------------------
    //
    // Low-level block encoding
    //
    // --------------------------------------------------------------------------------

    /**
     * Write the data in the encoder to the outputstream as a length encoded
     * block of data.  After this call the encoder stream will be ready to
     * start a new data block
     *
     * @throws IOException
     */
    private void writeBlock(final byte[] infoBlock, final byte[] genotypesBlock) throws IOException {
        BCF2Type.INT32.write(infoBlock.length, outputStream);
        BCF2Type.INT32.write(genotypesBlock.length, outputStream);
        outputStream.write(infoBlock);
        outputStream.write(genotypesBlock);
    }

    private BCF2Type encodeStringsByRef(final Collection<String> strings) throws IOException {
        final List<Integer> offsets = new ArrayList<Integer>(strings.size());

        // iterate over strings until we find one that needs 16 bits, and break
        for (final String string : strings) {
            final int offset = idDictionary.getIndex(string);
            offsets.add(offset);
        }

        final BCF2Type type = BCF2Utils.determineIntegerType(offsets);
        encoder.encodeTyped(offsets, type);
        return type;
    }
}
