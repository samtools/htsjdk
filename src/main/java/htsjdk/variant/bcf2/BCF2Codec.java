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

package htsjdk.variant.bcf2;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.tribble.BinaryFeatureCodec;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextUtils;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFPassThruTextTransformer;
import htsjdk.variant.vcf.VCFPercentEncodedTextTransformer;
import htsjdk.variant.vcf.VCFTextTransformer;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decodes BCF 2.1 and 2.2 from a stream of decompressed bytes. As for every Tribble codec, the reader that opens the
 * file decompresses it, so a BGZF-compressed BCF (as htslib and bcftools write it) and an uncompressed one (as htsjdk
 * writes it) look the same to the codec. A stream that is still compressed is refused.
 *
 * <p>The header is read once; after that the codec holds nothing that changes from record to record, so records may
 * be decoded by several threads at once, each from its own stream.
 */
public class BCF2Codec extends BinaryFeatureCodec<VariantContext> {
    public static final int ALLOWED_MAJOR_VERSION = 2;

    /**
     * The minor version htsjdk writes.
     *
     * @deprecated since 6.0.0: the codec reads BCF 2.1 and 2.2; nothing is gated on this constant any more
     */
    @Deprecated
    public static final int ALLOWED_MINOR_VERSION = 1;

    /**
     * The version htsjdk writes.
     *
     * @deprecated since 6.0.0: the codec reads BCF 2.1 and 2.2; nothing is gated on this constant any more
     */
    @Deprecated
    public static final BCFVersion ALLOWED_BCF_VERSION = new BCFVersion(ALLOWED_MAJOR_VERSION, ALLOWED_MINOR_VERSION);

    /** sizeof a BCF header (+ min/max version). Used when trying to detect when a streams starts with a bcf header */
    public static final int SIZEOF_BCF_HEADER = BCFVersion.MAGIC_HEADER_START.length + 2 * Byte.BYTES;

    /** The first byte of a gzip member, so of a BGZF stream; a BCF stream starts with 'B'. */
    private static final int GZIP_ID1 = 0x1f;

    private static final int MAX_HEADER_SIZE = 128 * 1024 * 1024; // 128 MiB

    private BCFVersion bcfVersion = null;

    /** The header as exposed to callers: without the IDX attributes the dictionaries were built from. */
    private VCFHeader header = null;

    /** Maps BCF contig indices to contig names from the header. */
    private BCFDictionary contigDictionary;

    /** Maps BCF dictionary indices to FILTER/INFO/FORMAT field names. */
    private BCFDictionary dictionary;

    private BCF2GenotypeFieldDecoders gtFieldDecoders = null;

    private BCF2LazyGenotypesDecoder lazyGenotypesDecoder = null;

    /** Percent-decodes INFO String values from VCF 4.3 on, as the VCF text reader does. */
    private VCFTextTransformer infoTextTransformer = null;

    // ----------------------------------------------------------------------
    //
    // Feature codec interface functions
    //
    // ----------------------------------------------------------------------

    /** @throws TribbleException if the stream is gzip/BGZF-compressed */
    @Override
    public LocationAware makeIndexableSourceFromStream(final InputStream bufferedInputStream) {
        final PositionalBufferedStream stream = makeSourceFromStream(bufferedInputStream);
        try {
            refuseCompressedStream(stream);
        } catch (final IOException e) {
            throw new TribbleException("I/O error while reading BCF2 file", e);
        }
        return stream;
    }

    @Override
    public Feature decodeLoc(final PositionalBufferedStream inputStream) {
        return decode(inputStream);
    }

    /** Decodes the record the stream is positioned at. An error in it cites its CHROM and POS once they are decoded. */
    @Override
    public VariantContext decode(final PositionalBufferedStream inputStream) {
        final VariantContextBuilder builder = new VariantContextBuilder();
        try {
            final BCF2Decoder decoder = new BCF2Decoder();
            final int sitesBlockSize = decoder.readBlockSize(inputStream);
            final int genotypeBlockSize = decoder.readBlockSize(inputStream);

            decoder.readNextBlock(sitesBlockSize, inputStream);
            decodeSiteLoc(decoder, builder);
            final SitesInfoForDecoding info = decodeSitesExtendedInfo(decoder, builder);

            decoder.readNextBlock(genotypeBlockSize, inputStream);
            createLazyGenotypesDecoder(decoder.getRecordBytes(), info, builder);
            return builder.fullyDecoded(true).make();
        } catch (final IOException e) {
            throw recordError("Failed to read BCF file", builder, e);
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw recordError("BCF record is truncated: " + e.getMessage(), builder, e);
        } catch (final TribbleException e) {
            throw recordError(e.getMessage(), builder, e);
        }
    }

    /** An error in a record, citing its CHROM and POS if {@link #decodeSiteLoc} has set them in the builder. */
    private static TribbleException recordError(
            final String message, final VariantContextBuilder builder, final Exception cause) {
        final String location = builder.getContig() == null
                ? ""
                : ", in the record at " + builder.getContig() + ":" + builder.getStart();
        return new TribbleException(message + location, cause);
    }

    @Override
    public Class<VariantContext> getFeatureType() {
        return VariantContext.class;
    }

    /**
     * Validate the actual version against the supported version to determine compatibility. Throws a
     * TribbleException if the actualVersion is not compatible with the supportedVersion.
     *
     * <p>The default policy accepts BCF 2.1 and 2.2.
     *
     * @param supportedVersion the version htsjdk writes
     * @param actualVersion the actual version
     * @throws TribbleException if the version policy determines that {@code actualVersion} is not compatible
     * with {@code supportedVersion}
     * @deprecated since 6.0.0: the codec reads every BCF version there is; this hook is called but there is nothing
     *     left for an override to allow
     */
    @Deprecated
    protected void validateVersionCompatibility(final BCFVersion supportedVersion, final BCFVersion actualVersion) {
        if (actualVersion.getMajorVersion() != ALLOWED_MAJOR_VERSION) {
            throw new TribbleException("BCF2Codec can only process BCF2 files, this file has major version "
                    + actualVersion.getMajorVersion());
        }
        if (actualVersion.getMinorVersion() != 1 && actualVersion.getMinorVersion() != 2) {
            throw new TribbleException("BCF2Codec does not support BCF " + actualVersion.getMajorVersion() + "."
                    + actualVersion.getMinorVersion() + "; only BCF 2.1 and 2.2 are supported");
        }
    }

    @Override
    public FeatureCodecHeader readHeader(final PositionalBufferedStream inputStream) {
        final VCFHeader rawHeader;
        try {
            refuseCompressedStream(inputStream);

            // note that this reads the magic as well, and so does double duty
            bcfVersion = BCFVersion.readBCFVersion(inputStream);
            if (bcfVersion == null) {
                throw new TribbleException(
                        "Input stream does not contain a BCF encoded file; BCF magic header info not found");
            }
            validateVersionCompatibility(ALLOWED_BCF_VERSION, bcfVersion);
            if (GeneralUtils.DEBUG_MODE_ENABLED) {
                System.err.println("Parsing data stream with BCF version " + bcfVersion);
            }

            final int headerSizeInBytes = BCF2Type.INT32.read(inputStream);
            if (headerSizeInBytes <= 0 || headerSizeInBytes > MAX_HEADER_SIZE) {
                throw new TribbleException("BCF2 header has invalid length: " + headerSizeInBytes
                        + " must be > 0 and <= " + MAX_HEADER_SIZE);
            }

            final byte[] headerBytes = new byte[headerSizeInBytes];
            if (inputStream.read(headerBytes) != headerSizeInBytes) {
                throw new TribbleException(
                        "Couldn't read all of the bytes specified in the header length = " + headerSizeInBytes);
            }

            // the text is NUL-terminated, and the terminator is counted in its length
            int headerTextLength = headerSizeInBytes;
            while (headerTextLength > 0 && headerBytes[headerTextLength - 1] == 0) {
                headerTextLength--;
            }
            final PositionalBufferedStream bps =
                    new PositionalBufferedStream(new ByteArrayInputStream(headerBytes, 0, headerTextLength));
            final LineIterator lineIterator = new LineIteratorImpl(new SynchronousLineReader(bps));
            rawHeader = (VCFHeader) new VCFCodec().readActualHeader(lineIterator);
            bps.close();
        } catch (IOException e) {
            throw new TribbleException("I/O error while reading BCF2 header", e);
        }

        if (rawHeader.getContigLines().isEmpty()) {
            throw new TribbleException("Didn't find any contig lines in BCF2 file header");
        }
        contigDictionary = BCFDictionary.forContigs(rawHeader);
        dictionary = BCFDictionary.forIDs(rawHeader);
        header = withoutIdx(rawHeader);

        final VCFHeaderVersion version = header.getVCFHeaderVersion();
        infoTextTransformer = version != null && version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)
                ? new VCFPercentEncodedTextTransformer()
                : new VCFPassThruTextTransformer();
        gtFieldDecoders = new BCF2GenotypeFieldDecoders(header);
        lazyGenotypesDecoder = new BCF2LazyGenotypesDecoder(dictionary, gtFieldDecoders);

        return new FeatureCodecHeader(header, inputStream.getPosition());
    }

    @Override
    public boolean canDecode(final String path) {
        try (InputStream rawStream = Files.newInputStream(IOUtil.getPath(path))) {
            final BufferedInputStream bis = new BufferedInputStream(rawStream);
            // Try BGZF first
            if (BlockCompressedInputStream.isValidFile(bis)) {
                try (final BlockCompressedInputStream bcis = new BlockCompressedInputStream(bis)) {
                    return isSupportedVersion(BCFVersion.readBCFVersion(bcis));
                }
            }
            // Try raw BCF
            return isSupportedVersion(BCFVersion.readBCFVersion(bis));
        } catch (final IOException e) {
            return false;
        }
    }

    private static boolean isSupportedVersion(final BCFVersion version) {
        return version != null
                && version.getMajorVersion() == ALLOWED_MAJOR_VERSION
                && (version.getMinorVersion() == 1 || version.getMinorVersion() == 2);
    }

    /**
     * Throws if the stream starts with the gzip magic, which a BCF stream cannot: otherwise a compressed stream would
     * be reported as holding no BCF magic, which says nothing of the cause.
     */
    private static void refuseCompressedStream(final PositionalBufferedStream stream) throws IOException {
        if (stream.peek() == GZIP_ID1) {
            throw new TribbleException("The stream is gzip/BGZF-compressed; BCF2Codec reads decompressed bytes, so the "
                    + "stream must be decompressed (for example with IOUtil.openGzipOrBgzfStream) before it reaches "
                    + "the codec");
        }
    }

    // --------------------------------------------------------------------------------
    //
    // Header
    //
    // --------------------------------------------------------------------------------

    /**
     * The header with the IDX attribute removed from every FILTER, INFO, FORMAT and contig line, or the header itself
     * if no line carries one. IDX is the BCF dictionary's business, settled by the time the header is exposed, and a
     * writer that copied it would mislead the next reader. The lines are rebuilt rather than edited: a header keeps
     * its lines in hash-based collections, and a line's hash covers its attributes.
     */
    private static VCFHeader withoutIdx(final VCFHeader rawHeader) {
        final VCFHeaderVersion version = rawHeader.getVCFHeaderVersion() == null
                ? VCFHeaderVersion.DEFAULT_VERSION
                : rawHeader.getVCFHeaderVersion();
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        boolean anyIdx = false;
        for (final VCFHeaderLine line : rawHeader.getMetaDataInInputOrder()) {
            final VCFHeaderLine stripped = withoutIdx(line, version);
            anyIdx |= stripped != line;
            lines.add(stripped);
        }
        return anyIdx ? new VCFHeader(lines, rawHeader.getGenotypeSamples()) : rawHeader;
    }

    /** The line without its IDX attribute, rebuilt through its own parser, or the line itself if it has none. */
    private static VCFHeaderLine withoutIdx(final VCFHeaderLine line, final VCFHeaderVersion version) {
        if (BCFDictionary.idxAttribute(line) == null) {
            return line;
        }
        if (line instanceof VCFInfoHeaderLine) {
            return new VCFInfoHeaderLine(encodedWithoutIdx(((VCFCompoundHeaderLine) line).getGenericFields()), version);
        }
        if (line instanceof VCFFormatHeaderLine) {
            return new VCFFormatHeaderLine(
                    encodedWithoutIdx(((VCFCompoundHeaderLine) line).getGenericFields()), version);
        }
        if (line instanceof VCFFilterHeaderLine) {
            return new VCFFilterHeaderLine(encodedWithoutIdx(((VCFFilterHeaderLine) line).getGenericFields()), version);
        }
        if (line instanceof VCFContigHeaderLine) {
            final VCFContigHeaderLine contig = (VCFContigHeaderLine) line;
            final Map<String, String> attributes = new LinkedHashMap<>(contig.getGenericFields());
            attributes.remove(BCFDictionary.IDX_ATTRIBUTE);
            return new VCFContigHeaderLine(attributes, contig.getContigIndex());
        }
        return line;
    }

    /** A line's attributes without IDX, as the text between the angle brackets of a header line. */
    private static String encodedWithoutIdx(final Map<String, String> attributes) {
        final Map<String, String> kept = new LinkedHashMap<>(attributes);
        kept.remove(BCFDictionary.IDX_ATTRIBUTE);
        return VCFHeaderLine.toStringEncoding(kept);
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

    /**
     * Decode the sites level data from the record's decoder. The contig is set in the builder together with the
     * position, so that an error can cite both once the contig is there.
     */
    private void decodeSiteLoc(final BCF2Decoder decoder, final VariantContextBuilder builder) throws IOException {
        final int contigOffset = decoder.decodeInt(BCF2Type.INT32);
        final String contig = lookupContigName(contigOffset);

        final int pos = decoder.decodeInt(BCF2Type.INT32) + 1; // GATK is one based, BCF2 is zero-based
        final int refLength = decoder.decodeInt(BCF2Type.INT32);
        builder.chr(contig);
        builder.start((long) pos);
        builder.stop((long) (pos + refLength - 1)); // minus one because GATK has closed intervals but BCF2 is open
    }

    /**
     * Decode the sites level data from the record's decoder
     */
    private SitesInfoForDecoding decodeSitesExtendedInfo(final BCF2Decoder decoder, final VariantContextBuilder builder)
            throws IOException {
        final Object qual = decoder.decodeSingleValue(BCF2Type.FLOAT);
        if (qual != null) {
            builder.log10PError(((Double) qual) / -10.0);
        }

        final int nAlleleInfo = decoder.decodeInt(BCF2Type.INT32);
        final int nFormatSamples = decoder.decodeInt(BCF2Type.INT32);
        final int nAlleles = nAlleleInfo >> 16;
        final int nInfo = nAlleleInfo & 0x0000FFFF;
        final int nFormatFields = nFormatSamples >> 24;
        final int nSamples = nFormatSamples & 0x00FFFFFF;

        if (nAlleles < 1) {
            throw new TribbleException("Record has no alleles");
        }

        if (header.getNGenotypeSamples() != nSamples) {
            throw new TribbleException("Reading BCF2 files with different numbers of samples per record "
                    + "is not currently supported.  Saw "
                    + header.getNGenotypeSamples() + " samples in header but have a record with "
                    + nSamples + " samples");
        }

        decodeID(decoder, builder);
        final List<Allele> alleles = decodeAlleles(decoder, builder, nAlleles);
        decodeFilter(decoder, builder);
        decodeInfo(decoder, builder, nInfo);

        final SitesInfoForDecoding info = new SitesInfoForDecoding(nFormatFields, nSamples, alleles);
        if (!info.isValid()) throw new TribbleException("Sites info is malformed: " + info);
        return info;
    }

    protected static final class SitesInfoForDecoding {
        final int nFormatFields;
        final int nSamples;
        final List<Allele> alleles;

        private SitesInfoForDecoding(final int nFormatFields, final int nSamples, final List<Allele> alleles) {
            this.nFormatFields = nFormatFields;
            this.nSamples = nSamples;
            this.alleles = alleles;
        }

        public boolean isValid() {
            return nFormatFields >= 0
                    && nSamples >= 0
                    && alleles != null
                    && !alleles.isEmpty()
                    && alleles.get(0).isReference();
        }

        @Override
        public String toString() {
            return String.format("nFormatFields = %d, nSamples = %d, alleles = %s", nFormatFields, nSamples, alleles);
        }
    }

    /**
     * Decode the id field in this BCF2 file and store it in the builder
     */
    private void decodeID(final BCF2Decoder decoder, final VariantContextBuilder builder) throws IOException {
        final String id = (String) decoder.decodeTypedValue();

        if (id == null) builder.noID();
        else builder.id(id);
    }

    /**
     * Decode the alleles from this BCF2 file and put the results in builder
     */
    private List<Allele> decodeAlleles(
            final BCF2Decoder decoder, final VariantContextBuilder builder, final int nAlleles) throws IOException {
        List<Allele> alleles = new ArrayList<Allele>(nAlleles);
        String ref = null;

        for (int i = 0; i < nAlleles; i++) {
            final String alleleBases = (String) decoder.decodeTypedValue();

            final boolean isRef = i == 0;
            final Allele allele = Allele.create(alleleBases, isRef);
            if (isRef) ref = alleleBases;

            alleles.add(allele);
        }
        assert ref != null;

        builder.alleles(alleles);

        assert !ref.isEmpty();

        return alleles;
    }

    /**
     * Decode the filter field of this BCF2 file and store the result in the builder
     */
    private void decodeFilter(final BCF2Decoder decoder, final VariantContextBuilder builder) throws IOException {
        final Object value = decoder.decodeTypedValue();

        if (value == null) builder.unfiltered();
        else {
            if (value instanceof Integer) {
                final String filterString = getDictionaryString((Integer) value);
                if (VCFConstants.PASSES_FILTERS_v4.equals(filterString)) builder.passFilters();
                else builder.filter(filterString);
            } else {
                for (final int offset : (List<Integer>) value) builder.filter(getDictionaryString(offset));
            }
        }
    }

    /**
     * Loop over the info field key / value pairs in this BCF2 file and decode them into the builder
     */
    private void decodeInfo(final BCF2Decoder decoder, final VariantContextBuilder builder, final int numInfoFields)
            throws IOException {
        if (numInfoFields == 0) return;

        final Map<String, Object> infoFieldEntries = new HashMap<String, Object>(numInfoFields);
        for (int i = 0; i < numInfoFields; i++) {
            final String key = getDictionaryString((Integer) decoder.decodeTypedValue());
            final byte typeDescriptor = decoder.readTypeDescriptor();
            Object value = decoder.decodeTypedValue(typeDescriptor);
            final VCFCompoundHeaderLine metaData = VariantContextUtils.getMetaDataForField(header, key);
            if (metaData.getType() == VCFHeaderLineType.Flag) {
                value = true; // whichever way the flag was encoded, its presence is what it says
            } else if (value != null && BCF2Utils.decodeType(typeDescriptor) == BCF2Type.CHAR) {
                value = infoStringValue(value);
            }
            infoFieldEntries.put(key, value);
        }

        builder.attributes(infoFieldEntries);
    }

    /**
     * An INFO String value as the VCF text reader would hold it: a value with commas is a list of Strings, split
     * before the elements are percent-decoded (from VCF 4.3), so an encoded comma inside an element stays.
     */
    @SuppressWarnings("unchecked")
    private Object infoStringValue(final Object value) {
        if (value instanceof String) {
            final String s = (String) value;
            return s.indexOf(',') < 0
                    ? infoTextTransformer.decodeText(s)
                    : infoTextTransformer.decodeText(Arrays.asList(s.split(",", -1)));
        }
        return infoTextTransformer.decodeText((List<String>) value);
    }

    // --------------------------------------------------------------------------------
    //
    // Decoding Genotypes
    //
    // --------------------------------------------------------------------------------

    /**
     * Create the lazy loader for the genotypes data, and store it in the builder
     * so that the VC will be able to decode on demand the genotypes data
     */
    private void createLazyGenotypesDecoder(
            final byte[] genotypeBytes, final SitesInfoForDecoding siteInfo, final VariantContextBuilder builder) {
        if (siteInfo.nSamples > 0) {
            final LazyData lazyData = new LazyData(
                    header, siteInfo.nFormatFields, genotypeBytes, siteInfo.alleles, bcfVersion, dictionary);
            final LazyGenotypesContext lazy = new LazyGenotypesContext(
                    lazyGenotypesDecoder, lazyData, header.getNGenotypeSamples(), header.getVCFHeaderVersion());

            // did we resort the sample names?  If so, we need to load the genotype data
            if (!header.samplesWereAlreadySorted()) lazy.decode();

            builder.genotypesNoValidation(lazy);
        }
    }

    /** The undecoded genotype block of one record, with what decoding it needs. */
    public static class LazyData {
        public final VCFHeader header;
        public final int nGenotypeFields;
        public final byte[] bytes;

        /** The record's alleles, which its GT values index into; null if not given to the constructor. */
        public final List<Allele> alleles;

        /** The BCF version of the file this record came from; null for data from old constructors. */
        public final BCFVersion bcfVersion;

        /** The ID dictionary of the file this record came from; null for data from old constructors. */
        public final BCFDictionary idDictionary;

        public LazyData(final VCFHeader header, final int nGenotypeFields, final byte[] bytes) {
            this(header, nGenotypeFields, bytes, null, null, null);
        }

        public LazyData(
                final VCFHeader header, final int nGenotypeFields, final byte[] bytes, final List<Allele> alleles) {
            this(header, nGenotypeFields, bytes, alleles, null, null);
        }

        public LazyData(
                final VCFHeader header,
                final int nGenotypeFields,
                final byte[] bytes,
                final List<Allele> alleles,
                final BCFVersion bcfVersion,
                final BCFDictionary idDictionary) {
            this.header = header;
            this.nGenotypeFields = nGenotypeFields;
            this.bytes = bytes;
            this.alleles = alleles;
            this.bcfVersion = bcfVersion;
            this.idDictionary = idDictionary;
        }
    }

    /**
     * @return the FILTER, INFO or FORMAT ID at a BCF dictionary index
     * @throws TribbleException if the dictionary has no ID at that index
     */
    protected final String getDictionaryString(final int offset) {
        return dictionary.getString(offset);
    }

    /**
     * Translate the config offset as encoded in the BCF file into the actual string
     * name of the contig from the dictionary
     */
    private String lookupContigName(final int contigOffset) {
        try {
            return contigDictionary.getString(contigOffset);
        } catch (final TribbleException e) {
            throw new TribbleException("Invalid contig index " + contigOffset + ": " + e.getMessage(), e);
        }
    }

    /**
     * @return the VCFHeader we found in this BCF2 file
     */
    protected VCFHeader getHeader() {
        return header;
    }

    protected BCF2GenotypeFieldDecoders.Decoder getGenotypeFieldDecoder(final String field) {
        return gtFieldDecoders.getDecoder(field);
    }

    /**
     * @deprecated since 6.0.0: the codec keeps no current record; throw a {@link TribbleException} instead
     */
    @Deprecated
    protected void error(final String message) throws RuntimeException {
        throw new TribbleException(message);
    }

    /** try to read a BCFVersion from an uncompressed BufferedInputStream.
     * The buffer must be large enough to contain {@link #SIZEOF_BCF_HEADER}
     *
     * @param uncompressedBufferedInput the uncompressed input stream
     * @return the BCFVersion if it can be decoded, or null if not found.
     * @throws IOException
     */
    public static BCFVersion tryReadBCFVersion(final BufferedInputStream uncompressedBufferedInput) throws IOException {
        uncompressedBufferedInput.mark(SIZEOF_BCF_HEADER);
        final BCFVersion bcfVersion = BCFVersion.readBCFVersion(uncompressedBufferedInput);
        uncompressedBufferedInput.reset();
        return bcfVersion;
    }
}
