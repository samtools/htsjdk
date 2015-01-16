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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.InsertBase;
import htsjdk.samtools.cram.encoding.read_features.Insertion;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.SoftClip;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CramCompressionRecord {
    public static final int MULTIFRAGMENT_FLAG = 0x1;
    public static final int PROPER_PAIR_FLAG = 0x2;
    public static final int SEGMENT_UNMAPPED_FLAG = 0x4;
    public static final int NEGATIVE_STRAND_FLAG = 0x10;
    public static final int FIRST_SEGMENT_FLAG = 0x40;
    public static final int LAST_SEGMENT_FLAG = 0x80;
    public static final int SECONDARY_ALIGNMENT_FLAG = 0x100;
    public static final int VENDOR_FILTERED_FLAG = 0x200;
    public static final int DUPLICATE_FLAG = 0x400;

    public static final int MATE_NEG_STRAND_FLAG = 0x1;
    public static final int MATE_UNMAPPED_FLAG = 0x2;

    public static final int FORCE_PRESERVE_QS_FLAG = 0x1;
    public static final int DETACHED_FLAG = 0x2;
    public static final int HAS_MATE_DOWNSTREAM_FLAG = 0x4;

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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CramCompressionRecord))
            return false;

        CramCompressionRecord r = (CramCompressionRecord) obj;

        if (alignmentStart != r.alignmentStart)
            return false;
        if (isNegativeStrand() != r.isNegativeStrand())
            return false;
        if (isVendorFiltered() != r.isVendorFiltered())
            return false;
        if (isSegmentUnmapped() != r.isSegmentUnmapped())
            return false;
        if (readLength != r.readLength)
            return false;
        if (isLastSegment() != r.isLastSegment())
            return false;
        if (recordsToNextFragment != r.recordsToNextFragment)
            return false;
        if (isFirstSegment() != r.isFirstSegment())
            return false;
        if (mappingQuality != r.mappingQuality)
            return false;

        if (!deepEquals(readFeatures, r.readFeatures))
            return false;

        if (!Arrays.equals(readBases, r.readBases))
            return false;
        if (!Arrays.equals(qualityScores, r.qualityScores))
            return false;

        if (!areEqual(flags, r.flags))
            return false;

        if (!areEqual(readName, r.readName))
            return false;

        return true;
    }

    private boolean areEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null)
            return true;
        return o1 != null && o1.equals(o2);
    }

    private boolean deepEquals(Collection<?> c1, Collection<?> c2) {
        if ((c1 == null || c1.isEmpty()) && (c2 == null || c2.isEmpty()))
            return true;
        if (c1 != null)
            return c1.equals(c2);
        return false;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("[");
        if (readName != null)
            sb.append(readName).append("; ");
        sb.append("flags=").append(flags);
        sb.append("; aloffset=").append(alignmentDelta);
        sb.append("; mateoffset=").append(recordsToNextFragment);
        sb.append("; mappingQuality=").append(mappingQuality);

        if (readFeatures != null)
            for (ReadFeature feature : readFeatures)
                sb.append("; ").append(feature.toString());

        if (readBases != null)
            sb.append("; ").append("bases: ").append(new String(readBases));
        if (qualityScores != null)
            sb.append("; ").append("qscores: ")
                    .append(new String(qualityScores));

        sb.append("]");
        return sb.toString();
    }

    public int getAlignmentSpan() {
        if (alignmentSpan < 0)
            calculateAlignmentBoundaries();
        return alignmentSpan;
    }

    public void calculateAlignmentBoundaries() {
        if (readFeatures == null || readFeatures.isEmpty()) {
            alignmentSpan = readLength;
            alignmentEnd = alignmentStart + alignmentSpan - 1;
        } else {
            alignmentSpan = readLength;
            for (ReadFeature f : readFeatures) {
                switch (f.getOperator()) {
                    case InsertBase.operator:
                        alignmentSpan--;
                        break;
                    case Insertion.operator:
                        alignmentSpan -= ((Insertion) f).getSequence().length;
                        break;
                    case SoftClip.operator:
                        alignmentSpan -= ((SoftClip) f).getSequence().length;
                        break;
                    case Deletion.operator:
                        alignmentSpan += ((Deletion) f).getLength();
                        break;

                    default:
                        break;
                }
            }
            alignmentEnd = alignmentStart + alignmentSpan - 1;
        }
    }

    public int getAlignmentEnd() {
        if (alignmentEnd < 0)
            calculateAlignmentBoundaries();
        return alignmentEnd;
    }

    public boolean isMultiFragment() {
        return (flags & MULTIFRAGMENT_FLAG) != 0;
    }

    public void setMultiFragment(boolean multiFragment) {
        flags = multiFragment ? flags | MULTIFRAGMENT_FLAG : flags
                & ~MULTIFRAGMENT_FLAG;
    }

    public boolean isSegmentUnmapped() {
        return (flags & SEGMENT_UNMAPPED_FLAG) != 0;
    }

    public void setSegmentUnmapped(boolean segmentUnmapped) {
        flags = segmentUnmapped ? flags | SEGMENT_UNMAPPED_FLAG : flags
                & ~SEGMENT_UNMAPPED_FLAG;
    }

    public boolean isFirstSegment() {
        return (flags & FIRST_SEGMENT_FLAG) != 0;
    }

    public void setFirstSegment(boolean firstSegment) {
        flags = firstSegment ? flags | FIRST_SEGMENT_FLAG : flags
                & ~FIRST_SEGMENT_FLAG;
    }

    public boolean isLastSegment() {
        return (flags & LAST_SEGMENT_FLAG) != 0;
    }

    public void setLastSegment(boolean lastSegment) {
        flags = lastSegment ? flags | LAST_SEGMENT_FLAG : flags
                & ~LAST_SEGMENT_FLAG;
    }

    public boolean isSecondaryAlignment() {
        return (flags & SECONDARY_ALIGNMENT_FLAG) != 0;
    }

    public void setSecondaryAlignment(boolean secondaryAlignment) {
        flags = secondaryAlignment ? flags | SECONDARY_ALIGNMENT_FLAG : flags
                & ~SECONDARY_ALIGNMENT_FLAG;
    }

    public boolean isVendorFiltered() {
        return (flags & VENDOR_FILTERED_FLAG) != 0;
    }

    public void setVendorFiltered(boolean vendorFiltered) {
        flags = vendorFiltered ? flags | VENDOR_FILTERED_FLAG : flags
                & ~VENDOR_FILTERED_FLAG;
    }

    public boolean isProperPair() {
        return (flags & PROPER_PAIR_FLAG) != 0;
    }

    public void setProperPair(boolean properPair) {
        flags = properPair ? flags | PROPER_PAIR_FLAG : flags
                & ~PROPER_PAIR_FLAG;
    }

    public boolean isDuplicate() {
        return (flags & DUPLICATE_FLAG) != 0;
    }

    public void setDuplicate(boolean duplicate) {
        flags = duplicate ? flags | DUPLICATE_FLAG : flags & ~DUPLICATE_FLAG;
    }

    public boolean isNegativeStrand() {
        return (flags & NEGATIVE_STRAND_FLAG) != 0;
    }

    public void setNegativeStrand(boolean negativeStrand) {
        flags = negativeStrand ? flags | NEGATIVE_STRAND_FLAG : flags
                & ~NEGATIVE_STRAND_FLAG;
    }

    public boolean isMateUmapped() {
        return (mateFlags & MATE_UNMAPPED_FLAG) != 0;
    }

    public void setMateUmapped(boolean mateUmapped) {
        mateFlags = mateUmapped ? mateFlags | MATE_UNMAPPED_FLAG : mateFlags
                & ~MATE_UNMAPPED_FLAG;
    }

    public boolean isMateNegativeStrand() {
        return (mateFlags & MATE_NEG_STRAND_FLAG) != 0;
    }

    public void setMateNegativeStrand(boolean mateNegativeStrand) {
        mateFlags = mateNegativeStrand ? mateFlags | MATE_NEG_STRAND_FLAG
                : mateFlags & ~MATE_NEG_STRAND_FLAG;
    }

    public boolean isHasMateDownStream() {
        return (compressionFlags & HAS_MATE_DOWNSTREAM_FLAG) != 0;
    }

    public void setHasMateDownStream(boolean hasMateDownStream) {
        compressionFlags = hasMateDownStream ? compressionFlags
                | HAS_MATE_DOWNSTREAM_FLAG : compressionFlags
                & ~HAS_MATE_DOWNSTREAM_FLAG;
    }

    public boolean isDetached() {
        return (compressionFlags & DETACHED_FLAG) != 0;
    }

    public void setDetached(boolean detached) {
        compressionFlags = detached ? compressionFlags | DETACHED_FLAG
                : compressionFlags & ~DETACHED_FLAG;
    }

    public boolean isForcePreserveQualityScores() {
        return (compressionFlags & FORCE_PRESERVE_QS_FLAG) != 0;
    }

    public void setForcePreserveQualityScores(boolean forcePreserveQualityScores) {
        compressionFlags = forcePreserveQualityScores ? compressionFlags
                | FORCE_PRESERVE_QS_FLAG : compressionFlags
                & ~FORCE_PRESERVE_QS_FLAG;
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
    }

    public static int getBAMFlags(int cramFlags, byte cramMateFlags) {
        int value = cramFlags;
        if ((cramMateFlags & MATE_NEG_STRAND_FLAG) != 0)
            value |= BAM_FLAGS.MATE_STRAND_FLAG;
        if ((cramMateFlags & MATE_UNMAPPED_FLAG) != 0)
            value |= BAM_FLAGS.MATE_UNMAPPED_FLAG;
        return value;
    }
}
