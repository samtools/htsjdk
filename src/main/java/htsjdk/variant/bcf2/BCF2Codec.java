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

import htsjdk.samtools.util.IOUtil;
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
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextUtils;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decode BCF2 files
 */
public class BCF2Codec extends BinaryFeatureCodec<VariantContext> {
    public static String IDXField = "IDX"; // BCF2.2 IDX field name

    protected final static int ALLOWED_MAJOR_VERSION = 2;
    protected final static int ALLOWED_MINOR_VERSION = 2;
    public static final BCFVersion ALLOWED_BCF_VERSION = new BCFVersion(ALLOWED_MAJOR_VERSION, ALLOWED_MINOR_VERSION);

    /**
     * sizeof a BCF header (+ min/max version). Used when trying to detect when a streams starts with a bcf header
     */
    public static final int SIZEOF_BCF_HEADER = BCFVersion.MAGIC_HEADER_START.length + 2 * Byte.BYTES;

    private BCFVersion bcfVersion = null;

    private VCFHeader header = null;

    /**
     * Maps offsets (encoded in BCF) into contig names (from header) for the CHROM field
     */
    private BCF2Dictionary contigDictionary;

    /**
     * Maps header string names (encoded in VCF) into strings found in the BCF header
     * <p>
     * Initialized when processing the header
     */
    private BCF2Dictionary stringDictionary;

    /**
     * Our decoder that reads low-level objects from the BCF2 records
     */
    private BCF2Decoder decoder;

    /**
     * Provides some sanity checking on the header
     */
    private final static int MAX_HEADER_SIZE = 0x08000000;

    /**
     * Genotype field decoders that are initialized when the header is read
     */
    private BCF2GenotypeFieldDecoders gtFieldDecoders = null;

    /**
     * A cached array of GenotypeBuilders for efficient genotype decoding.
     * <p>
     * Caching it allows us to avoid recreating this intermediate data
     * structure each time we decode genotypes
     */
    private GenotypeBuilder[] builders = null;

    // for error handling
    private int recordNo = 0;
    private int pos = 0;


    // ----------------------------------------------------------------------
    //
    // Feature codec interface functions
    //
    // ----------------------------------------------------------------------

    @Override
    public Feature decodeLoc(final PositionalBufferedStream inputStream) {
        return decode(inputStream);
    }

    @Override
    public VariantContext decode(final PositionalBufferedStream inputStream) {
        try {
            recordNo++;
            final VariantContextBuilder builder = new VariantContextBuilder();

            final int sitesBlockSize = decoder.readBlockSize(inputStream);
            final int genotypeBlockSize = decoder.readBlockSize(inputStream);

            decoder.readNextBlock(sitesBlockSize, inputStream);
            decodeSiteLoc(builder);
            final SitesInfoForDecoding info = decodeSitesExtendedInfo(builder);

            decoder.readNextBlock(genotypeBlockSize, inputStream);
            createLazyGenotypesDecoder(info, builder);
            return builder.fullyDecoded(true).make();
        } catch (final IOException e) {
            throw new TribbleException("Failed to read BCF file", e);
        }
    }

    @Override
    public Class<VariantContext> getFeatureType() {
        return VariantContext.class;
    }

    @Override
    public FeatureCodecHeader readHeader(final PositionalBufferedStream inputStream) {
        try {
            // note that this reads the magic as well, and so does double duty
            bcfVersion = BCFVersion.readBCFVersion(inputStream);
            if (bcfVersion == null) {
                error("Input stream does not contain a BCF encoded file; BCF magic header info not found");
            }

            decoder = BCF2Decoder.getDecoder(bcfVersion);
            if (GeneralUtils.DEBUG_MODE_ENABLED) {
                System.err.println("Parsing data stream with BCF version " + bcfVersion);
            }

            final int headerSizeInBytes = BCF2Type.INT32.read(inputStream);

            if (headerSizeInBytes <= 0 || headerSizeInBytes > MAX_HEADER_SIZE) // no bigger than 8 MB
                error("BCF2 header has invalid length: " + headerSizeInBytes + " must be >= 0 and < " + MAX_HEADER_SIZE);

            final byte[] headerBytes = new byte[headerSizeInBytes];
            if (inputStream.read(headerBytes) != headerSizeInBytes)
                error("Couldn't read all of the bytes specified in the header length = " + headerSizeInBytes);

            final PositionalBufferedStream bps = new PositionalBufferedStream(new ByteArrayInputStream(headerBytes));
            final LineIterator lineIterator = new LineIteratorImpl(new SynchronousLineReader(bps));
            final VCFCodec headerParser = new VCFCodec();
            this.header = (VCFHeader) headerParser.readActualHeader(lineIterator);
            bps.close();
        } catch (final IOException e) {
            throw new TribbleException("I/O error while reading BCF2 header");
        }

        // TODO should follow up on hts-specs and clarify the relationship between ##dictionary and IDX fields
        // Error on ##dictionary lines, we don't know what to do with them
        if (this.header.getMetaDataInInputOrder().stream().anyMatch(line -> line.getKey().equals("dictionary"))) {
            throw new TribbleException("Use of the ##dictionary line is not supported");
        }

        // create the contig dictionary
        contigDictionary = makeContigDictionary(bcfVersion);

        // create the string dictionary
        stringDictionary = makeStringDictionary(bcfVersion);

        // prepare the genotype field decoders
        gtFieldDecoders = new BCF2GenotypeFieldDecoders(header);

        // create and initialize the genotype builder array
        final int nSamples = header.getNGenotypeSamples();
        builders = new GenotypeBuilder[nSamples];
        for (int i = 0; i < nSamples; i++) {
            builders[i] = new GenotypeBuilder(header.getGenotypeSamples().get(i));
        }

        // position right before next line (would be right before first real record byte at end of header)
        return new FeatureCodecHeader(header, inputStream.getPosition());
    }

    @Override
    public boolean canDecode(final String path) {
        // TODO: this is broken
        // First, the version check is too permissive - it accepts any minor version, including BCF 2.2,
        // which it shouldn't without checking version compatibility.
        // Second, it doesn't recognize that BCF can be block gzipped, so it rejects
        // those files because the header never matches, but only because the stream isn't decompressed.
        // TODO: call validateVersionCompatibility here instead to remedy #1 above
        try (final InputStream fis = Files.newInputStream(IOUtil.getPath(path))) {
            final BCFVersion version = BCFVersion.readBCFVersion(fis);
            return version != null && version.getMajorVersion() == ALLOWED_MAJOR_VERSION;
        } catch (final IOException e) {
            return false;
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

    /**
     * Decode the sites level data from this classes decoder
     *
     * @param builder
     * @return
     */
    private final void decodeSiteLoc(final VariantContextBuilder builder) throws IOException {
        final int contigOffset = decoder.decodeInt(BCF2Type.INT32);
        final String contig = lookupContigName(contigOffset);
        builder.chr(contig);

        this.pos = decoder.decodeInt(BCF2Type.INT32) + 1; // GATK is one based, BCF2 is zero-based
        final int refLength = decoder.decodeInt(BCF2Type.INT32);
        builder.start(pos);
        builder.stop(pos + refLength - 1); // minus one because GATK has closed intervals but BCF2 is open
    }

    /**
     * Decode the sites level data from this classes decoder
     *
     * @param builder
     * @return
     */
    private final SitesInfoForDecoding decodeSitesExtendedInfo(final VariantContextBuilder builder) throws IOException {
        final Object qual = decoder.decodeSingleValue(BCF2Type.FLOAT);
        if (qual != null) {
            builder.log10PError(((Double) qual) / -10.0);
        }

        final int nAlleleInfo = decoder.decodeInt(BCF2Type.INT32);
        final int nFormatSamples = decoder.decodeInt(BCF2Type.INT32);
        // Use logical shift to not introduce leading 1s
        final int nAlleles = nAlleleInfo >>> 16;
        final int nInfo = nAlleleInfo & 0x0000FFFF;
        final int nFormatFields = nFormatSamples >>> 24;
        final int nSamples = nFormatSamples & 0x00FFFFF;

        if (header.getNGenotypeSamples() != nSamples)
            error("Reading BCF2 files with different numbers of samples per record " +
                "is not currently supported.  Saw " + header.getNGenotypeSamples() +
                " samples in header but have a record with " + nSamples + " samples");

        decodeID(builder);
        final List<Allele> alleles = decodeAlleles(builder, pos, nAlleles);
        decodeFilter(builder);
        decodeInfo(builder, nInfo);

        final SitesInfoForDecoding info = new SitesInfoForDecoding(nFormatFields, nSamples, alleles);
        if (!info.isValid())
            error("Sites info is malformed: " + info);
        return info;
    }

    protected final static class SitesInfoForDecoding {
        final int nFormatFields;
        final int nSamples;
        final List<Allele> alleles;

        private SitesInfoForDecoding(final int nFormatFields, final int nSamples, final List<Allele> alleles) {
            this.nFormatFields = nFormatFields;
            this.nSamples = nSamples;
            this.alleles = alleles;
        }

        public boolean isValid() {
            return nFormatFields >= 0 &&
                nSamples >= 0 &&
                alleles != null && !alleles.isEmpty() && alleles.get(0).isReference();
        }

        @Override
        public String toString() {
            return String.format("nFormatFields = %d, nSamples = %d, alleles = %s", nFormatFields, nSamples, alleles);
        }
    }

    /**
     * Decode the id field in this BCF2 file and store it in the builder
     *
     * @param builder
     */
    private void decodeID(final VariantContextBuilder builder) throws IOException {
        final String id = decoder.decodeUnexplodedString();

        if (id == null)
            builder.noID();
        else
            builder.id(id);
    }

    /**
     * Decode the alleles from this BCF2 file and put the results in builder
     *
     * @param builder
     * @param pos
     * @param nAlleles
     * @return the alleles
     */
    private List<Allele> decodeAlleles(final VariantContextBuilder builder, final int pos, final int nAlleles) throws IOException {
        final List<Allele> alleles = new ArrayList<>(nAlleles);
        byte[] ref = null;

        for (int i = 0; i < nAlleles; i++) {
            // Some decoder functionality is inlined here to avoid conversion from bytes -> string -> bytes
            final byte typeDescriptor = decoder.readTypeDescriptor();
            final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
            if (type != BCF2Type.CHAR) {
                error("Expected to find vector of type CHAR while decoding Alleles, found type " + type);
            }
            final int size = decoder.decodeNumberOfElements(typeDescriptor);
            final byte[] alleleBases = decoder.decodeRawBytes(size);

            final boolean isRef = i == 0;
            final Allele allele = Allele.create(alleleBases, isRef);
            if (isRef) ref = alleleBases;

            alleles.add(allele);
        }

        assert ref != null;
        assert ref.length > 0;

        builder.alleles(alleles);
        return alleles;
    }

    /**
     * Decode the filter field of this BCF2 file and store the result in the builder
     *
     * @param builder
     */
    private void decodeFilter(final VariantContextBuilder builder) throws IOException {
        final byte typeDescriptor = decoder.readTypeDescriptor();
        final int size = decoder.decodeNumberOfElements(typeDescriptor);
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);

        if (size == 0) {
            // No filters
            builder.unfiltered();
        } else if (size == 1) {
            final int i = decoder.decodeInt(type);
            if (i == 0) {
                // PASS is always implicitly encoded as 0
                builder.passFilters();
            } else {
                builder.filter(getDictionaryString(i));
            }
        } else {
            for (final int offset : decoder.decodeIntArray(size, type, null)) {
                builder.filter(getDictionaryString(offset));
            }
        }
    }

    /**
     * Loop over the info field key / value pairs in this BCF2 file and decode them into the builder
     *
     * @param builder
     * @param numInfoFields
     */
    private void decodeInfo(final VariantContextBuilder builder, final int numInfoFields) throws IOException {
        if (numInfoFields == 0)
            // fast path, don't bother doing any work if there are no fields
            return;

        final Map<String, Object> infoFieldEntries = new HashMap<>(numInfoFields);
        for (int i = 0; i < numInfoFields; i++) {
            final String key = getDictionaryString();
            Object value = decoder.decodeTypedValue();
            final VCFCompoundHeaderLine metaData = VariantContextUtils.getMetaDataForField(header, key);
            if (metaData.getType() == VCFHeaderLineType.Flag && value != null) {
                value = Boolean.TRUE; // special case for flags
            }
            infoFieldEntries.put(key, value);
        }

        builder.attributes(infoFieldEntries);
    }

    // --------------------------------------------------------------------------------
    //
    // Decoding Genotypes
    //
    // --------------------------------------------------------------------------------

    /**
     * Create the lazy loader for the genotypes data, and store it in the builder
     * so that the VC will be able to decode on demand the genotypes data
     *
     * @param siteInfo
     * @param builder
     */
    private void createLazyGenotypesDecoder(final SitesInfoForDecoding siteInfo,
                                            final VariantContextBuilder builder) {
        if (siteInfo.nSamples > 0) {
            final LazyGenotypesContext.LazyParser lazyParser =
                new BCF2LazyGenotypesDecoder(this, siteInfo.alleles, siteInfo.nSamples, siteInfo.nFormatFields, builders);

            final LazyData lazyData = new LazyData(header, siteInfo.nFormatFields, decoder.getRecordBytes());
            final LazyGenotypesContext lazy = new LazyGenotypesContext(lazyParser, lazyData, header.getNGenotypeSamples());

            // did we resort the sample names?  If so, we need to load the genotype data
            if (!header.samplesWereAlreadySorted())
                lazy.decode();

            builder.genotypesNoValidation(lazy);
        }
    }

    public static class LazyData {
        final public VCFHeader header;
        final public int nGenotypeFields;
        final public byte[] bytes;

        public LazyData(final VCFHeader header, final int nGenotypeFields, final byte[] bytes) {
            this.header = header;
            this.nGenotypeFields = nGenotypeFields;
            this.bytes = bytes;
        }
    }

    private String getDictionaryString() throws IOException {
        return getDictionaryString((Integer) decoder.decodeTypedValue());
    }

    protected final String getDictionaryString(final int offset) {
        return stringDictionary.get(offset);
    }

    private BCF2Dictionary makeStringDictionary(final BCFVersion bcfVersion) {
        final BCF2Dictionary dict = BCF2Dictionary.makeBCF2StringDictionary(header, bcfVersion);

        // if we got here we never found a dictionary, or there are no elements in the dictionary
        if (dict.isEmpty())
            error("Dictionary header element was absent or empty");

        return dict;
    }

    /**
     * Translate the config offset as encoded in the BCF file into the actual string
     * name of the contig from the dictionary
     *
     * @param contigOffset
     * @return
     */
    private String lookupContigName(final int contigOffset) {
        return contigDictionary.get(contigOffset);
    }

    private BCF2Dictionary makeContigDictionary(final BCFVersion bcfVersion) {
        // create the config offsets
        if (header.getContigLines().isEmpty())
            error("Didn't find any contig lines in BCF2 file header");

        return BCF2Dictionary.makeBCF2ContigDictionary(header, bcfVersion);
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

    protected void error(final String message) throws RuntimeException {
        throw new TribbleException(String.format("%s, at record %d with position %d:", message, recordNo, pos));
    }

    /**
     * try to read a BCFVersion from an uncompressed BufferedInputStream.
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

    public BCFVersion getBCFVersion() {
        return bcfVersion;
    }
}
