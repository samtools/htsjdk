package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class CramRecordWriterReaderTest extends HtsjdkTest {

    @DataProvider(name = "coordSortedTrueFalse")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "coordSortedTrueFalse")
    public void roundTripTest(final boolean coordinateSorted) {
        // we can only use unmapped records in order to ensure that readbases are roundtripped
        // (since we're not doing full substitution matrix/reference compression
        final List<CRAMCompressionRecord> unmappedRecords = getUnmappedRecords();

        final CompressionHeader header = new CompressionHeaderFactory(
                new CRAMEncodingStrategy()).createCompressionHeader(unmappedRecords, coordinateSorted);

        final Slice slice = new Slice(unmappedRecords, header, 0L, 0L);
        final List<CRAMCompressionRecord> roundTripRecords = slice.deserializeCRAMRecords(new CompressorCache(), ValidationStringency.STRICT);

        Assert.assertEquals(roundTripRecords, unmappedRecords);
    }

    public static List<CRAMCompressionRecord> getUnmappedRecords() {
        final List<CRAMCompressionRecord> cramCompressionRecords = new ArrayList<>();

        // note for future refactoring
        // createRecord() calls Sam2CramRecordFactory.createCramRecord()
        // which is the only way to set a record's readFeatures (except via read codec)
        // which would otherwise be null

        cramCompressionRecords.add(new CRAMCompressionRecord(
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

        cramCompressionRecords.add(new CRAMCompressionRecord(
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

        cramCompressionRecords.add(new CRAMCompressionRecord(
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

        cramCompressionRecords.add(new CRAMCompressionRecord(
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

        return cramCompressionRecords;

    }

}