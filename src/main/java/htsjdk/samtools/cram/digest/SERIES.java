package htsjdk.samtools.cram.digest;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.structure.CRAMCompressionRecord;

enum SERIES {
    BASES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getReadBases();
        }

        @Override
        byte[] getBytes(final CRAMCompressionRecord record) {
            return record.getReadBases();
        }
    },
    SCORES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getBaseQualities();
        }

        @Override
        byte[] getBytes(final CRAMCompressionRecord record) {
            return record.getQualityScores();
        }
    };

    abstract byte[] getBytes(SAMRecord record);

    abstract byte[] getBytes(CRAMCompressionRecord record);

}