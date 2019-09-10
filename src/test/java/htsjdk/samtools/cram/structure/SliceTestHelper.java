package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.common.CramVersions;

import java.util.Collections;
import java.util.HashMap;

public class SliceTestHelper {

    public static Slice getSingleRecordSlice() {
        final SAMRecord samRecord = new SAMRecord(new SAMFileHeader());
        samRecord.setReadName("testRead");
        return new Slice(
                Collections.singletonList(new CRAMRecord(
                        CramVersions.CRAM_v3,
                        new CRAMEncodingStrategy(),
                        samRecord,
                        new byte[1],
                        1,
                        new HashMap<>())),
                new CompressionHeader(), 0);
    }

}
