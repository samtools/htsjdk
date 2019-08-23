package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.structure.CRAMRecord;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

//TODO: this really needs to use SAMRecords to check round tripping of read bases via reference/substitution matrix,
// bases, qual scores, etc.

public class CramRecordWriterReaderTest extends HtsjdkTest {

    @DataProvider(name = "coordSortedTrueFalse")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "coordSortedTrueFalse")
    public void roundTripTest(final boolean coordinateSorted) {
        // we can only use unmapped records in order to ensure that readbases are roundtripped
        // (since we're not doing full substitution matrix/reference compression
        final List<CRAMRecord> unmappedRecords = getUnmappedRecords();

        // TODO:
        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        final CompressionHeader header = new CompressionHeaderFactory().build(unmappedRecords, coordinateSorted);

        final Slice slice = Slice.buildSlice(unmappedRecords, header);
        final List<CRAMRecord> roundTripRecords = slice.getRecords(new SAMFileHeader(), ValidationStringency.STRICT);

        Assert.assertEquals(roundTripRecords, unmappedRecords);
    }

    public static List<CRAMRecord> getUnmappedRecords() {
        final List<CRAMRecord> cramRecords = new ArrayList<>();

        // note for future refactoring
        // createRecord() calls Sam2CramRecordFactory.createCramRecord()
        // which is the only way to set a record's readFeatures (except via read codec)
        // which would otherwise be null

        cramRecords.add(new CRAMRecord(
                1,
                2,
                SAMFlag.READ_UNMAPPED.intValue(),
                0,
                "rec1",
                "AAA".length(),
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                0,
                0,
                SAMRecord.NULL_QUALS,
                "AAA".getBytes(),
                null,
                null,
                2,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1));

        cramRecords.add(new CRAMRecord(
                1,
                2,
                SAMFlag.READ_UNMAPPED.intValue(),
                0,
                "rec2",
                "CCCCCC".length(),
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                0,
                0,
                SAMRecord.NULL_QUALS,
                "CCCCCC".getBytes(),
                null,
                null,
                2,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1));

        cramRecords.add(new CRAMRecord(
                1,
                2,
                SAMFlag.READ_UNMAPPED.intValue(),
                0,
                "rec2",
                "GG".length(),
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                0,
                0,
                SAMRecord.NULL_QUALS,
                "GG".getBytes(),
                null,
                null,
                2,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1));

        cramRecords.add(new CRAMRecord(
                1,
                2,
                SAMFlag.READ_UNMAPPED.intValue(),
                0,
                "rec2",
                "TTTTTTTTTT".length(),
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                0,
                0,
                SAMRecord.NULL_QUALS,
                "TTTTTTTTTT".getBytes(),
                null,
                null,
                2,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1));

        return cramRecords;

    }

}