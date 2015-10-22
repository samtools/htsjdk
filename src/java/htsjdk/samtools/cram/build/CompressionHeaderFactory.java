/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.ByteArrayLenEncoding;
import htsjdk.samtools.cram.encoding.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.ExternalByteEncoding;
import htsjdk.samtools.cram.encoding.ExternalCompressor;
import htsjdk.samtools.cram.encoding.ExternalIntegerEncoding;
import htsjdk.samtools.cram.encoding.NullEncoding;
import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanIntegerEncoding;
import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanParamsCalculator;
import htsjdk.samtools.cram.encoding.rans.RANS;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.EncodingParams;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;
import htsjdk.samtools.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implementation of {@link htsjdk.samtools.cram.build.CramCompression} that mostly relies on GZIP and RANS.
 */
public class CompressionHeaderFactory implements CramCompression {
    private static final Log log = Log.getInstance(CompressionHeaderFactory.class);

    private final Map<Integer, EncodingDetails> bestEncodings = new HashMap<Integer, EncodingDetails>();

    /**
     * Decides on compression methods to use for the given records.
     *
     * @param records            the data to be compressed
     * @param substitutionMatrix a matrix of base substitution frequencies, can be null, in which case it is re-calculated.
     * @param sorted             if true the records are assumed to be sorted by alignment
     *                           position
     * @return {@link htsjdk.samtools.cram.structure.CompressionHeader} object describing the encoding chosen for the data
     */
    @Override
    public CompressionHeader buildCompressionHeader(final List<CramCompressionRecord> records,
                                                    final SubstitutionMatrix substitutionMatrix, final boolean sorted) {
        final CompressionHeader header = new CompressionHeader();
        header.externalIds = new ArrayList<Integer>();
        int exCounter = 0;

        final int baseID = exCounter++;
        header.externalIds.add(baseID);
        header.externalCompressors.put(baseID, ExternalCompressor.createRANS(RANS.ORDER.ONE));

        final int qualityScoreID = exCounter++;
        header.externalIds.add(qualityScoreID);
        header.externalCompressors.put(qualityScoreID, ExternalCompressor.createRANS(RANS.ORDER.ONE));

        final int readNameID = exCounter++;
        header.externalIds.add(readNameID);
        header.externalCompressors.put(readNameID, ExternalCompressor.createGZIP());

        header.encodingMap = new TreeMap<EncodingKey, EncodingParams>();
        for (final EncodingKey key : EncodingKey.values()) {
            header.encodingMap.put(key, NullEncoding.toParam());
        }

        header.tMap = new TreeMap<Integer, EncodingParams>();

        { // bit flags encoding:
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.BF_BitFlags, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // compression bit flags encoding:
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.CF_CompressionBitFlags, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // ref id:
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ZERO));
            header.encodingMap.put(EncodingKey.RI_RefId, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // read length encoding:
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.RL_ReadLength, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // alignment offset:
            if (sorted) {
                header.APDelta = true;

                final int aStartID = exCounter++;
                header.encodingMap.put(EncodingKey.AP_AlignmentPositionOffset,
                        ExternalIntegerEncoding.toParam(aStartID));
                header.externalIds.add(aStartID);
                header.externalCompressors.put(aStartID, ExternalCompressor.createRANS(RANS.ORDER.ZERO));
            } else {
                final int aStartID = exCounter++;
                header.APDelta = false;
                header.encodingMap.put(EncodingKey.AP_AlignmentPositionOffset,
                        ExternalIntegerEncoding.toParam(aStartID));
                header.externalIds.add(aStartID);
                header.externalCompressors.put(aStartID, ExternalCompressor.createRANS(RANS.ORDER.ZERO));
                log.debug("Assigned external id to alignment starts: " + aStartID);
            }
        }

        { // read group
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.RG_ReadGroup, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // read name encoding:
            header.externalIds.add(readNameID);
            header.externalCompressors.put(readNameID, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.RN_ReadName, ByteArrayStopEncoding.toParam((byte) '\t', readNameID));
        }

        { // records to next fragment
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.NF_RecordsToNextFragment, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // tag count
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.TC_TagCount, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // tag name and type
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.TN_TagNameAndType, ExternalIntegerEncoding.toParam(exCounter++));
        }

        {

            final Comparator<ReadTag> comparator = new Comparator<ReadTag>() {

                @Override
                public int compare(final ReadTag o1, final ReadTag o2) {
                    return o1.keyType3BytesAsInt - o2.keyType3BytesAsInt;
                }
            };

            final Comparator<byte[]> baComparator = new Comparator<byte[]>() {

                @Override
                public int compare(final byte[] o1, final byte[] o2) {
                    if (o1.length - o2.length != 0) {
                        return o1.length - o2.length;
                    }

                    for (int i = 0; i < o1.length; i++) {
                        if (o1[i] != o2[i]) {
                            return o1[i] - o2[i];
                        }
                    }

                    return 0;
                }
            };

            final Map<byte[], MutableInt> map = new TreeMap<byte[], MutableInt>(baComparator);
            final MutableInt noTagCounter = new MutableInt();
            map.put(new byte[0], noTagCounter);
            for (final CramCompressionRecord record : records) {
                if (record.tags == null) {
                    noTagCounter.value++;
                    record.tagIdsIndex = noTagCounter;
                    continue;
                }

                Arrays.sort(record.tags, comparator);
                record.tagIds = new byte[record.tags.length * 3];

                int tagIndex = 0;
                for (int i = 0; i < record.tags.length; i++) {
                    record.tagIds[i * 3] = (byte) record.tags[tagIndex].keyType3Bytes.charAt(0);
                    record.tagIds[i * 3 + 1] = (byte) record.tags[tagIndex].keyType3Bytes.charAt(1);
                    record.tagIds[i * 3 + 2] = (byte) record.tags[tagIndex].keyType3Bytes.charAt(2);
                    tagIndex++;
                }

                MutableInt count = map.get(record.tagIds);
                if (count == null) {
                    count = new MutableInt();
                    map.put(record.tagIds, count);
                }
                count.value++;
                record.tagIdsIndex = count;
            }

            final byte[][][] dic = new byte[map.size()][][];
            int i = 0;
            for (final byte[] idsAsBytes : map.keySet()) {
                final int nofIds = idsAsBytes.length / 3;
                dic[i] = new byte[nofIds][];
                for (int j = 0; j < idsAsBytes.length; ) {
                    final int idIndex = j / 3;
                    dic[i][idIndex] = new byte[3];
                    dic[i][idIndex][0] = idsAsBytes[j++];
                    dic[i][idIndex][1] = idsAsBytes[j++];
                    dic[i][idIndex][2] = idsAsBytes[j++];
                }
                map.get(idsAsBytes).value = i++;
            }

            header.dictionary = dic;

            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.TL_TagIdList, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // tag values
            final Set<Integer> tagIdSet = new HashSet<Integer>();

            for (final CramCompressionRecord record : records) {
                if (record.tags == null) {
                    continue;
                }

                for (final ReadTag tag : record.tags) {
                    tagIdSet.add(tag.keyType3BytesAsInt);
                }
            }

            for (final int id : tagIdSet) {
                final int externalID;
                if (bestEncodings.containsKey(id)) {
                    final EncodingDetails e = bestEncodings.get(id);
                    externalID = id;
                    header.externalIds.add(externalID);
                    header.externalCompressors.put(externalID, e.compressor);
                    header.tMap.put(id, e.params);
                    continue;
                }

                externalID = id;
                final EncodingDetails e = buildEncodingForTag(records, id, externalID);
                header.externalIds.add(externalID);
                header.externalCompressors.put(externalID, e.compressor);
                header.tMap.put(id, e.params);
                bestEncodings.put(id, e);
            }
        }

        { // number of read features
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.FN_NumberOfReadFeatures, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // feature position
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.FP_FeaturePosition, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // feature code
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.FC_FeatureCode, ExternalByteEncoding.toParam(exCounter++));
        }

        { // bases:
            header.encodingMap.put(EncodingKey.BA_Base, ExternalByteEncoding.toParam(baseID));
        }

        { // quality scores:
            header.encodingMap.put(EncodingKey.QS_QualityScore, ExternalByteEncoding.toParam(qualityScoreID));
        }

        { // base substitution code
            if (substitutionMatrix == null) {
                final long[][] frequencies = new long[200][200];
                for (final CramCompressionRecord record : records) {
                    if (record.readFeatures != null) {
                        for (final ReadFeature readFeature : record.readFeatures) {
                            if (readFeature.getOperator() == Substitution.operator) {
                                final Substitution substitution = ((Substitution) readFeature);
                                final byte refBase = substitution.getReferenceBase();
                                final byte base = substitution.getBase();
                                frequencies[refBase][base]++;
                            }
                        }
                    }
                }

                header.substitutionMatrix = new SubstitutionMatrix(frequencies);
            } else {
                header.substitutionMatrix = substitutionMatrix;
            }

            for (final CramCompressionRecord record : records) {
                if (record.readFeatures != null) {
                    for (final ReadFeature recordFeature : record.readFeatures) {
                        if (recordFeature.getOperator() == Substitution.operator) {
                            final Substitution substitution = ((Substitution) recordFeature);
                            if (substitution.getCode() == -1) {
                                final byte refBase = substitution.getReferenceBase();
                                final byte base = substitution.getBase();
                                substitution.setCode(header.substitutionMatrix.code(refBase, base));
                            }
                        }
                    }
                }
            }

            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.BS_BaseSubstitutionCode, ExternalByteEncoding.toParam(exCounter++));
        }

        { // insertion bases
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.IN_Insertion, ByteArrayStopEncoding.toParam((byte) '\t', exCounter++));
        }

        { // insertion bases
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.SC_SoftClip, ByteArrayStopEncoding.toParam((byte) '\t', exCounter++));
        }

        { // deletion length
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.DL_DeletionLength, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // hard clip length
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.HC_HardClip, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // padding length
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.PD_padding, ExternalIntegerEncoding.toParam(exCounter++));

        }

        { // ref skip length
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.RS_RefSkip, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // mapping quality score
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.MQ_MappingQualityScore, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // mate bit flags
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.MF_MateBitFlags, ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // next fragment ref id
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.NS_NextFragmentReferenceSequenceID,
                    ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // next fragment alignment start
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createGZIP());
            header.encodingMap.put(EncodingKey.NP_NextFragmentAlignmentStart,
                    ExternalIntegerEncoding.toParam(exCounter++));
        }

        { // template size
            header.externalIds.add(exCounter);
            header.externalCompressors.put(exCounter, ExternalCompressor.createRANS(RANS.ORDER.ONE));
            header.encodingMap.put(EncodingKey.TS_InsetSize, ExternalIntegerEncoding.toParam(exCounter));
        }

        return header;
    }

    EncodingDetails buildEncodingForTag(final List<CramCompressionRecord> records, final int id, final int externalID) {
        @SuppressWarnings("resource") final
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 1024);
        final Map<Integer, MutableInt> byteSizes = new HashMap<Integer, MutableInt>();
        final byte type = (byte) (id & 0xFF);
        for (final CramCompressionRecord record : records) {
            if (record.tags == null) {
                continue;
            }

            for (final ReadTag tag : record.tags) {
                if (tag.keyType3BytesAsInt != id) {
                    continue;
                }
                final byte[] data = tag.getValueAsByteArray();
                try {
                    os.write(data);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
                if (!byteSizes.containsKey(data.length)) {
                    byteSizes.put(data.length, new MutableInt());
                }
                byteSizes.get(data.length).value++;
            }
        }

        final byte[] data = os.toByteArray();

        final ExternalCompressor gzip = ExternalCompressor.createGZIP();
        final int gzipLen = gzip.compress(data).length;

        final ExternalCompressor rans0 = ExternalCompressor.createRANS(RANS.ORDER.ZERO);
        final int rans0Len = rans0.compress(data).length;

        final ExternalCompressor rans1 = ExternalCompressor.createRANS(RANS.ORDER.ONE);
        final int rans1Len = rans1.compress(data).length;

        final EncodingDetails d = new EncodingDetails();

        final int minLen = Math.min(gzipLen, Math.min(rans0Len, rans1Len));
        if (minLen == rans0Len) {
            d.compressor = rans0;
        } else if (minLen == rans1Len) {
            d.compressor = rans1;
        } else {
            d.compressor = gzip;
        }

        switch (type) {
            case 'A':
            case 'I':
            case 'i':
            case 's':
            case 'S':
            case 'c':
            case 'C':
            case 'f':
                final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
                calculator.add(getTagValueByteSize(type));
                calculator.calculate();
                d.params = ByteArrayLenEncoding.toParam(HuffmanIntegerEncoding.toParam(calculator.getValues(), calculator.getBitLens()),
                        ExternalByteEncoding.toParam(externalID));
                return d;

            default:
                break;
        }

        int maxSize = 0;
        int minSize = 0;
        for (final int size : byteSizes.keySet()) {
            maxSize = Math.max(maxSize, size);
            minSize = Math.min(minSize, size);
        }

        if (type != 'Z') {
            d.params = ByteArrayLenEncoding.toParam(ExternalIntegerEncoding.toParam(externalID),
                    ExternalByteEncoding.toParam(externalID));
            return d;
        }

        final int minSize_threshold_ForByteArrayStopEncoding = 200;
        final int byteSizes_sizeThreshold_ForByteArrayStopEncoding = 50;
        if (byteSizes.size() > byteSizes_sizeThreshold_ForByteArrayStopEncoding
                || minSize > minSize_threshold_ForByteArrayStopEncoding) {
            d.params = ByteArrayStopEncoding.toParam((byte) '\t', externalID);
        } else {
            final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
            for (final int size : byteSizes.keySet()) {
                for (int count = 0; count < byteSizes.get(size).value; count++) {
                    c.add(size);
                }
            }
            c.calculate();
            d.params = ByteArrayLenEncoding.toParam(HuffmanIntegerEncoding.toParam(c.getValues(), c.getBitLens()),
                    ExternalByteEncoding.toParam(externalID));
        }

        return d;
    }

    private int getTagValueByteSize(final byte type) {
        switch (type) {
            case 'A':
                return 1;
            case 'I':
                return 4;
            case 'i':
                return 4;
            case 's':
                return 2;
            case 'S':
                return 2;
            case 'c':
                return 1;
            case 'C':
                return 1;
            case 'f':
                return 4;
            default:
                throw new SAMFormatException("Unrecognized tag type: " + type);
        }
    }

    private static class EncodingDetails {
        ExternalCompressor compressor;
        EncodingParams params;

        public EncodingDetails() {
        }
    }
}
