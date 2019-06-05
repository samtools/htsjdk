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
import htsjdk.samtools.cram.encoding.*;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Factory for creating CRAM compression headers for containers when writing to a CRAM stream. The fixed data
 * series are generally the same for every container in the stream (this is not required by the spec, but reflects
 * the current htsjdk implementation), however, the tag data series and encodings can vary across containers
 * based on which tags are present in the actual records for that container, and the best compressor to use based
 * on the actual data. This class delegates to a {@link CRAMEncodingStrategy} object to determine which encodings
 * to use for the fixed CRAM data series, and dynamically chooses the best encoding for tag data series.
 */
public final class CompressionHeaderFactory {
    public static final int BYTE_SPACE_SIZE = 256;
    public static final int ALL_BYTES_USED = -1;

    // a parameter for Huffman encoding used for tag ByteArrayLenEncodings
    private static final int[] SINGLE_ZERO = new int[] { 0 };

    private final CRAMEncodingStrategy encodingStrategy;
    private final CompressionHeaderEncodingMap encodingMap;

    private final Map<Integer, EncodingDetails> bestTagEncodings = new HashMap<>();
    private final ByteArrayOutputStream baosForTagValues = new ByteArrayOutputStream(1024 * 1024);

    /**
     * Create a CompressionHeaderFactory using the provided CRAMEncodingStrategy.
     * @param encodingStrategy {@link CRAMEncodingStrategy} to use, may not be null
     */
    public CompressionHeaderFactory(final CRAMEncodingStrategy encodingStrategy) {
        ValidationUtils.nonNull(encodingStrategy, "A CRAMEncodingStrategy is required");

        this.encodingMap = encodingStrategy.getCustomCompressionHeaderEncodingMap() == null ?
                new CompressionHeaderEncodingMap(encodingStrategy) :
                encodingStrategy.getCustomCompressionHeaderEncodingMap();
        this.encodingStrategy = encodingStrategy;
    }

    /**
     * Creates a compression header for the provided list of {@link CRAMCompressionRecord} objects. Resets any internal
     * state (i.e. the tag encoding map state) as preparation for starting the next compression header.
     *
     * @param containerCRAMCompressionRecords all CRAMRecords that will be stored in the container
     * @param coordinateSorted
     *            if true the records are assumed to be sorted by alignment
     *            position
     * @return {@link htsjdk.samtools.cram.structure.CompressionHeader} for the container for {@code containerCRAMRecords}
     */
    public CompressionHeader createCompressionHeader(final List<CRAMCompressionRecord> containerCRAMCompressionRecords, final boolean coordinateSorted) {
        final CompressionHeader compressionHeader = new CompressionHeader(
                encodingMap,
                coordinateSorted,
                true,
                true);

        compressionHeader.setTagIdDictionary(buildTagIdDictionary(containerCRAMCompressionRecords));
        buildTagEncodings(containerCRAMCompressionRecords, compressionHeader);
        final SubstitutionMatrix substitutionMatrix = new SubstitutionMatrix(containerCRAMCompressionRecords);
        updateSubstitutionCodes(containerCRAMCompressionRecords, substitutionMatrix);
        compressionHeader.setSubstitutionMatrix(substitutionMatrix);

        //reset the bestTagEncodings map state since there is no guarantee that the tag encodings accumulated
        // for the current container will be appropriate for the tag value distributions in subsequent containers
        bestTagEncodings.clear();
        return compressionHeader;
    }

    public CRAMEncodingStrategy getEncodingStrategy() {
        return encodingStrategy;
    }

    /**
     * Iterate over the records and for each tag found come up with an encoding.
     * Tag encodings are registered via the builder.
     *
     * @param cramCompressionRecords
     *            CRAM records holding the tags to be encoded
     * @param compressionHeader
     *            compression header to register encodings
     */
    private void buildTagEncodings(final List<CRAMCompressionRecord> cramCompressionRecords, final CompressionHeader compressionHeader) {
        final Set<Integer> tagIdSet = new HashSet<>();

        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            if (record.getTags() == null || record.getTags().size() == 0) {
                continue;
            }

            for (final ReadTag tag : record.getTags()) {
                tagIdSet.add(tag.keyType3BytesAsInt);
            }
        }

        for (final int tagId : tagIdSet) {
            if (bestTagEncodings.containsKey(tagId)) {
                compressionHeader.addTagEncoding(tagId, bestTagEncodings.get(tagId).compressor, bestTagEncodings.get(tagId).params);
            } else {
                final EncodingDetails e = buildEncodingForTag(cramCompressionRecords, tagId);
                compressionHeader.addTagEncoding(tagId, e.compressor, e.params);
                bestTagEncodings.put(tagId, e);
            }
        }
    }

    /**
     * Given the records update the substitution matrix with actual substitution
     * codes.
     *
     * @param cramCompressionRecords
     *            CRAM records
     * @param substitutionMatrix
     *            the matrix to be updated
     */
    static void updateSubstitutionCodes(final List<CRAMCompressionRecord> cramCompressionRecords, final SubstitutionMatrix substitutionMatrix) {
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
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
     * @param cramCompressionRecords
     *            records holding the tags
     * @return a 3D byte array: a set of unique lists of tag ids.
     */
    private static byte[][][] buildTagIdDictionary(final List<CRAMCompressionRecord> cramCompressionRecords) {
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

        final Map<byte[], MutableInt> map = new TreeMap<>(baComparator);
        final MutableInt noTagCounter = new MutableInt();
        map.put(new byte[0], noTagCounter);
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
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

    /**
     * Get the best external compressor to use for the given byte array.
     *
     * @param data byte array to compress
     * @return best compressor to use for the data
     */
    public ExternalCompressor getBestExternalCompressor(final byte[] data) {
        return encodingMap.getBestExternalCompressor(data, encodingStrategy);
    }

    byte[] getDataForTag(final List<CRAMCompressionRecord> cramCompressionRecords, final int tagID) {
        baosForTagValues.reset();

        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            if (record.getTags() != null) {
                for (final ReadTag tag : record.getTags()) {
                    if (tag.keyType3BytesAsInt == tagID) {
                        final byte[] valueBytes = tag.getValueAsByteArray();
                        try {
                            baosForTagValues.write(valueBytes);
                        } catch (final IOException e) {
                            throw new RuntimeIOException(e);
                        }
                    }
                }
            }
        }

        return baosForTagValues.toByteArray();
    }

    public static ByteSizeRange getByteSizeRangeOfTagValues(final List<CRAMCompressionRecord> records, final int tagID) {
        final byte type = getTagType(tagID);
        final ByteSizeRange stats = new ByteSizeRange();
        for (final CRAMCompressionRecord record : records) {
            if (record.getTags() != null) {
                for (final ReadTag tag : record.getTags()) {
                    if (tag.keyType3BytesAsInt == tagID) {
                        final int size = getTagValueByteSize(type, tag.getValue());
                        if (stats.min > size) {
                            stats.min = size;
                        }
                        if (stats.max < size) {
                            stats.max = size;
                        }
                    }
                }
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
            if (usage[i] == 0) {
                return i;
            }
        }
        return ALL_BYTES_USED;
    }

    // Visible for testing
    static class ByteSizeRange {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
    }

    /**
     * A combination of external compressor and encoding params. This is all
     * that is needed to encode a data series.
     */
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
    private EncodingDetails buildEncodingForTag(final List<CRAMCompressionRecord> records, final int tagID) {
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
                if (value instanceof byte[]) {
                    return 1 + 4 + ((byte[]) value).length;
                }
                if (value instanceof short[]) {
                    return 1 + 4 + ((short[]) value).length * 2;
                }
                if (value instanceof int[]) {
                    return 1 + 4 + ((int[]) value).length * 4;
                }
                if (value instanceof float[]) {
                    return 1 + 4 + ((float[]) value).length * 4;
                }
                if (value instanceof long[]) {
                    return 1 + 4 + ((long[]) value).length * 4;
                }
                throw new RuntimeException("Unknown tag array class: " + value.getClass());
            default:
                throw new RuntimeException("Unknown tag type: " + (char) type);
        }
    }
}
