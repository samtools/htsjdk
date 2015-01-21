package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceTracks;

import java.util.List;

public class CramBuilder {
    ReferenceTracks tracks;
    Sam2CramRecordFactory factory;
    ReferenceSource source;

    public void build(List<SAMRecord> samRecords) {

    }
}
