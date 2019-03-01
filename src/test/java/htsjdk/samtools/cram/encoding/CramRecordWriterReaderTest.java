package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CramRecordWriterReaderTest extends CramRecordTestHelper {
    private CramCompressionRecord read(final byte[] dataBytes,
                                       final Map<Integer, ByteArrayInputStream> inputMap,
                                       final CompressionHeader header,
                                       final ReferenceContext refContext,
                                       final int prevAlignmentStart) throws IOException {
        try (final ByteArrayInputStream is = new ByteArrayInputStream(dataBytes);
            final BitInputStream bis = new DefaultBitInputStream(is)) {

            final CramRecordReader reader = new CramRecordReader(bis, inputMap, header, refContext, ValidationStringency.DEFAULT_STRINGENCY);
            final CramCompressionRecord recordToRead = new CramCompressionRecord();
            reader.read(recordToRead, prevAlignmentStart);
            return recordToRead;
        }
    }

    private List<CramCompressionRecord> initRTRecords() {
        // note for future refactoring
        // createRecords() calls Sam2CramRecordFactory.createCramRecord()
        // which is the only way to set a record's readFeatures (except via read codec)
        // which would otherwise be null

        List<CramCompressionRecord> records = createRecords();

        // note for future refactoring
        // Sam2CramRecordFactory.createCramRecord() sets readBases = qualityScores = new byte[0] if missing
        // but CramRecordReader.read() sets readBases = qualityScores = null if missing
        // so we force it here to match the round trips

        for (CramCompressionRecord record : records) {
            record.readBases = record.qualityScores = null;
        }

        return records;
    }

    @DataProvider(name = "coordSortedTrueFalse")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "coordSortedTrueFalse")
    public void roundTripTest(final boolean coordinateSorted) throws IOException {
        final List<CramCompressionRecord> initialRecords = initRTRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        final CompressionHeader header = createHeader(initialRecords, coordinateSorted);

        final Map<Integer, ByteArrayOutputStream> outputMap = createOutputMap(header);
        int initialAlignmentStart = initialRecords.get(0).alignmentStart;
        final byte[] written = write(initialRecords, outputMap, header, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, initialAlignmentStart);

        final Map<Integer, ByteArrayInputStream> inputMap = createInputMap(outputMap);
        final List<CramCompressionRecord> roundTripRecords = new ArrayList<>(initialRecords.size());

        int prevAlignmentStart = initialAlignmentStart;
        for (int i = 0; i < initialRecords.size(); i++) {
            final CramCompressionRecord newRecord = read(written, inputMap, header, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, prevAlignmentStart);
            prevAlignmentStart = newRecord.alignmentStart;
            roundTripRecords.add(newRecord);
        }

        Assert.assertEquals(roundTripRecords, initialRecords);
    }
}