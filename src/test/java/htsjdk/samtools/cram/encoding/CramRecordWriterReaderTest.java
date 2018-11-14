package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CramRecordWriterReaderTest extends CramRecordTestHelper {
    private CramCompressionRecord read(final byte[] dataBytes,
                                       final CompressionHeader header,
                                       final int refId,
                                       final Map<Integer, InputStream> inputMap) throws IOException {
        try (final ByteArrayInputStream is = new ByteArrayInputStream(dataBytes);
            final BitInputStream bis = new DefaultBitInputStream(is)) {

            final CramRecordReader reader = new CramRecordReader(bis, inputMap, header, refId, ValidationStringency.DEFAULT_STRINGENCY);
            final CramCompressionRecord recordToRead = new CramCompressionRecord();
            reader.read(recordToRead);
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

    @Test
    public void roundTripTest() throws IOException {
        final List<CramCompressionRecord> initialRecords = initRTRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        final boolean sorted = true;
        final CompressionHeader header = createHeader(initialRecords, sorted);

        final int refId = Slice.MULTI_REFERENCE;
        final Map<Integer, ByteArrayOutputStream> outputMap = createOutputMap(header);
        final byte[] written = write(initialRecords, header, refId, outputMap);

        final Map<Integer, InputStream> inputMap = createInputMap(outputMap);
        final List<CramCompressionRecord> roundTripRecords = new ArrayList<>(initialRecords.size());
        for (int i = 0; i < initialRecords.size(); i++) {
            roundTripRecords.add(read(written, header, refId, inputMap));
        }

        Assert.assertEquals(roundTripRecords, initialRecords);
    }
}