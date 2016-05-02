package htsjdk.samtools.cram.digest;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.structure.CramCompressionRecord;

enum SERIES {
    BASES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getReadBases();
        }

        @Override
        byte[] getBytes(final CramCompressionRecord record) {
            return record.readBases;
        }
    },
    SCORES {
        @Override
        byte[] getBytes(final SAMRecord record) {
            return record.getBaseQualities();
        }

        @Override
        byte[] getBytes(final CramCompressionRecord record) {
            return record.qualityScores;
        }
    };

    abstract byte[] getBytes(SAMRecord record);

    abstract byte[] getBytes(CramCompressionRecord record);

}