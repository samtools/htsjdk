package htsjdk.samtools.cram;

import htsjdk.samtools.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * A builder to help with SAMRecord creation
 */
class SAMRecordBuilder {
    private String readName;
    private byte[] readBases;
    private byte[] baseQualities;
    private int alignmentStart;
    private int mappingQuality;
    private String cigarString;
    private int flags;
    private int mateAlignmentStart;
    private int inferredInsertSize;
    private SAMBinaryTagAndValue attributes;
    private Integer referenceIndex;
    private Integer mateReferenceIndex = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
    private SAMFileHeader header;

    SAMRecordBuilder(SAMFileHeader header) {
        this.header = header;
    }

    SAMRecordBuilder unmap() {
        flags |= SAMFlag.READ_UNMAPPED.intValue();
        return this;
    }

    SAMRecordBuilder name(String readName) {
        this.readName = readName;
        return this;
    }

    SAMRecordBuilder bases(byte[] readBases) {
        this.readBases = readBases;
        return this;
    }

    SAMRecordBuilder bases(String readBases) {
        this.readBases = readBases.getBytes();
        return this;
    }

    SAMRecordBuilder bases(int length) {
        final byte[] bases = new byte[length];

        byte[] BASES = "ACGT".getBytes();
        Random random = new Random();
        for (int i = 0; i < length; ++i) {
            bases[i] = "ACGT".getBytes()[random.nextInt(BASES.length)];
        }

        bases(bases);
        return this;
    }

    SAMRecordBuilder noBases() {
        readBases = SAMRecord.NULL_SEQUENCE;
        return this;
    }

    SAMRecordBuilder phred(byte[] baseQualities) {
        this.baseQualities = baseQualities;
        return this;
    }

    SAMRecordBuilder scores(String baseQualities) {
        if (SAMRecord.NULL_QUALS_STRING.equals(baseQualities)) {
            phred(SAMRecord.NULL_QUALS);
        } else {
            phred(SAMUtils.fastqToPhred(baseQualities));
        }
        return this;
    }

    SAMRecordBuilder scores(int length) {
        baseQualities = new byte[length];
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            baseQualities[i] = (byte) (0xFF & random.nextInt(40));
        }
        return this;
    }

    SAMRecordBuilder scores() {
        if (readBases == null) throw new NullPointerException("bases are not set, can't assume read length");
        scores(readBases.length);
        return this;
    }

    SAMRecordBuilder noScores() {
        baseQualities = SAMRecord.NULL_QUALS;
        return this;
    }

    SAMRecordBuilder start(int alignmentStart) {
        this.alignmentStart = alignmentStart;
        return this;
    }

    SAMRecordBuilder mapq(int mappingQuality) {
        this.mappingQuality = mappingQuality;
        return this;
    }

    SAMRecordBuilder cigar(String cigarString) {
        this.cigarString = cigarString;
        return this;
    }

    SAMRecordBuilder flags(int flags) {
        this.flags = flags;
        return this;
    }

    SAMRecordBuilder mstart(int mateAlignmentStart) {
        this.mateAlignmentStart = mateAlignmentStart;
        return this;
    }

    SAMRecordBuilder tlen(int inferredInsertSize) {
        this.inferredInsertSize = inferredInsertSize;
        return this;
    }

    SAMRecordBuilder att(SAMBinaryTagAndValue attributes) {
        this.attributes = attributes;
        return this;
    }

    SAMRecordBuilder att(String name, Object value) {
        SAMBinaryTagAndValue attr = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag(name), value);
        if (attributes == null) attributes = attr;
        else attributes.insert(attr);
        return this;
    }

    SAMRecordBuilder ref(Integer referenceIndex) {
        this.referenceIndex = referenceIndex;
        return this;
    }

    SAMRecordBuilder mref(Integer mateReferenceIndex) {
        this.mateReferenceIndex = mateReferenceIndex;
        return this;
    }

    SAMRecord create() {
        SAMRecord record = new SAMRecord(header);
        record.setReadName(readName);
        record.setReadBases(readBases);
        record.setBaseQualities(baseQualities);
        record.setAlignmentStart(alignmentStart);
        record.setMappingQuality(mappingQuality);
        record.setCigarString(cigarString);
        record.setFlags(flags);
        record.setMateAlignmentStart(mateAlignmentStart);
        record.setInferredInsertSize(inferredInsertSize);

        SAMBinaryTagAndValue tag = attributes;
        while (tag != null) {
            record.setAttribute(SAMTagUtil.getSingleton().makeStringTag(tag.tag), tag.value);
            tag = tag.getNext();
        }

        record.setReferenceIndex(referenceIndex);
        record.setMateReferenceIndex(mateReferenceIndex);

        return record;
    }

    /**
     * A builder-like structure for a pair of reads
     */
    static class Pair implements Iterable<SAMRecord> {
        LinkedList<SAMRecordBuilder> builders;

        Pair(int size, SAMFileHeader header) {
            this.builders = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                builders.add(new SAMRecordBuilder(header));
            }
        }

        SAMRecordBuilder first() {
            return builders.getFirst();
        }

        SAMRecordBuilder last() {
            return builders.getLast();
        }

        /**
         * Check if all mates are paired
         *
         * @return true if all are mapped, false otherwise
         */
        boolean allMapped() {
            for (SAMRecordBuilder b : builders) {
                if ((b.flags & SAMFlag.READ_UNMAPPED.intValue()) != 0) return false;
            }
            return true;
        }

        /**
         * Set mate* flags/fields and add MC (mate's cigar) tags for both read in the pair.
         * This method does not update insert size.
         *
         * @return this object
         */
        Pair mate() {
            if ((last().flags & SAMFlag.READ_UNMAPPED.intValue()) != 0)
                first().flags |= SAMFlag.MATE_UNMAPPED.intValue();

            first().mateAlignmentStart = last().alignmentStart;
            first().mateReferenceIndex = last().referenceIndex;
            first().att(SAMTag.MC.name(), last().cigarString);
            first().flags |= SAMFlag.FIRST_OF_PAIR.intValue();
            first().flags &= ~SAMFlag.SECOND_OF_PAIR.intValue();

            if ((first().flags & SAMFlag.READ_UNMAPPED.intValue()) != 0)
                last().flags |= SAMFlag.MATE_UNMAPPED.intValue();

            last().mateAlignmentStart = first().alignmentStart;
            last().mateReferenceIndex = first().referenceIndex;
            last().att(SAMTag.MC.name(), first().cigarString);
            last().flags &= ~SAMFlag.FIRST_OF_PAIR.intValue();
            last().flags |= SAMFlag.SECOND_OF_PAIR.intValue();

            return this;
        }

        @Override
        public Iterator<SAMRecord> iterator() {
            SAMRecord first = first().create();
            SAMRecord second = last().create();
            return Arrays.asList(new SAMRecord[]{first, second}).iterator();
        }
    }
}