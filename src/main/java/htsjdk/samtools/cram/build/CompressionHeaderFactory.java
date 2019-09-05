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

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.compression.RANSExternalCompressor;
import htsjdk.samtools.cram.encoding.*;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A class responsible for decisions about which encodings to use for a given set of records.
 * This particular version relies heavily on GZIP and RANS for better compression.
 */
public class CompressionHeaderFactory {
    public static final int BYTE_SPACE_SIZE = 256;
    public static final int ALL_BYTES_USED = -1;

    // a parameter for Huffman encoding, so we don't have to re-construct on each call
    private static final int[] SINGLE_ZERO = new int[] { 0 };

    private final CRAMEncodingStrategy encodingStrategy;
    private final CompressionHeaderEncodingMap encodingMap;

    private final Map<Integer, EncodingDetails> bestEncodings = new HashMap<>();
    //TODO: fix this allocation
    private final ByteArrayOutputStream baosForTagValues = new ByteArrayOutputStream(1024 * 1024);
    /**
     * Create a CompressionHeaderFactory using default CRAMEncodingStrategy.
     */
    //TODO: this is used only by tests
    public CompressionHeaderFactory() {
        this(new CRAMEncodingStrategy());
    }

    /**
     * Create a CompressionHeaderFactory using the provided CRAMEncodingStrategy.
     * @param encodingStrategy
     */
    public CompressionHeaderFactory(final CRAMEncodingStrategy encodingStrategy) {
        // cache the encodingMap if its serialized so it only gets created once per
        // container factory rather than once per container
        final String customCompressionMapPath = encodingStrategy.getCustomCompressionMapPath();
        if (customCompressionMapPath.isEmpty()) {
            this.encodingMap = new CompressionHeaderEncodingMap(encodingStrategy);
        } else {
            try {
                this.encodingMap = CompressionHeaderEncodingMap.readFromPath(IOUtil.getPath(customCompressionMapPath));
            } catch (final IOException e) {
                throw new RuntimeIOException("Failure reading custom encoding map from Path", e);
            }
        }
        this.encodingStrategy = encodingStrategy;
    }

    /**
     * Decides on compression methods to use for the given records.
     *
     * @param records
     *            the data to be compressed
     * @param coordinateSorted
     *            if true the records are assumed to be sorted by alignment
     *            position
     * @return {@link htsjdk.samtools.cram.structure.CompressionHeader} object
     *         describing the encoding chosen for the data
     */
    public CompressionHeader build(final List<CRAMRecord> records, final boolean coordinateSorted) {
        final CompressionHeader compressionHeader = new CompressionHeader(encodingMap);

        //TODO: this should be set by the caller since it should only be set once on the header so
        //TODO: that all containers created by the factory use the same settings
        compressionHeader.setIsCoordinateSorted(coordinateSorted);
        compressionHeader.setTagIdDictionary(buildTagIdDictionary(records));

        buildTagEncodings(records, compressionHeader);

        // TODO: these next three lines should move into CompressionHeader
        final SubstitutionMatrix substitutionMatrix = new SubstitutionMatrix(records);
        updateSubstitutionCodes(records, substitutionMatrix);
        compressionHeader.setSubstitutionMatrix(substitutionMatrix);

        return compressionHeader;
    }

    /**
     * Iterate over the records and for each tag found come up with an encoding.
     * Tag encodings are registered via the builder.
     *
     * @param records
     *            CRAM records holding the tags to be encoded
     * @param compressionHeader
     *            compression header to register encodings
     */
    private void buildTagEncodings(final List<CRAMRecord> records, final CompressionHeader compressionHeader) {
        final Set<Integer> tagIdSet = new HashSet<>();

        for (final CRAMRecord record : records) {
            if (record.getTags() == null || record.getTags().size() == 0) {
                continue;
            }

            for (final ReadTag tag : record.getTags()) {
                tagIdSet.add(tag.keyType3BytesAsInt);
            }
        }

        for (final int tagId : tagIdSet) {
            if (bestEncodings.containsKey(tagId)) {
                compressionHeader.addTagEncoding(tagId, bestEncodings.get(tagId).compressor, bestEncodings.get(tagId).params);
            } else {
                final EncodingDetails e = buildEncodingForTag(records, tagId);
                compressionHeader.addTagEncoding(tagId, e.compressor, e.params);
                bestEncodings.put(tagId, e);
            }
        }
    }

    /**
     * Given the records update the substitution matrix with actual substitution
     * codes.
     *
     * @param records
     *            CRAM records
     * @param substitutionMatrix
     *            the matrix to be updated
     */
    static void updateSubstitutionCodes(final List<CRAMRecord> records, final SubstitutionMatrix substitutionMatrix) {
        for (final CRAMRecord record : records) {
            if (record.getReadFeatures() != null) {
                for (final ReadFeature recordFeature : record.getReadFeatures()) {
                    if (recordFeature.getOperator() == Substitution.operator) {
                        final Substitution substitution = ((Substitution) recordFeature);
                        if (substitution.getCode() == Substitution.NO_CODE) {
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
     * Build a dictionary of tag ids.
     *
     * @param records
     *            records holding the tags
     * @return a 3D byte array: a set of unique lists of tag ids.
     */
    private static byte[][][] buildTagIdDictionary(final List<CRAMRecord> records) {
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

        //TODO: WTF?
        final Map<byte[], MutableInt> map = new TreeMap<>(baComparator);
        final MutableInt noTagCounter = new MutableInt();
        map.put(new byte[0], noTagCounter);
        for (final CRAMRecord record : records) {
            if (record.getTags() == null) {
                noTagCounter.value++;
                record.setTagIdsIndex(noTagCounter);
                continue;
            }

            record.getTags().sort(comparator);
            final byte[] tagIds = new byte[record.getTags().size() * 3];

            int tagIndex = 0;
            for (int i = 0; i < record.getTags().size(); i++) {
                tagIds[i * 3] = (byte) record.getTags().get(tagIndex).keyType3Bytes.charAt(0);
                tagIds[i * 3 + 1] = (byte) record.getTags().get(tagIndex).keyType3Bytes.charAt(1);
                tagIds[i * 3 + 2] = (byte) record.getTags().get(tagIndex).keyType3Bytes.charAt(2);
                tagIndex++;
            }

            MutableInt count = map.get(tagIds);
            if (count == null) {
                count = new MutableInt();
                map.put(tagIds, count);
            }
            count.value++;
            record.setTagIdsIndex(count);
        }

        final byte[][][] dictionary = new byte[map.size()][][];
        int i = 0;
        for (final byte[] idsAsBytes : map.keySet()) {
            final int nofIds = idsAsBytes.length / 3;
            dictionary[i] = new byte[nofIds][];
            for (int j = 0; j < idsAsBytes.length;) {
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
     * Tag id is and integer where the first byte is its type and the other 2
     * bytes represent the name. For example 'OQZ', where 'OQ' stands for
     * original quality score tag and 'Z' stands for string type.
     *
     * @param tagID
     *            a 3 byte tag id stored in an int
     * @return tag type, the lowest byte in the tag id
     */
    static byte getTagType(final int tagID) {
        return (byte) (tagID & 0xFF);
    }

    public ExternalCompressor getBestExternalCompressor(final byte[] data) {
        return encodingMap.getBestExternalCompressor(data, encodingStrategy);
    }

    byte[] getDataForTag(final List<CRAMRecord> records, final int tagID) {
        baosForTagValues.reset();

        for (final CRAMRecord record : records) {
            if (record.getTags() == null) {
                continue;
            }

            for (final ReadTag tag : record.getTags()) {
                if (tag.keyType3BytesAsInt != tagID) {
                    continue;
                }
                final byte[] valueBytes = tag.getValueAsByteArray();
                try {
                    baosForTagValues.write(valueBytes);
                } catch (final IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
        }

        return baosForTagValues.toByteArray();
    }

    static ByteSizeRange getByteSizeRangeOfTagValues(final List<CRAMRecord> records, final int tagID) {
        final byte type = getTagType(tagID);
        final ByteSizeRange stats = new ByteSizeRange();
        for (final CRAMRecord record : records) {
            if (record.getTags() == null) {
                continue;
            }

            for (final ReadTag tag : record.getTags()) {
                if (tag.keyType3BytesAsInt != tagID) {
                    continue;
                }
                final int size = getTagValueByteSize(type, tag.getValue());
                if (stats.min > size)
                    stats.min = size;
                if (stats.max < size)
                    stats.max = size;
            }
        }
        return stats;
    }

    /**
     * Find a byte value never mentioned in the array
     * @param array bytes
     * @return byte value or -1 if the array contains all possible byte values.
     */
    static int getUnusedByte(final byte[] array) {
        final byte[] usage = new byte[BYTE_SPACE_SIZE];
        for (final byte b : array) {
            usage[0xFF & b] = 1;
        }

        for (int i = 0; i < usage.length; i++) {
            if (usage[i] == 0)
                return i;
        }
        return ALL_BYTES_USED;
    }

    static class ByteSizeRange {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
    }

    /**
     * A combination of external compressor and encoding params. This is all
     * that is needed to encode a data series.
     */
    //TODO: can this go away ?
    private static class EncodingDetails {
        ExternalCompressor compressor;
        EncodingDescriptor params; // EncodingID + byte array of params
    }

    /**
     * Used by buildEncodingForTag to create a ByteArrayLenEncoding with CanonicalHuffmanIntegerEncoding and
     * ExternalByteArrayEncoding sub-encodings.
     *
     * @param tagValueSize the size in bytes of the values for the tag being encoded
     * @param tagID the ID of the tag being encoded
     * @return EncodingDescriptor descriptor for the resulting encoding
     */
    private EncodingDescriptor buildTagEncodingForSize(final int tagValueSize, final int tagID) {
        // NOTE: This usage of ByteArrayLenEncoding splits the stream between core (for the tag value length)
        // and external (for the tag value bytes). Since the tag value size is determined by the tag type, it
        // is constant and can be represented using a canonical Huffman encoding with a single symbol alphabet,
        // which in turn can use a huffman code of length 0 bits.
        return new ByteArrayLenEncoding(
                // for the huffman encoding, our alphabet has just one symbol (the tag value size, which is constant),
                // so we can just declare that the canonical codeword size will be 0
                new CanonicalHuffmanIntegerEncoding(new int[] { tagValueSize }, SINGLE_ZERO),
                new ExternalByteArrayEncoding(tagID)).toEncodingDescriptor();
    }

    /**
     * Build an encoding for a specific tag for given records.
     *
     * @param records CRAM records holding the tags
     * @param tagID an integer id of the tag
     * @return an encoding for the tag
     */
    private EncodingDetails buildEncodingForTag(final List<CRAMRecord> records, final int tagID) {
        final EncodingDetails details = new EncodingDetails();
        final byte[] data = getDataForTag(records, tagID);

        details.compressor = getBestExternalCompressor(data);

        final byte type = getTagType(tagID);
        switch (type) {
            case 'A':
            case 'c':
            case 'C':
                details.params = buildTagEncodingForSize(1, tagID);
                return details;

            case 'I':
            case 'i':
            case 'f':
                details.params = buildTagEncodingForSize(4, tagID);
                return details;

            case 's':
            case 'S':
                details.params = buildTagEncodingForSize(2, tagID);
                return details;

            case 'Z':
            case 'B':
                final ByteSizeRange stats = getByteSizeRangeOfTagValues(records, tagID);
                final boolean singleSize = stats.min == stats.max;
                if (singleSize) {
                    details.params = buildTagEncodingForSize(stats.min, tagID);
                    return details;
                }

                if (type == 'Z') {
                    details.params = new ByteArrayStopEncoding((byte) '\t', tagID).toEncodingDescriptor();
                    return details;
                }

                final int minSize_threshold_ForByteArrayStopEncoding = 100;
                if (stats.min > minSize_threshold_ForByteArrayStopEncoding) {
                    final int unusedByte = getUnusedByte(data);
                    if (unusedByte > ALL_BYTES_USED) {
                        details.params = new ByteArrayStopEncoding((byte) unusedByte, tagID).toEncodingDescriptor();
                        return details;
                    }
                }

                // NOTE: This usage of ByteArrayLenEncoding does NOT split the stream between core
                // and external, since both the length and byte encoding instantiated here are
                // external. But it does create two different external encodings with the same externalID (???)
                details.params = new ByteArrayLenEncoding(
                        new ExternalIntegerEncoding(tagID),
                        new ExternalByteArrayEncoding(tagID)).toEncodingDescriptor();
                return details;
            default:
                throw new IllegalArgumentException("Unknown tag type: " + (char) type);
        }
    }

    /**
     * Calculate byte size of a tag value based on it's type and value class
     * @param type tag type, like 'A' or 'i'
     * @param value object representing the tag value
     * @return number of bytes used for the tag value
     */
    static int getTagValueByteSize(final byte type, final Object value) {
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
            case 'Z':
                return ((String) value).length()+1;
            case 'B':
                if (value instanceof byte[])
                    return 1+ 4+ ((byte[]) value).length;
                if (value instanceof short[])
                    return 1+ 4+ ((short[]) value).length * 2;
                if (value instanceof int[])
                    return 1+ 4+ ((int[]) value).length * 4;
                if (value instanceof float[])
                    return 1+ 4+ ((float[]) value).length * 4;
                if (value instanceof long[])
                    return 1+ 4+ ((long[]) value).length * 4;

                throw new RuntimeException("Unknown tag array class: " + value.getClass());
            default:
                throw new RuntimeException("Unknown tag type: " + (char) type);
        }
    }
}
