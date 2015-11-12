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
                                                    SubstitutionMatrix substitutionMatrix, final boolean sorted) {

        final CompressionHeaderBuilder builder = new CompressionHeaderBuilder(sorted);

        builder.addExternalByteRansOrderOneEncoding(EncodingKey.BA_Base);
        builder.addExternalByteRansOrderOneEncoding(EncodingKey.QS_QualityScore);
        builder.addExternalByteArrayStopTabGzipEncoding(EncodingKey.RN_ReadName);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.BF_BitFlags);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.CF_CompressionBitFlags);
        builder.addExternalIntegerRansOrderZeroEncoding(EncodingKey.RI_RefId);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.RL_ReadLength);

        builder.addExternalIntegerRansOrderZeroEncoding(EncodingKey.AP_AlignmentPositionOffset);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.RG_ReadGroup);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.NF_RecordsToNextFragment);
        builder.addExternalIntegerGzipEncoding(EncodingKey.TC_TagCount);
        builder.addExternalIntegerGzipEncoding(EncodingKey.TN_TagNameAndType);

        builder.addExternalIntegerGzipEncoding(EncodingKey.FN_NumberOfReadFeatures);
        builder.addExternalIntegerGzipEncoding(EncodingKey.FP_FeaturePosition);
        builder.addExternalByteGzipEncoding(EncodingKey.FC_FeatureCode);
        builder.addExternalByteGzipEncoding(EncodingKey.BS_BaseSubstitutionCode);

        builder.addExternalByteArrayStopTabGzipEncoding(EncodingKey.IN_Insertion);
        builder.addExternalByteArrayStopTabGzipEncoding(EncodingKey.SC_SoftClip);

        builder.addExternalIntegerGzipEncoding(EncodingKey.DL_DeletionLength);
        builder.addExternalIntegerGzipEncoding(EncodingKey.HC_HardClip);
        builder.addExternalIntegerGzipEncoding(EncodingKey.PD_padding);
        builder.addExternalIntegerGzipEncoding(EncodingKey.RS_RefSkip);
        builder.addExternalIntegerGzipEncoding(EncodingKey.MQ_MappingQualityScore);
        builder.addExternalIntegerGzipEncoding(EncodingKey.MF_MateBitFlags);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.NS_NextFragmentReferenceSequenceID);
        builder.addExternalIntegerGzipEncoding(EncodingKey.NP_NextFragmentAlignmentStart);
        builder.addExternalIntegerRansOrderOneEncoding(EncodingKey.TS_InsetSize);

        builder.setTagIdDictionary(buildTagIdDictionary(records));
        builder.addExternalIntegerEncoding(EncodingKey.TL_TagIdList, ExternalCompressor.createGZIP());
        buildTagEncodings(records, builder);

        if (substitutionMatrix == null) {
            substitutionMatrix = new SubstitutionMatrix(buildFrequencies(records));
            updateSubstitutionCodes(records, substitutionMatrix);
        }
        builder.setSubstitutionMatrix(substitutionMatrix);
        return builder.getHeader();
    }

    /**
     * Iterate over the records and for each tag found come up with an encoding. Tag encodings are registered via the builder.
     *
     * @param records CRAM records holding the tags to be encoded
     * @param builder compression header builder to register encodings
     */
    private void buildTagEncodings(final List<CramCompressionRecord> records, final CompressionHeaderBuilder builder) {
        final Set<Integer> tagIdSet = new HashSet<Integer>();

        for (final CramCompressionRecord record : records) {
            if (record.tags == null || record.tags.length == 0) {
                continue;
            }

            for (final ReadTag tag : record.tags) {
                tagIdSet.add(tag.keyType3BytesAsInt);
            }
        }

        for (final int id : tagIdSet) {
            if (bestEncodings.containsKey(id)) {
                builder.addTagEncoding(id, bestEncodings.get(id));
            } else {
                final EncodingDetails e = buildEncodingForTag(records, id);
                builder.addTagEncoding(id, e);
                bestEncodings.put(id, e);
            }
        }
    }

    /**
     * Given the records update the substitution matrix with actual substitution codes.
     *
     * @param records
     * @param substitutionMatrix
     */
    private static void updateSubstitutionCodes(final List<CramCompressionRecord> records, final SubstitutionMatrix substitutionMatrix) {
        for (final CramCompressionRecord record : records) {
            if (record.readFeatures != null) {
                for (final ReadFeature recordFeature : record.readFeatures) {
                    if (recordFeature.getOperator() == Substitution.operator) {
                        final Substitution substitution = ((Substitution) recordFeature);
                        if (substitution.getCode() == -1) {
                            final byte refBase = substitution.getReferenceBase();
                            final byte base = substitution.getBase();
                            substitution.setCode(substitutionMatrix.code(refBase, base));
                        }
                    }
                }
            }
        }
    }

    /**
     * Build an array of substitution frequencies for the given CRAM records.
     *
     * @param records CRAM records to scan
     * @return a 2D array of frequencies, see {@link htsjdk.samtools.cram.structure.SubstitutionMatrix}
     */
    private static long[][] buildFrequencies(final List<CramCompressionRecord> records) {
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
        return frequencies;
    }

    /**
     * Build a dictionary of tag ids.
     *
     * @param records records holding the tags
     * @return a 3D byte array: a set of unique lists of tag ids.
     */
    private static byte[][][] buildTagIdDictionary(final List<CramCompressionRecord> records) {
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

        final byte[][][] dictionary = new byte[map.size()][][];
        int i = 0;
        for (final byte[] idsAsBytes : map.keySet()) {
            final int nofIds = idsAsBytes.length / 3;
            dictionary[i] = new byte[nofIds][];
            for (int j = 0; j < idsAsBytes.length; ) {
                final int idIndex = j / 3;
                dictionary[i][idIndex] = new byte[3];
                dictionary[i][idIndex][0] = idsAsBytes[j++];
                dictionary[i][idIndex][1] = idsAsBytes[j++];
                dictionary[i][idIndex][2] = idsAsBytes[j++];
            }
            map.get(idsAsBytes).value = i++;
        }
        return dictionary;
    }

    /**
     * Build an encoding for a specific tag for given records.
     *
     * @param records CRAM records holding the tags
     * @param tagID   an integer id of the tag
     * @return an encoding for the tag
     */
    private EncodingDetails buildEncodingForTag(final List<CramCompressionRecord> records, final int tagID) {
        @SuppressWarnings("resource") final
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 1024);
        final Map<Integer, MutableInt> byteSizes = new HashMap<Integer, MutableInt>();
        final byte type = (byte) (tagID & 0xFF);
        for (final CramCompressionRecord record : records) {
            if (record.tags == null) {
                continue;
            }

            for (final ReadTag tag : record.tags) {
                if (tag.keyType3BytesAsInt != tagID) {
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
                        ExternalByteEncoding.toParam(tagID));
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
            d.params = ByteArrayLenEncoding.toParam(ExternalIntegerEncoding.toParam(tagID),
                    ExternalByteEncoding.toParam(tagID));
            return d;
        }

        final int minSize_threshold_ForByteArrayStopEncoding = 200;
        final int byteSizes_sizeThreshold_ForByteArrayStopEncoding = 50;
        if (byteSizes.size() > byteSizes_sizeThreshold_ForByteArrayStopEncoding
                || minSize > minSize_threshold_ForByteArrayStopEncoding) {
            d.params = ByteArrayStopEncoding.toParam((byte) '\t', tagID);
        } else {
            final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
            for (final int size : byteSizes.keySet()) {
                for (int count = 0; count < byteSizes.get(size).value; count++) {
                    c.add(size);
                }
            }
            c.calculate();
            d.params = ByteArrayLenEncoding.toParam(HuffmanIntegerEncoding.toParam(c.getValues(), c.getBitLens()),
                    ExternalByteEncoding.toParam(tagID));
        }

        return d;
    }

    /**
     * Return byte size of tag value identified by its type.
     *
     * @param type tag type, like 'i', 'I' etc.
     * @return byte size of tag value
     */
    private static int getTagValueByteSize(final byte type) {
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

    /**
     * A combination of external compressor and encoding params.
     * This is all that is needed to encode a data series.
     */
    private static class EncodingDetails {
        ExternalCompressor compressor;
        EncodingParams params;

        public EncodingDetails() {
        }
    }

    /**
     * A helper class to build {@link htsjdk.samtools.cram.structure.CompressionHeader} object.
     */
    private static class CompressionHeaderBuilder {
        private final CompressionHeader header;
        private int externalBlockCounter;

        CompressionHeaderBuilder(final boolean sorted) {
            header = new CompressionHeader();
            header.externalIds = new ArrayList<Integer>();
            header.tMap = new TreeMap<Integer, EncodingParams>();

            header.encodingMap = new TreeMap<EncodingKey, EncodingParams>();
            for (final EncodingKey key : EncodingKey.values()) {
                header.encodingMap.put(key, NullEncoding.toParam());
            }

            externalBlockCounter = 0;
            header.APDelta = sorted;
        }

        CompressionHeader getHeader() {
            return header;
        }

        void addExternalEncoding(final EncodingKey encodingKey, EncodingParams params, final ExternalCompressor compressor) {
            header.externalIds.add(externalBlockCounter);
            header.externalCompressors.put(externalBlockCounter, compressor);
            header.encodingMap.put(encodingKey, params);
            externalBlockCounter++;
        }

        void addExternalByteArrayStopTabGzipEncoding(final EncodingKey encodingKey) {
            addExternalEncoding(encodingKey, ByteArrayStopEncoding.toParam((byte)'\t', externalBlockCounter), ExternalCompressor.createGZIP());
        }

        void addExternalIntegerEncoding(final EncodingKey encodingKey, final ExternalCompressor compressor) {
            addExternalEncoding(encodingKey, ExternalIntegerEncoding.toParam(externalBlockCounter), compressor);
        }

        void addExternalIntegerGzipEncoding(final EncodingKey encodingKey) {
            addExternalEncoding(encodingKey, ExternalIntegerEncoding.toParam(externalBlockCounter), ExternalCompressor.createGZIP());
        }

        void addExternalByteEncoding(final EncodingKey encodingKey, final ExternalCompressor compressor) {
            addExternalEncoding(encodingKey, ExternalByteEncoding.toParam(externalBlockCounter), compressor);
        }

        void addExternalByteGzipEncoding(final EncodingKey encodingKey) {
            addExternalEncoding(encodingKey, ExternalByteEncoding.toParam(externalBlockCounter), ExternalCompressor.createGZIP());
        }

        void addExternalByteRansOrderOneEncoding(final EncodingKey encodingKey) {
            addExternalEncoding(encodingKey, ExternalByteEncoding.toParam(externalBlockCounter), ExternalCompressor.createRANS(RANS.ORDER.ONE));
        }


        void addExternalIntegerRansOrderOneEncoding(final EncodingKey encodingKey) {
            addExternalIntegerEncoding(encodingKey, ExternalCompressor.createRANS(RANS.ORDER.ONE));
        }

        void addExternalIntegerRansOrderZeroEncoding(final EncodingKey encodingKey) {
            addExternalIntegerEncoding(encodingKey, ExternalCompressor.createRANS(RANS.ORDER.ZERO));
        }


        void addTagEncoding(final int tagId, final EncodingDetails encodingDetails) {
            header.externalIds.add(tagId);
            header.externalCompressors.put(tagId, encodingDetails.compressor);
            header.tMap.put(tagId, encodingDetails.params);
        }

        void setTagIdDictionary(final byte[][][] dictionary) {
            header.dictionary = dictionary;
        }

        void setSubstitutionMatrix(final SubstitutionMatrix substitutionMatrix) {
            header.substitutionMatrix = substitutionMatrix;
        }
    }
}
