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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CramCompressionRecord {
    private static final int MULTI_FRAGMENT_FLAG = 0x1;
    private static final int PROPER_PAIR_FLAG = 0x2;
    private static final int SEGMENT_UNMAPPED_FLAG = 0x4;
    private static final int NEGATIVE_STRAND_FLAG = 0x10;
    private static final int FIRST_SEGMENT_FLAG = 0x40;
    private static final int LAST_SEGMENT_FLAG = 0x80;
    private static final int SECONDARY_ALIGNMENT_FLAG = 0x100;
    private static final int VENDOR_FILTERED_FLAG = 0x200;
    private static final int DUPLICATE_FLAG = 0x400;
    private static final int SUPPLEMENTARY_FLAG = 0x800;

    private static final int MATE_NEG_STRAND_FLAG = 0x1;
    private static final int MATE_UNMAPPED_FLAG = 0x2;

    private static final int FORCE_PRESERVE_QS_FLAG = 0x1;
    private static final int DETACHED_FLAG = 0x2;
    private static final int HAS_MATE_DOWNSTREAM_FLAG = 0x4;
    private static final int UNKNOWN_BASES = 0x8;

    // sequential index of the record in a stream:
    public int index = 0;
    public int alignmentStart;
    public int alignmentDelta;
    private int alignmentEnd = -1;
    private int alignmentSpan = -1;

    public int readLength;

    public int recordsToNextFragment = -1;

    public byte[] readBases;
    public byte[] qualityScores;

    public List<ReadFeature> readFeatures;

    public int readGroupID = 0;

    // bit flags:
    public int flags = 0;
    public int mateFlags = 0;
    public int compressionFlags = 0;

    // pointers to the previous and next segments in the template:
    public CramCompressionRecord next, previous;

    public int mateSequenceID = -1;
    public int mateAlignmentStart = 0;

    public int mappingQuality;

    public String sequenceName;
    public int sequenceId;
    public String readName;

    // insert size:
    public int templateSize;

    public ReadTag[] tags;
    public byte[] tagIds;
    public MutableInt tagIdsIndex;

    public int sliceIndex = 0;

    public byte getMateFlags() {
        return (byte) (0xFF & mateFlags);
    }

    public byte getCompressionFlags() {
        return (byte) (0xFF & compressionFlags);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof CramCompressionRecord)) return false;

        final CramCompressionRecord cramRecord = (CramCompressionRecord) obj;

        if (alignmentStart != cramRecord.alignmentStart) return false;
        if (isNegativeStrand() != cramRecord.isNegativeStrand()) return false;
        if (isVendorFiltered() != cramRecord.isVendorFiltered()) return false;
        if (isSegmentUnmapped() != cramRecord.isSegmentUnmapped()) return false;
        if (readLength != cramRecord.readLength) return false;
        if (isLastSegment() != cramRecord.isLastSegment()) return false;
        if (recordsToNextFragment != cramRecord.recordsToNextFragment) return false;
        if (isFirstSegment() != cramRecord.isFirstSegment()) return false;
        if (mappingQuality != cramRecord.mappingQuality) return false;

        if (!deepEquals(readFeatures, cramRecord.readFeatures)) return false;

        if (!Arrays.equals(readBases, cramRecord.readBases)) return false;
        return Arrays.equals(qualityScores, cramRecord.qualityScores) && areEqual(flags, cramRecord.flags) && areEqual(readName, cramRecord.readName);

    }

    private boolean areEqual(final Object o1, final Object o2) {
        return o1 == null && o2 == null || o1 != null && o1.equals(o2);
    }

    private boolean deepEquals(final Collection<?> c1, final Collection<?> c2) {
        return (c1 == null || c1.isEmpty()) && (c2 == null || c2.isEmpty()) || c1 != null && c1.equals(c2);
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("[");
        if (readName != null) stringBuilder.append(readName).append("; ");
        stringBuilder.append("flags=").append(flags)
                .append("; alignmentOffset=").append(alignmentDelta)
                .append("; mateOffset=").append(recordsToNextFragment)
                .append("; mappingQuality=").append(mappingQuality);

        if (readFeatures != null) for (final ReadFeature feature : readFeatures)
            stringBuilder.append("; ").append(feature.toString());

        if (readBases != null) stringBuilder.append("; ").append("bases: ").append(new String(readBases));
        if (qualityScores != null) stringBuilder.append("; ").append("scores: ").append(new String(qualityScores));

        stringBuilder.append(']');
        return stringBuilder.toString();
    }

    public int getAlignmentSpan() {
        if (alignmentSpan < 0) calculateAlignmentBoundaries();
        return alignmentSpan;
    }

    void calculateAlignmentBoundaries() {
        if (isSegmentUnmapped()) {
            alignmentSpan = 0;
            alignmentEnd = SAMRecord.NO_ALIGNMENT_START;
        } else if (readFeatures == null || readFeatures.isEmpty()) {
            alignmentSpan = readLength;
            alignmentEnd = alignmentStart + alignmentSpan - 1;
        } else {
            alignmentSpan = readLength;
            for (final ReadFeature readFeature : readFeatures) {
                switch (readFeature.getOperator()) {
                    case InsertBase.operator:
                        alignmentSpan--;
                        break;
                    case Insertion.operator:
                        alignmentSpan -= ((Insertion) readFeature).getSequence().length;
                        break;
                    case SoftClip.operator:
                        alignmentSpan -= ((SoftClip) readFeature).getSequence().length;
                        break;
                    case Deletion.operator:
                        alignmentSpan += ((Deletion) readFeature).getLength();
                        break;

                    default:
                        break;
                }
            }
            alignmentEnd = alignmentStart + alignmentSpan - 1;
        }
    }

    public int getAlignmentEnd() {
        if (alignmentEnd < 0) calculateAlignmentBoundaries();
        return alignmentEnd;
    }

    public boolean isMultiFragment() {
        return (flags & MULTI_FRAGMENT_FLAG) != 0;
    }

    public void setMultiFragment(final boolean multiFragment) {
        flags = multiFragment ? flags | MULTI_FRAGMENT_FLAG : flags & ~MULTI_FRAGMENT_FLAG;
    }

    public boolean isSegmentUnmapped() {
        return (flags & SEGMENT_UNMAPPED_FLAG) != 0;
    }

    public void setSegmentUnmapped(final boolean segmentUnmapped) {
        flags = segmentUnmapped ? flags | SEGMENT_UNMAPPED_FLAG : flags & ~SEGMENT_UNMAPPED_FLAG;
    }

    public boolean isFirstSegment() {
        return (flags & FIRST_SEGMENT_FLAG) != 0;
    }

    public void setFirstSegment(final boolean firstSegment) {
        flags = firstSegment ? flags | FIRST_SEGMENT_FLAG : flags & ~FIRST_SEGMENT_FLAG;
    }

    public boolean isLastSegment() {
        return (flags & LAST_SEGMENT_FLAG) != 0;
    }

    public void setLastSegment(final boolean lastSegment) {
        flags = lastSegment ? flags | LAST_SEGMENT_FLAG : flags & ~LAST_SEGMENT_FLAG;
    }

    public boolean isSecondaryAlignment() {
        return (flags & SECONDARY_ALIGNMENT_FLAG) != 0;
    }

    public void setSecondaryAlignment(final boolean secondaryAlignment) {
        flags = secondaryAlignment ? flags | SECONDARY_ALIGNMENT_FLAG : flags & ~SECONDARY_ALIGNMENT_FLAG;
    }

    public boolean isVendorFiltered() {
        return (flags & VENDOR_FILTERED_FLAG) != 0;
    }

    public void setVendorFiltered(final boolean vendorFiltered) {
        flags = vendorFiltered ? flags | VENDOR_FILTERED_FLAG : flags & ~VENDOR_FILTERED_FLAG;
    }

    public boolean isProperPair() {
        return (flags & PROPER_PAIR_FLAG) != 0;
    }

    public void setProperPair(final boolean properPair) {
        flags = properPair ? flags | PROPER_PAIR_FLAG : flags & ~PROPER_PAIR_FLAG;
    }

    public boolean isDuplicate() {
        return (flags & DUPLICATE_FLAG) != 0;
    }

    public void setDuplicate(final boolean duplicate) {
        flags = duplicate ? flags | DUPLICATE_FLAG : flags & ~DUPLICATE_FLAG;
    }

    public boolean isNegativeStrand() {
        return (flags & NEGATIVE_STRAND_FLAG) != 0;
    }

    public void setNegativeStrand(final boolean negativeStrand) {
        flags = negativeStrand ? flags | NEGATIVE_STRAND_FLAG : flags & ~NEGATIVE_STRAND_FLAG;
    }

    public boolean isMateUnmapped() {
        return (mateFlags & MATE_UNMAPPED_FLAG) != 0;
    }

    public void setMateUnmapped(final boolean mateUnmapped) {
        mateFlags = mateUnmapped ? mateFlags | MATE_UNMAPPED_FLAG : mateFlags & ~MATE_UNMAPPED_FLAG;
    }

    public boolean isMateNegativeStrand() {
        return (mateFlags & MATE_NEG_STRAND_FLAG) != 0;
    }

    public void setMateNegativeStrand(final boolean mateNegativeStrand) {
        mateFlags = mateNegativeStrand ? mateFlags | MATE_NEG_STRAND_FLAG : mateFlags & ~MATE_NEG_STRAND_FLAG;
    }

    public boolean isHasMateDownStream() {
        return (compressionFlags & HAS_MATE_DOWNSTREAM_FLAG) != 0;
    }

    public void setHasMateDownStream(final boolean hasMateDownStream) {
        compressionFlags = hasMateDownStream ? compressionFlags | HAS_MATE_DOWNSTREAM_FLAG : compressionFlags & ~HAS_MATE_DOWNSTREAM_FLAG;
    }

    public boolean isDetached() {
        return (compressionFlags & DETACHED_FLAG) != 0;
    }

    public void setDetached(final boolean detached) {
        compressionFlags = detached ? compressionFlags | DETACHED_FLAG : compressionFlags & ~DETACHED_FLAG;
    }

    public boolean isForcePreserveQualityScores() {
        return (compressionFlags & FORCE_PRESERVE_QS_FLAG) != 0;
    }

    public void setForcePreserveQualityScores(final boolean forcePreserveQualityScores) {
        compressionFlags = forcePreserveQualityScores ? compressionFlags | FORCE_PRESERVE_QS_FLAG : compressionFlags &
                ~FORCE_PRESERVE_QS_FLAG;
    }

    public boolean isUnknownBases() {
        return (compressionFlags & UNKNOWN_BASES) != 0;
    }

    public void setUnknownBases(final boolean unknownBases) {
        compressionFlags = unknownBases ? compressionFlags | UNKNOWN_BASES : compressionFlags &
                ~UNKNOWN_BASES;
    }

    public boolean isSupplementary() {
        return (flags & SUPPLEMENTARY_FLAG) != 0;
    }

    public void setSupplementary(final boolean supplementary) {
        flags = supplementary ? flags | SUPPLEMENTARY_FLAG : flags & ~SUPPLEMENTARY_FLAG;
    }

    public static class BAM_FLAGS {
        public static final int READ_PAIRED_FLAG = 0x1;
        public static final int PROPER_PAIR_FLAG = 0x2;
        public static final int READ_UNMAPPED_FLAG = 0x4;
        public static final int MATE_UNMAPPED_FLAG = 0x8;
        public static final int READ_STRAND_FLAG = 0x10;
        public static final int MATE_STRAND_FLAG = 0x20;
        public static final int FIRST_OF_PAIR_FLAG = 0x40;
        public static final int SECOND_OF_PAIR_FLAG = 0x80;
        public static final int NOT_PRIMARY_ALIGNMENT_FLAG = 0x100;
        public static final int READ_FAILS_VENDOR_QUALITY_CHECK_FLAG = 0x200;
        public static final int DUPLICATE_READ_FLAG = 0x400;
        public static final int SUPPLEMENTARY_FLAG = 0x800;
    }

    public static int getBAMFlags(final int cramFlags, final byte cramMateFlags) {
        int value = cramFlags;
        if ((cramMateFlags & MATE_NEG_STRAND_FLAG) != 0) value |= BAM_FLAGS.MATE_STRAND_FLAG;
        if ((cramMateFlags & MATE_UNMAPPED_FLAG) != 0) value |= BAM_FLAGS.MATE_UNMAPPED_FLAG;
        return value;
    }
}
