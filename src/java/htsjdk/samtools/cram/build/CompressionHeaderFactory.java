/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.encoding.ByteArrayLenEncoding;
import htsjdk.samtools.cram.encoding.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.encoding.ExternalByteArrayEncoding;
import htsjdk.samtools.cram.encoding.ExternalByteEncoding;
import htsjdk.samtools.cram.encoding.ExternalCompressor;
import htsjdk.samtools.cram.encoding.ExternalIntegerEncoding;
import htsjdk.samtools.cram.encoding.GammaIntegerEncoding;
import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanByteEncoding;
import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanIntegerEncoding;
import htsjdk.samtools.cram.encoding.NullEncoding;
import htsjdk.samtools.cram.encoding.SubexponentialIntegerEncoding;
import htsjdk.samtools.cram.encoding.huffman.HuffmanCode;
import htsjdk.samtools.cram.encoding.huffman.HuffmanTree;
import htsjdk.samtools.cram.encoding.rans.RANS;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.HardClip;
import htsjdk.samtools.cram.encoding.read_features.Padding;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.RefSkip;
import htsjdk.samtools.cram.encoding.read_features.Substitution;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.EncodingParams;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;
import htsjdk.samtools.util.Log;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class CompressionHeaderFactory {
    private static final Charset charset = Charset.forName("US-ASCII");
    private static final Log log = Log.getInstance(CompressionHeaderFactory.class);
    private static final int oqz = ReadTag.nameType3BytesToInt("OQ", 'Z');
    private static final int bqz = ReadTag.nameType3BytesToInt("OQ", 'Z');

    public CompressionHeader build(final List<CramCompressionRecord> records, final SubstitutionMatrix substitutionMatrix, final boolean sorted) {
        final CompressionHeader h = new CompressionHeader();
        h.externalIds = new ArrayList<Integer>();
        int exCounter = 0;

        final int baseID = exCounter++;
        h.externalIds.add(baseID);
        h.externalCompressors.put(baseID,
                ExternalCompressor.createRANS(RANS.ORDER.ONE));

        final int qualityScoreID = exCounter++;
        h.externalIds.add(qualityScoreID);
        h.externalCompressors.put(qualityScoreID,
                ExternalCompressor.createRANS(RANS.ORDER.ONE));

        final int readNameID = exCounter++;
        h.externalIds.add(readNameID);
        h.externalCompressors.put(readNameID, ExternalCompressor.createGZIP());

        final int mateInfoID = exCounter++;
        h.externalIds.add(mateInfoID);
        h.externalCompressors.put(mateInfoID,
                ExternalCompressor.createRANS(RANS.ORDER.ONE));

        h.eMap = new TreeMap<EncodingKey, EncodingParams>();
        for (final EncodingKey key : EncodingKey.values())
            h.eMap.put(key, NullEncoding.toParam());

        h.tMap = new TreeMap<Integer, EncodingParams>();

        { // bit flags encoding:
            getOptimalIntegerEncoding(h, EncodingKey.BF_BitFlags, 0, records);
        }

        { // compression bit flags encoding:
            getOptimalIntegerEncoding(h, EncodingKey.CF_CompressionBitFlags, 0, records);
        }

        { // ref id:

            getOptimalIntegerEncoding(h, EncodingKey.RI_RefId, -2, records);
        }

        { // read length encoding:
            getOptimalIntegerEncoding(h, EncodingKey.RL_ReadLength, 0, records);
        }

        { // alignment offset:
            if (sorted) { // alignment offset:
                h.AP_seriesDelta = true;
                getOptimalIntegerEncoding(h, EncodingKey.AP_AlignmentPositionOffset, 0, records);
            } else {
                final int aStartID = exCounter++;
                h.AP_seriesDelta = false;
                h.eMap.put(EncodingKey.AP_AlignmentPositionOffset,
                        ExternalIntegerEncoding.toParam(aStartID));
                h.externalIds.add(aStartID);
                h.externalCompressors.put(aStartID,
                        ExternalCompressor.createRANS(RANS.ORDER.ONE));
                log.debug("Assigned external id to alignment starts: " + aStartID);
            }
        }

        { // read group
            getOptimalIntegerEncoding(h, EncodingKey.RG_ReadGroup, -1, records);
        }

        { // read name encoding:
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                calculator.add(r.readName.length());
            calculator.calculate();

            h.eMap.put(EncodingKey.RN_ReadName, ByteArrayLenEncoding.toParam(
                    HuffmanIntegerEncoding.toParam(calculator.values(),
                            calculator.bitLens()), ExternalByteArrayEncoding
                            .toParam(readNameID)));
        }

        { // records to next fragment
            final IntegerEncodingCalculator calc = new IntegerEncodingCalculator(
                    EncodingKey.NF_RecordsToNextFragment.name(), 0);
            for (final CramCompressionRecord r : records) {
                if (r.isHasMateDownStream())
                    calc.addValue(r.recordsToNextFragment);
            }

            final Encoding<Integer> bestEncoding = calc.getBestEncoding();
            h.eMap.put(
                    EncodingKey.NF_RecordsToNextFragment,
                    new EncodingParams(bestEncoding.id(), bestEncoding
                            .toByteArray()));
        }

        { // tag count
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                calculator.add(r.tags == null ? 0 : r.tags.length);
            calculator.calculate();

            h.eMap.put(EncodingKey.TC_TagCount, HuffmanIntegerEncoding.toParam(
                    calculator.values(), calculator.bitLens()));
        }

        { // tag name and type
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records) {
                if (r.tags == null)
                    continue;
                for (final ReadTag tag : r.tags)
                    calculator.add(tag.keyType3BytesAsInt);
            }
            calculator.calculate();

            h.eMap.put(EncodingKey.TN_TagNameAndType, HuffmanIntegerEncoding
                    .toParam(calculator.values(), calculator.bitLens()));
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
                    if (o1.length - o2.length != 0)
                        return o1.length - o2.length;

                    for (int i = 0; i < o1.length; i++)
                        if (o1[i] != o2[i])
                            return o1[i] - o2[i];

                    return 0;
                }
            };

            final Map<byte[], MutableInt> map = new TreeMap<byte[], MutableInt>(baComparator);
            final MutableInt noTagCounter = new MutableInt();
            map.put(new byte[0], noTagCounter);
            for (final CramCompressionRecord r : records) {
                if (r.tags == null) {
                    noTagCounter.value++;
                    r.tagIdsIndex = noTagCounter;
                    continue;
                }

                Arrays.sort(r.tags, comparator);
                r.tagIds = new byte[r.tags.length * 3];

                int tagIndex = 0;
                for (int i = 0; i < r.tags.length; i++) {
                    r.tagIds[i * 3] = (byte) r.tags[tagIndex].keyType3Bytes.charAt(0);
                    r.tagIds[i * 3 + 1] = (byte) r.tags[tagIndex].keyType3Bytes.charAt(1);
                    r.tagIds[i * 3 + 2] = (byte) r.tags[tagIndex].keyType3Bytes.charAt(2);
                    tagIndex++;
                }

                MutableInt count = map.get(r.tagIds);
                if (count == null) {
                    count = new MutableInt();
                    map.put(r.tagIds, count);
                }
                count.value++;
                r.tagIdsIndex = count;
            }

            final byte[][][] dic = new byte[map.size()][][];
            int i = 0;
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
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
                calculator.add(i, map.get(idsAsBytes).value);
                map.get(idsAsBytes).value = i++;
            }

            calculator.calculate();
            h.eMap.put(EncodingKey.TL_TagIdList,
                    HuffmanIntegerEncoding.toParam(calculator.values(), calculator.bitLens()));
            h.dictionary = dic;
        }

        { // tag values
            @SuppressWarnings("UnnecessaryLocalVariable") final int unsortedTagValueExternalID = exCounter;
            h.externalIds.add(unsortedTagValueExternalID);
            h.externalCompressors.put(unsortedTagValueExternalID,
                    ExternalCompressor.createRANS(RANS.ORDER.ONE));

            final Set<Integer> tagIdSet = new HashSet<Integer>();
            for (final CramCompressionRecord r : records) {
                if (r.tags == null)
                    continue;

                for (final ReadTag tag : r.tags)
                    tagIdSet.add(tag.keyType3BytesAsInt);
            }

            for (final int id : tagIdSet) {
                final int externalID;
                final byte type = (byte) (id & 0xFF);
                switch (type) {
                    case 'Z':
                    case 'B':
                        externalID = id;
                        break;

                    default:
                        externalID = unsortedTagValueExternalID;
                        break;
                }

                h.externalIds.add(externalID);
                h.externalCompressors.put(externalID,
                        ExternalCompressor.createRANS(RANS.ORDER.ONE));
                h.tMap.put(id, ByteArrayLenEncoding.toParam(
                        ExternalIntegerEncoding.toParam(externalID),
                        ExternalByteEncoding.toParam(externalID)));
            }
        }

        { // number of read features
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                calculator.add(r.readFeatures == null ? 0 : r.readFeatures.size());
            calculator.calculate();

            h.eMap.put(EncodingKey.FN_NumberOfReadFeatures,
                    HuffmanIntegerEncoding.toParam(calculator.values(), calculator.bitLens()));
        }

        { // feature position
            final IntegerEncodingCalculator calc = new IntegerEncodingCalculator("read feature position", 0);
            for (final CramCompressionRecord r : records) {
                int prevPos = 0;
                if (r.readFeatures == null)
                    continue;
                for (final ReadFeature rf : r.readFeatures) {
                    calc.addValue(rf.getPosition() - prevPos);
                    prevPos = rf.getPosition();
                }
            }

            final Encoding<Integer> bestEncoding = calc.getBestEncoding();
            h.eMap.put(EncodingKey.FP_FeaturePosition,
                    new EncodingParams(bestEncoding.id(), bestEncoding.toByteArray()));
        }

        { // feature code
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures)
                        calculator.add(rf.getOperator());
            calculator.calculate();

            h.eMap.put(EncodingKey.FC_FeatureCode, HuffmanByteEncoding.toParam(
                    calculator.valuesAsBytes(), calculator.bitLens));
        }

        { // bases:
            h.eMap.put(EncodingKey.BA_Base, ExternalByteEncoding.toParam(baseID));
        }

        { // quality scores:
            h.eMap.put(EncodingKey.QS_QualityScore, ExternalByteEncoding.toParam(qualityScoreID));
        }

        { // base substitution code
            if (substitutionMatrix == null) {
                final long[][] frequencies = new long[200][200];
                for (final CramCompressionRecord r : records) {
                    if (r.readFeatures != null)
                        for (final ReadFeature rf : r.readFeatures)
                            if (rf.getOperator() == Substitution.operator) {
                                final Substitution s = ((Substitution) rf);
                                final byte refBase = s.getReferenceBase();
                                final byte base = s.getBase();
                                frequencies[refBase][base]++;
                            }
                }

                h.substitutionMatrix = new SubstitutionMatrix(frequencies);
            } else
                h.substitutionMatrix = substitutionMatrix;

            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures) {
                        if (rf.getOperator() == Substitution.operator) {
                            final Substitution s = ((Substitution) rf);
                            if (s.getCode() == -1) {
                                final byte refBase = s.getReferenceBase();
                                final byte base = s.getBase();
                                s.setCode(h.substitutionMatrix.code(refBase, base));
                            }
                            calculator.add(s.getCode());
                        }
                    }
            calculator.calculate();

            h.eMap.put(EncodingKey.BS_BaseSubstitutionCode,
                    HuffmanIntegerEncoding.toParam(calculator.values, calculator.bitLens));
        }

        { // insertion bases
            h.eMap.put(EncodingKey.IN_Insertion, ByteArrayStopEncoding.toParam((byte) 0, baseID));
        }

        { // insertion bases
            h.eMap.put(EncodingKey.SC_SoftClip, ByteArrayStopEncoding.toParam((byte) 0, baseID));
        }

        { // deletion length
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures)
                        if (rf.getOperator() == Deletion.operator)
                            calculator.add(((Deletion) rf).getLength());
            calculator.calculate();

            h.eMap.put(EncodingKey.DL_DeletionLength,
                    HuffmanIntegerEncoding.toParam(calculator.values, calculator.bitLens));
        }

        { // hard clip length
            final IntegerEncodingCalculator calculator = new IntegerEncodingCalculator(EncodingKey.HC_HardClip.name(), 0);
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures)
                        if (rf.getOperator() == HardClip.operator)
                            calculator.addValue(((HardClip) rf).getLength());

            final Encoding<Integer> bestEncoding = calculator.getBestEncoding();
            h.eMap.put(EncodingKey.HC_HardClip, new EncodingParams(bestEncoding.id(), bestEncoding.toByteArray()));
        }

        { // padding length
            final IntegerEncodingCalculator calculator = new IntegerEncodingCalculator(EncodingKey.PD_padding.name(), 0);
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures)
                        if (rf.getOperator() == Padding.operator)
                            calculator.addValue(((Padding) rf).getLength());

            final Encoding<Integer> bestEncoding = calculator.getBestEncoding();
            h.eMap.put(EncodingKey.PD_padding, new EncodingParams(bestEncoding.id(), bestEncoding.toByteArray()));

        }

        { // ref skip length
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (r.readFeatures != null)
                    for (final ReadFeature rf : r.readFeatures)
                        if (rf.getOperator() == RefSkip.operator)
                            calculator.add(((RefSkip) rf).getLength());
            calculator.calculate();

            h.eMap.put(EncodingKey.RS_RefSkip, HuffmanIntegerEncoding.toParam(calculator.values, calculator.bitLens));
        }

        { // mapping quality score
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (!r.isSegmentUnmapped())
                    calculator.add(r.mappingQuality);
            calculator.calculate();

            h.eMap.put(EncodingKey.MQ_MappingQualityScore,
                    HuffmanIntegerEncoding.toParam(calculator.values(), calculator.bitLens));
        }

        { // mate bit flags
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                calculator.add(r.getMateFlags());
            calculator.calculate();

            h.eMap.put(EncodingKey.MF_MateBitFlags,
                    HuffmanIntegerEncoding.toParam(calculator.values, calculator.bitLens));
        }

        { // next fragment ref id:
            final HuffmanParamsCalculator calculator = new HuffmanParamsCalculator();
            for (final CramCompressionRecord r : records)
                if (r.isDetached())
                    calculator.add(r.mateSequenceID);
            calculator.calculate();

            if (calculator.values.length == 0)
                h.eMap.put(EncodingKey.NS_NextFragmentReferenceSequenceID, NullEncoding.toParam());

            h.eMap.put(EncodingKey.NS_NextFragmentReferenceSequenceID,
                    HuffmanIntegerEncoding.toParam(calculator.values(),
                            calculator.bitLens()));
            log.debug("NS: "
                    + h.eMap.get(EncodingKey.NS_NextFragmentReferenceSequenceID));
        }

        { // next fragment alignment start
            h.eMap.put(EncodingKey.NP_NextFragmentAlignmentStart, ExternalIntegerEncoding.toParam(mateInfoID));
        }

        { // template size
            h.eMap.put(EncodingKey.TS_InsetSize, ExternalIntegerEncoding.toParam(mateInfoID));
        }

        return h;
    }

    private static int getValue(final EncodingKey key, final CramCompressionRecord r) {
        switch (key) {
            case AP_AlignmentPositionOffset:
                return r.alignmentDelta;
            case BF_BitFlags:
                return r.flags;
            case CF_CompressionBitFlags:
                return r.compressionFlags;
            case FN_NumberOfReadFeatures:
                return r.readFeatures == null ? 0 : r.readFeatures.size();
            case MF_MateBitFlags:
                return r.mateFlags;
            case MQ_MappingQualityScore:
                return r.mappingQuality;
            case NF_RecordsToNextFragment:
                return r.recordsToNextFragment;
            case NP_NextFragmentAlignmentStart:
                return r.mateAlignmentStart;
            case NS_NextFragmentReferenceSequenceID:
                return r.mateSequenceID;
            case RG_ReadGroup:
                return r.readGroupID;
            case RI_RefId:
                return r.sequenceId;
            case RL_ReadLength:
                return r.readLength;
            case TC_TagCount:
                return r.tags == null ? 0 : r.tags.length;

            default:
                throw new RuntimeException("Unexpected encoding key: " + key.name());
        }
    }

    private static void getOptimalIntegerEncoding(final CompressionHeader h, final EncodingKey key, final int minValue,
                                                        final List<CramCompressionRecord> records) {
        final IntegerEncodingCalculator calc = new IntegerEncodingCalculator(key.name(), minValue);
        for (final CramCompressionRecord r : records) {
            final int value = getValue(key, r);
            calc.addValue(value);
        }

        final Encoding<Integer> bestEncoding = calc.getBestEncoding();
        h.eMap.put(key, new EncodingParams(bestEncoding.id(), bestEncoding.toByteArray()));
    }

    private static class BitCode implements Comparable<BitCode> {
        final int value;
        final int len;

        public BitCode(final int value, final int len) {
            this.value = value;
            this.len = len;
        }

        @Override
        public int compareTo(@SuppressWarnings("NullableProblems") final BitCode o) {
            final int result = value - o.value;
            if (result != 0)
                return result;
            return len - o.len;
        }
    }

    public static class HuffmanParamsCalculator {
        private final HashMap<Integer, MutableInt> countMap = new HashMap<Integer, MutableInt>();
        private int[] values = new int[]{};
        private int[] bitLens = new int[]{};

        public void add(final int value) {
            MutableInt counter = countMap.get(value);
            if (counter == null) {
                counter = new MutableInt();
                countMap.put(value, counter);
            }
            counter.value++;
        }

        public void add(final Integer value, final int inc) {
            MutableInt counter = countMap.get(value);
            if (counter == null) {
                counter = new MutableInt();
                countMap.put(value, counter);
            }
            counter.value += inc;
        }

        public int[] bitLens() {
            return bitLens;
        }

        public int[] values() {
            return values;
        }

        public Integer[] valuesAsAutoIntegers() {
            final Integer[] intValues = new Integer[values.length];
            for (int i = 0; i < intValues.length; i++)
                intValues[i] = values[i];

            return intValues;
        }

        public byte[] valuesAsBytes() {
            final byte[] byteValues = new byte[values.length];
            for (int i = 0; i < byteValues.length; i++)
                byteValues[i] = (byte) (0xFF & values[i]);

            return byteValues;
        }

        public Byte[] valuesAsAutoBytes() {
            final Byte[] byteValues = new Byte[values.length];
            for (int i = 0; i < byteValues.length; i++)
                byteValues[i] = (byte) (0xFF & values[i]);

            return byteValues;
        }

        public void calculate() {
            final HuffmanTree<Integer> tree ;
            {
                final int size = countMap.size();
                final int[] frequencies = new int[size];
                final int[] values = new int[size];

                int i = 0;
                for (final Integer v : countMap.keySet()) {
                    values[i] = v;
                    frequencies[i] = countMap.get(v).value;
                    i++;
                }
                tree = HuffmanCode.buildTree(frequencies, autobox(values));
            }

            final List<Integer> valueList = new ArrayList<Integer>();
            final List<Integer> lens = new ArrayList<Integer>();
            HuffmanCode.getValuesAndBitLengths(valueList, lens, tree);

            // the following sorting is not really required, but whatever:
            final BitCode[] codes = new BitCode[valueList.size()];
            for (int i = 0; i < valueList.size(); i++) {
                codes[i] = new BitCode(valueList.get(i), lens.get(i));
            }
            Arrays.sort(codes);

            values = new int[codes.length];
            bitLens = new int[codes.length];

            for (int i = 0; i < codes.length; i++) {
                final BitCode code = codes[i];
                bitLens[i] = code.len;
                values[i] = code.value;
            }
        }
    }

    private static Integer[] autobox(final int[] array) {
        final Integer[] newArray = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
            newArray[i] = array[i];
        return newArray;
    }

    public static class EncodingLengthCalculator {
        private final BitCodec<Integer> codec;
        private final Encoding<Integer> encoding;
        private long len;

        public EncodingLengthCalculator(final Encoding<Integer> encoding) {
            this.encoding = encoding;
            codec = encoding.buildCodec(null, null);
        }

        public void add(final int value) {
            len += codec.numberOfBits(value);
        }

        public void add(final int value, final int inc) {
            len += inc * codec.numberOfBits(value);
        }

        public long len() {
            return len;
        }
    }

    public static class IntegerEncodingCalculator {
        public final List<EncodingLengthCalculator> calculators = new ArrayList<EncodingLengthCalculator>();
        private int max = 0;
        private int count = 0;
        private final String name;
        private HashMap<Integer, MutableInt> dictionary = new HashMap<Integer, MutableInt>();
        private final int dictionaryThreshold = 100;
        private final int minValue;

        public IntegerEncodingCalculator(final String name, final int dictionaryThreshold, final int minValue) {
            this.name = name;
            this.minValue = minValue ;
            // for (int i = 2; i < 10; i++)
            // calculators.add(new EncodingLengthCalculator(
            // new GolombIntegerEncoding(i)));
            //
            // for (int i = 2; i < 20; i++)
            // calculators.add(new EncodingLengthCalculator(
            // new GolombRiceIntegerEncoding(i)));

            calculators.add(new EncodingLengthCalculator(new GammaIntegerEncoding(1 - minValue)));

            for (int i = 2; i < 5; i++)
                calculators.add(new EncodingLengthCalculator(new SubexponentialIntegerEncoding(0 - minValue, i)));

            if (dictionaryThreshold < 1)
                dictionary = null;
            else {
                dictionary = new HashMap<Integer, MutableInt>();
                // int pow = (int) Math.ceil(Math.log(dictionaryThreshold)
                // / Math.log(2f));
                // dictionaryThreshold = 1 << pow ;
                // dictionary = new HashMap<Integer,
                // MutableInt>(dictionaryThreshold, 1);
            }
        }

        public IntegerEncodingCalculator(final String name, final int minValue) {
            this(name, 255, minValue);
        }

        public void addValue(final int value) {
            count++;
            if (value > max)
                max = value;

            for (final EncodingLengthCalculator c : calculators)
                c.add(value);

            if (dictionary != null) {
                if (dictionary.size() >= dictionaryThreshold - 1)
                    dictionary = null;
                else {
                    MutableInt m = dictionary.get(value);
                    if (m == null) {
                        m = new MutableInt();
                        dictionary.put(value, m);
                    }
                    m.value++;
                }

            }

        }

        public Encoding<Integer> getBestEncoding() {
            if (dictionary != null && dictionary.size() == 1) {
                final int value = dictionary.keySet().iterator().next();
                final EncodingParams param = HuffmanIntegerEncoding.toParam(new int[]{value}, new int[]{0});
                final HuffmanIntegerEncoding he = new HuffmanIntegerEncoding();
                he.fromByteArray(param.params);
                return he;
            }

            EncodingLengthCalculator bestC = calculators.get(0);

            for (final EncodingLengthCalculator c : calculators) {
                if (c.len() < bestC.len())
                    bestC = c;
            }

            Encoding<Integer> bestEncoding = bestC.encoding;
            long bits = bestC.len();

            { // check if beta is better:

                final int betaLength = (int) Math.round(Math.log(max - minValue)/ Math.log(2) + 0.5);
                if (bits > betaLength * count) {
                    bestEncoding = new BetaIntegerEncoding(-minValue, betaLength);
                    bits = betaLength * count;
                }
            }

            { // try huffman:
                if (dictionary != null) {
                    final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
                    for (final Integer value : dictionary.keySet())
                        c.add(value, dictionary.get(value).value);

                    c.calculate();

                    final EncodingParams param = HuffmanIntegerEncoding.toParam(c.values(), c.bitLens());
                    final HuffmanIntegerEncoding he = new HuffmanIntegerEncoding();
                    he.fromByteArray(param.params);
                    final EncodingLengthCalculator lc = new EncodingLengthCalculator(he);
                    for (final Integer value : dictionary.keySet())
                        lc.add(value, dictionary.get(value).value);

                    if (lc.len() < bits) {
                        bestEncoding = he;
                        bits = lc.len();
                    }
                }
            }

            byte[] params = bestEncoding.toByteArray();
            params = Arrays.copyOf(params, Math.min(params.length, 20));
            log.debug("Best encoding for " + name + ": " + bestEncoding.id().name() + Arrays.toString(params) + ", bits="+bits);

            return bestEncoding;
        }
    }
}
