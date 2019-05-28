package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.encoding.external.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.*;

public class CompressionHeaderEncodingMap {

    private Map<DataSeries, EncodingParams> encodingMap = new TreeMap<>();

    //TODO: are the keys for these (externalIDs and externalCompressors) always the same ? consolidate ?
    private final Map<Integer, ExternalCompressor> externalCompressors = new HashMap<>();
    private List<Integer> externalIds= new ArrayList<>();

    public CompressionHeaderEncodingMap() {
        encodingMap = new TreeMap<>();

        addExternalRansOrderZeroEncoding(DataSeries.AP_AlignmentPositionOffset);
        addExternalRansOrderOneEncoding(DataSeries.BA_Base);
        // BB is not used
        addExternalRansOrderOneEncoding(DataSeries.BF_BitFlags);
        addExternalGzipEncoding(DataSeries.BS_BaseSubstitutionCode);
        addExternalRansOrderOneEncoding(DataSeries.CF_CompressionBitFlags);
        addExternalGzipEncoding(DataSeries.DL_DeletionLength);
        addExternalGzipEncoding(DataSeries.FC_FeatureCode);
        addExternalGzipEncoding(DataSeries.FN_NumberOfReadFeatures);
        addExternalGzipEncoding(DataSeries.FP_FeaturePosition);
        addExternalGzipEncoding(DataSeries.HC_HardClip);
        addExternalByteArrayStopTabGzipEncoding(DataSeries.IN_Insertion);
        addExternalGzipEncoding(DataSeries.MF_MateBitFlags);
        addExternalGzipEncoding(DataSeries.MQ_MappingQualityScore);
        addExternalGzipEncoding(DataSeries.NF_RecordsToNextFragment);
        addExternalGzipEncoding(DataSeries.NP_NextFragmentAlignmentStart);
        addExternalRansOrderOneEncoding(DataSeries.NS_NextFragmentReferenceSequenceID);
        addExternalGzipEncoding(DataSeries.PD_padding);
        // QQ is not used
        addExternalRansOrderOneEncoding(DataSeries.QS_QualityScore);
        addExternalRansOrderOneEncoding(DataSeries.RG_ReadGroup);
        addExternalRansOrderZeroEncoding(DataSeries.RI_RefId);
        addExternalRansOrderOneEncoding(DataSeries.RL_ReadLength);
        addExternalByteArrayStopTabGzipEncoding(DataSeries.RN_ReadName);
        addExternalGzipEncoding(DataSeries.RS_RefSkip);
        addExternalByteArrayStopTabGzipEncoding(DataSeries.SC_SoftClip);
        addExternalGzipEncoding(DataSeries.TC_TagCount);
        addExternalGzipEncoding(DataSeries.TL_TagIdList);
        addExternalGzipEncoding(DataSeries.TN_TagNameAndType);
        addExternalRansOrderOneEncoding(DataSeries.TS_InsertSize);
    }

    public CompressionHeaderEncodingMap(final InputStream inputStream) {
        // encoding map:
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

            encodingMap.put(dataSeries, new EncodingParams(id, paramBytes));
        }
    }

    //TODO: remove this and delegate here ?
    public EncodingParams getEncodingParamsForDataSeries(final DataSeries dataSeries) {
        return encodingMap.get(dataSeries);
    }

    // TODO: remove these and delegate here ?
    public Map<Integer, ExternalCompressor> getExternalCompresssors() { return externalCompressors; }
    public List<Integer> getExternalIDs() { return externalIds; }
    public Collection<EncodingParams> getAllEncodingParams() { return encodingMap.values(); }

    public void write(final OutputStream outputStream) throws IOException {
        // encoding map:
        int size = 0;
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            if (encodingMap.get(dataSeries).id != EncodingID.NULL)
                size++;
        }

        final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
        ITF8.writeUnsignedITF8(size, mapBuffer);
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            if (encodingMap.get(dataSeries).id == EncodingID.NULL)
                continue;

            final String dataSeriesAbbreviation = dataSeries.getCanonicalName();
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(0));
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(1));

            final EncodingParams params = encodingMap.get(dataSeries);
            mapBuffer.put((byte) (0xFF & params.id.getId()));
            ITF8.writeUnsignedITF8(params.params.length, mapBuffer);
            mapBuffer.put(params.params);
        }
        mapBuffer.flip();
        final byte[] mapBytes = new byte[mapBuffer.limit()];
        mapBuffer.get(mapBytes);

        ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
        outputStream.write(mapBytes);
    }

    private void addExternalEncoding(final DataSeries dataSeries,
                                     final EncodingParams params,
                                     final ExternalCompressor compressor) {
        externalIds.add(dataSeries.getExternalBlockContentId());
        externalCompressors.put(dataSeries.getExternalBlockContentId(), compressor);
        encodingMap.put(dataSeries, params);
    }

    private void addExternalByteArrayStopTabGzipEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries,
                new ByteArrayStopEncoding((byte) '\t', dataSeries.getExternalBlockContentId()).toParam(),
                ExternalCompressor.createGZIP());
    }

    private void addExternalEncoding(final DataSeries dataSeries, final ExternalCompressor compressor) {
        // we need a concrete type; the choice of Byte is arbitrary.
        // params are equal for all External Encoding value types
        final EncodingParams params = new ExternalByteEncoding(dataSeries.getExternalBlockContentId()).toParam();
        addExternalEncoding(dataSeries, params, compressor);
    }

    private void addExternalGzipEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, ExternalCompressor.createGZIP());
    }

    private void addExternalRansOrderOneEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, ExternalCompressor.createRANS(RANS.ORDER.ONE));
    }

    private void addExternalRansOrderZeroEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, ExternalCompressor.createRANS(RANS.ORDER.ZERO));
    }

    void addTagEncoding(final int tagId, final ExternalCompressor compressor, final EncodingParams params) {
        externalIds.add(tagId);
        externalCompressors.put(tagId, compressor);
    }

}
