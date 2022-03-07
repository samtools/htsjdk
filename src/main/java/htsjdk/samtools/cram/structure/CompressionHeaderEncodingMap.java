/*
 * Copyright (c) 2019 The Broad Institute
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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Params;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.external.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalLongEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.utils.ValidationUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import htsjdk.samtools.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maintains a map of DataSeries to EncodingDescriptor, and a second map that contains the compressor to use
 * for each EncodingDescriptor that represents an EXTERNAL encoding.
 *
 * There are two constructors; one populates the map from scratch using the default encodings chosen by
 * this (htsjdk) implementation, used when writing a new CRAM; one populates the map from a serialized
 * CRAM stream resulting in encodings chosen by the implementation that wrote that CRAM.
 *
 * Although the CRAM spec defines a fixed list of data series, individual CRAM implementations
 * may choose to use only a subset of these. Therefore, the actual set of encodings that are
 * instantiated can vary depending on the source.
 *
 * Notes on the htsjdk CRAM write implementation: This implementation encodes ALL DataSeries to external
 * blocks, (although some of the external encodings split the data between core and external; see
 * {@link htsjdk.samtools.cram.encoding.ByteArrayLenEncoding}, and does not use the 'BB' or 'QQ'
 * DataSeries when writing CRAM at all.  Relies heavily on GZIP and RANS for compression.
 *
 * See {@link htsjdk.samtools.cram.encoding.EncodingFactory} for details on how an {@link EncodingDescriptor}
 * is mapped to the codec that actually transfers data to and from underlying Slice blocks.
 */
public class CompressionHeaderEncodingMap {

    // Set of obsolete DataSeries that are ignored on CRAM read
    public static final Set<DataSeries> DATASERIES_NOT_READ_BY_HTSJDK = Collections.unmodifiableSet(new LinkedHashSet<DataSeries>() {{
        add(DataSeries.TC_TagCount);
        add(DataSeries.TN_TagNameAndType);
    }});

    private final static Log LOG = Log.getInstance(CompressionHeaderEncodingMap.class);

    // Encoding descriptors for each data series. (These encodings can be either EXTERNAL or CORE, although
    // the spec does not make a clear distinction between EXTERNAL and CODE for encodings; only for blocks.
    // See https://github.com/samtools/hts-specs/issues/426). The encodingMap is used as a template that is
    // reused for each container being written (though this is not required).
    private Map<DataSeries, EncodingDescriptor> encodingMap = new TreeMap<>();

    // External compressor to use for each external block, keyed by external content ID. This
    // map contains a key for each data series that is in an external block, plus additional
    // ones for each external block used for tags. The externalCompressors list does not quite
    // parallel the encodingMap because it varies with the tags discovered in the records
    // for each container.
    private final Map<Integer, ExternalCompressor> externalCompressors = new TreeMap<>();

    // Keep a compressor cache for the lifetime of this encoding map
    private final CompressorCache compressorCache = new CompressorCache();

    /**
     * Constructor used to create the default encoding map for writing CRAMs. The encoding strategy
     * parameter values are used to set compression levels, etc, but any encoding map embedded is ignored
     * since this uses the default strategy.
     *
     * @param encodingStrategy {@link CRAMEncodingStrategy} containing parameter values to use when creating
     *                                                     the encoding map
     */
    public CompressionHeaderEncodingMap(final CRAMEncodingStrategy encodingStrategy) {
        ValidationUtils.nonNull(encodingStrategy, "An encoding strategy must be provided");
        ValidationUtils.validateArg(
                encodingStrategy.getCustomCompressionHeaderEncodingMap() == null,
                "A custom compression map cannot be used with this constructor");

        // NOTE: all of these encodings use external blocks and compressors for actual CRAM
        // data. The only use of core block encodings are as params for other (external)
        // encodings, i.e., the ByteArrayLenEncoding used for tag data uses a core (sub-)encoding
        // to store the length of the array that is stored in an external block.
        putExternalRansOrderZeroEncoding(DataSeries.AP_AlignmentPositionOffset);
        putExternalRansOrderOneEncoding(DataSeries.BA_Base);
        // the BB data series is not used by this implementation when writing CRAMs
        putExternalRansOrderOneEncoding(DataSeries.BF_BitFlags);
        putExternalGzipEncoding(encodingStrategy, DataSeries.BS_BaseSubstitutionCode);
        putExternalRansOrderOneEncoding(DataSeries.CF_CompressionBitFlags);
        putExternalGzipEncoding(encodingStrategy, DataSeries.DL_DeletionLength);
        putExternalGzipEncoding(encodingStrategy, DataSeries.FC_FeatureCode);
        putExternalGzipEncoding(encodingStrategy, DataSeries.FN_NumberOfReadFeatures);
        putExternalGzipEncoding(encodingStrategy, DataSeries.FP_FeaturePosition);
        putExternalGzipEncoding(encodingStrategy, DataSeries.HC_HardClip);
        putExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.IN_Insertion);
        putExternalGzipEncoding(encodingStrategy, DataSeries.MF_MateBitFlags);
        putExternalGzipEncoding(encodingStrategy, DataSeries.MQ_MappingQualityScore);
        putExternalGzipEncoding(encodingStrategy, DataSeries.NF_RecordsToNextFragment);
        putExternalGzipEncoding(encodingStrategy, DataSeries.NP_NextFragmentAlignmentStart);
        putExternalRansOrderOneEncoding(DataSeries.NS_NextFragmentReferenceSequenceID);
        putExternalGzipEncoding(encodingStrategy, DataSeries.PD_padding);
        // the QQ data series is not used by this implementation when writing CRAMs
        putExternalRansOrderOneEncoding(DataSeries.QS_QualityScore);
        putExternalRansOrderOneEncoding(DataSeries.RG_ReadGroup);
        putExternalRansOrderZeroEncoding(DataSeries.RI_RefId);
        putExternalRansOrderOneEncoding(DataSeries.RL_ReadLength);
        putExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.RN_ReadName);
        putExternalGzipEncoding(encodingStrategy, DataSeries.RS_RefSkip);
        putExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.SC_SoftClip);
        // the TC data series is obsolete
        putExternalGzipEncoding(encodingStrategy, DataSeries.TL_TagIdList);
        // the TN data series is obsolete
        putExternalRansOrderOneEncoding(DataSeries.TS_InsertSize);
    }

    /**
     * Constructor used to discover an encoding map from a serialized CRAM stream.
     * @param inputStream the CRAM input stream to be consumed
     */
    public CompressionHeaderEncodingMap(final InputStream inputStream) {
        final int byteSize = ITF8.readUnsignedITF8(inputStream);
        final byte[] bytes = new byte[byteSize];
        InputStreamUtils.readFully(inputStream, bytes, 0, bytes.length);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final int mapSize = ITF8.readUnsignedITF8(buffer);

        for (int i = 0; i < mapSize; i++) {
            final String dataSeriesAbbreviation = new String(new byte[]{buffer.get(), buffer.get()});
            final DataSeries dataSeries = DataSeries.byCanonicalName(dataSeriesAbbreviation);

            final EncodingID id = EncodingID.values()[buffer.get()];
            final int paramLen = ITF8.readUnsignedITF8(buffer);
            final byte[] paramBytes = new byte[paramLen];
            buffer.get(paramBytes);

            // if TC, TN DataSeries are present, log a warning and ignore on CRAM read
            if (DATASERIES_NOT_READ_BY_HTSJDK.contains(dataSeries)) {
                LOG.warn("Ignoring obsolete CRAM dataseries: " + dataSeries.getCanonicalName());
            }
            else {

                // NOTE: the compression associated with this DataSeries is a property of the BLOCK in which it
                // resides, not of the encoding, so the externalCompressors map isn't populated when reading a
                // CRAM. The block data will be uncompressed before the codec ever sees it.
                encodingMap.put(dataSeries, new EncodingDescriptor(id, paramBytes));
            }
        }
    }

    /**
     * Add an external compressor for a tag block
     * @param tagId the tag as a content ID
     * @param compressor compressor to be used for this tag block
     */
    public void putTagBlockCompression(final int tagId, final ExternalCompressor compressor) {
        ValidationUtils.validateArg(
                Arrays.asList(DataSeries.values()).stream().noneMatch(ds -> ds.getExternalBlockContentId().intValue() == tagId),
                String.format("tagID %d overlaps with data series content ID", tagId));
        externalCompressors.put(tagId, compressor);
    }

    /**
     * Get the encoding params that should be used for a given DataSeries.
     * @param dataSeries
     * @return EncodingDescriptor for the DataSeries
     */
    public EncodingDescriptor getEncodingDescriptorForDataSeries(final DataSeries dataSeries) {
        return encodingMap.get(dataSeries);
    }

    /**
     * Get a list of all external IDs for this encoding map
     * @return list of all external IDs for this encoding map
     */
    public List<Integer> getExternalIDs() { return new ArrayList(externalCompressors.keySet()); }

    /**
     * Given a content ID, return a {@link Block} for that ID by obtaining the contents of the stream,
     * compressing it using the compressor for that contentID, and converting the result to a {@link Block}.
     * @param contentId contentID to use
     * @param outputStream stream to compress
     * @return Block containing the compressed contends of the stream
     */
    public Block createCompressedBlockForStream(final Integer contentId, final ByteArrayOutputStream outputStream) {
        final ExternalCompressor compressor = externalCompressors.get(contentId);
        final byte[] rawContent = outputStream.toByteArray();
        return Block.createExternalBlock(
                compressor.getMethod(),
                contentId,
                compressor.compress(rawContent),
                rawContent.length);
    }

    /**
     * Write the encoding map out to a CRAM Stream
     * @param outputStream stream to write
     * @throws IOException
     */
    public void write(final OutputStream outputStream) throws IOException {
        // encoding map:
        int size = 0;
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            // not all DataSeries are used by this implementation
            if (encodingMap.get(dataSeries).getEncodingID() != EncodingID.NULL) {
                size++;
            }
        }

        final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
        ITF8.writeUnsignedITF8(size, mapBuffer);
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            if (encodingMap.get(dataSeries).getEncodingID() == EncodingID.NULL) {
                // not all DataSeries are used by this implementation
                continue;
            }

            final String dataSeriesAbbreviation = dataSeries.getCanonicalName();
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(0));
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(1));

            final EncodingDescriptor params = encodingMap.get(dataSeries);
            mapBuffer.put((byte) (0xFF & params.getEncodingID().getId()));
            ITF8.writeUnsignedITF8(params.getEncodingParameters().length, mapBuffer);
            mapBuffer.put(params.getEncodingParameters());
        }
        mapBuffer.flip();
        final byte[] mapBytes = new byte[mapBuffer.limit()];
        mapBuffer.get(mapBytes);

        ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
        outputStream.write(mapBytes);
    }

    /**
     * Return the best external compressor to use for the provided byte array (compressor that results in the
     * smallest compressed size).
     *
     * Note that this does not necessarily mean this is the best compression to use for the source
     * data series, as it does not consider the size of the alphabet (2 byte int, 4 byte int) since
     * its only choosing from EXTERNAL compressors.
     *
     * @param data byte array to compress
     * @param encodingStrategy encoding strategy parameters to use
     * @return the best {@link ExternalCompressor} to use for this data
     */
    public ExternalCompressor getBestExternalCompressor(final byte[] data, final CRAMEncodingStrategy encodingStrategy) {
        final ExternalCompressor gzip = compressorCache.getCompressorForMethod(
                BlockCompressionMethod.GZIP,
                encodingStrategy.getGZIPCompressionLevel());
        final int gzipLen = gzip.compress(data).length;

        final ExternalCompressor rans0 = compressorCache.getCompressorForMethod(
                BlockCompressionMethod.RANS,
                RANS4x8Params.ORDER.ZERO.ordinal());
        final int rans0Len = rans0.compress(data).length;

        final ExternalCompressor rans1 = compressorCache.getCompressorForMethod(
                BlockCompressionMethod.RANS,
                RANS4x8Params.ORDER.ONE.ordinal());
        final int rans1Len = rans1.compress(data).length;

        // find the best of general purpose codecs:
        final int minLen = Math.min(gzipLen, Math.min(rans0Len, rans1Len));
        if (minLen == rans0Len) {
            return rans0;
        } else if (minLen == rans1Len) {
            return rans1;
        } else {
            return gzip;
        }
    }

    // Visible for testing, because without this we have no way to unit test round-tripping an
    // encoding map that contains the handful of data series that htsjdk generally doesn't use
    // when writing, since there is no code to add those data series to the map as part of the
    // CRAM write implementation.
    //VisibleForTesting
    void putExternalEncoding(final DataSeries dataSeries, final ExternalCompressor compressor) {
        // This spins up a CRAMEncoding temporarily in order to retrieve its EncodingDescriptor.
        // In reality, the encoding descriptor/parameters for each of these external encoding
        // classes happens to be identical and are all interchangeable (they only contain the
        // content ID and nothing else, no matter  what the data series type), but that's
        // accidental and could change, so don't rely on it.
        final int blockContentID = dataSeries.getExternalBlockContentId();
        CRAMEncoding<?> cramEncoding;
        switch (dataSeries.getType()) {
            case BYTE:
                cramEncoding = new ExternalByteEncoding(blockContentID);
                break;
            case INT:
                cramEncoding = new ExternalIntegerEncoding(blockContentID);
                break;
            case LONG:
                cramEncoding = new ExternalLongEncoding(blockContentID);
                break;
            case BYTE_ARRAY:
                cramEncoding = new ExternalByteEncoding(blockContentID);
                break;
            default:
                throw new CRAMException("Unknown data series value type");
        }
        putExternalEncoding(dataSeries, cramEncoding.toEncodingDescriptor(), compressor);
    }

    /**
     * Puts a CORE encoding into the encoding map, replacing any existing encoding for this data series with
     * the new encoding, and removing any compressor that was previously registered for the corresponding
     * content id.
     * @param dataSeries data series to add
     * @param encodingDescriptor encoding descriptor to use
     */
    //VisibleForTesting
    void putCoreEncoding(final DataSeries dataSeries, final EncodingDescriptor encodingDescriptor) {
        ValidationUtils.validateArg(!encodingDescriptor.getEncodingID().isExternalEncoding(),
                "Attempt to use an external encoding as a core encoding");
        if (externalCompressors.containsKey(dataSeries.getExternalBlockContentId())) {
            externalCompressors.remove(dataSeries.getExternalBlockContentId());
        }
        putEncoding(dataSeries, encodingDescriptor);
    }

    /**
     * Puts an encoding, either EXTERNAL or CORE, into the encoding map, replacing any existing encoding
     * for this data series with the new encoding, and removing any compressor that might also be registered.
     * For external encodings, the caller should establish the corresponding compressor for this encoding
     * AFTER this call returns by calling .
     * @param dataSeries
     * @param encodingDescriptor
     */
    private void putEncoding(final DataSeries dataSeries, final EncodingDescriptor encodingDescriptor) {
        encodingMap.put(dataSeries, encodingDescriptor);
    }

    // add an external encoding and corresponding compressor
    public void putExternalEncoding(final DataSeries dataSeries,
                                    final EncodingDescriptor encodingDescriptor,
                                    final ExternalCompressor compressor) {
        ValidationUtils.validateArg(encodingDescriptor.getEncodingID().isExternalEncoding(),
                "Attempt to use an external encoding as a core encoding");
        putEncoding(dataSeries, encodingDescriptor);
        // add the external compressor after the call to putEncoding, since putEncoding removes
        // any compressor that is already registered
        externalCompressors.put(dataSeries.getExternalBlockContentId(), compressor);
    }

    private void putExternalByteArrayStopTabGzipEncoding(final CRAMEncodingStrategy encodingStrategy, final DataSeries dataSeries) {
        putExternalEncoding(dataSeries,
                new ByteArrayStopEncoding((byte) '\t', dataSeries.getExternalBlockContentId()).toEncodingDescriptor(),
                compressorCache.getCompressorForMethod(BlockCompressionMethod.GZIP, encodingStrategy.getGZIPCompressionLevel()));
    }

    // add an external encoding appropriate for the dataSeries value type, with a GZIP compressor
    private void putExternalGzipEncoding(final CRAMEncodingStrategy encodingStrategy, final DataSeries dataSeries) {
        putExternalEncoding(
                dataSeries,
                compressorCache.getCompressorForMethod(BlockCompressionMethod.GZIP, encodingStrategy.getGZIPCompressionLevel()));
    }

    // add an external encoding appropriate for the dataSeries value type, with a RANS order 1 compressor
    private void putExternalRansOrderOneEncoding(final DataSeries dataSeries) {
        putExternalEncoding(
                dataSeries,
                compressorCache.getCompressorForMethod(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ONE.ordinal()));
    }

    // add an external encoding appropriate for the dataSeries value type, with a RANS order 0 compressor
    private void putExternalRansOrderZeroEncoding(final DataSeries dataSeries) {
        putExternalEncoding(
                dataSeries,
                compressorCache.getCompressorForMethod(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ZERO.ordinal()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CompressionHeaderEncodingMap that = (CompressionHeaderEncodingMap) o;

        if (!this.encodingMap.equals(that.encodingMap)) return false;
        return this.externalCompressors.equals(that.externalCompressors);
    }

    @Override
    public int hashCode() {
        int result = encodingMap.hashCode();
        result = 31 * result + externalCompressors.hashCode();
        return result;
    }

}