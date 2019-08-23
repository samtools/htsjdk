package htsjdk.samtools.cram.digest;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.structure.CRAMRecord;

enum SERIES {
    BASES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getReadBases();
        }

        @Override
        byte[] getBytes(final CRAMRecord record) {
            return record.getReadBases();
        }
    },
    SCORES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getBaseQualities();
        }

        @Override
        byte[] getBytes(final CRAMRecord record) {
            return record.getQualityScores();
        }
    };

    abstract byte[] getBytes(SAMRecord record);

    abstract byte[] getBytes(CRAMRecord record);

}