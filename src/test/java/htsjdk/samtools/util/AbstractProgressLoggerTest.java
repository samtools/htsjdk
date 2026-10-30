package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AbstractProgressLoggerTest extends HtsjdkTest {

    @Test
    public void padLeftPadsToTheGivenLength() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 10), "     hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 6), " hello");
    }

    @Test
    public void padLeavesAStringOfExactlyTheGivenLengthUnchanged() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 5), "hello");
    }

    @Test
    public void padLeavesAStringLongerThanTheGivenLengthUnchanged() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 4), "hello");
    }

    @Test
    public void padLeavesTheStringUnchangedForANegativeLength() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", -1), "hello");
    }

    /** A logger that keeps the lines it would log. */
    private static class CapturingProgressLogger extends AbstractProgressLogger {
        final List<String> lines = new ArrayList<>();

        CapturingProgressLogger(final int n) {
            super("records", "Read", n);
        }

        @Override
        protected void log(final String... message) {
            lines.add(String.join("", message));
        }
    }

    /** A record that counts how often its read name is asked for. */
    private static class NameCountingRecord extends SAMRecord {
        int readNameCalls = 0;

        NameCountingRecord(final String readName, final int alignmentStart) {
            super(HEADER);
            super.setReadName(readName);
            setReferenceIndex(0);
            setAlignmentStart(alignmentStart);
        }

        @Override
        public String getReadName() {
            readNameCalls++;
            return super.getReadName();
        }
    }

    private static final SAMFileHeader HEADER = new SAMFileHeader();

    static {
        HEADER.addSequence(new SAMSequenceRecord("chr1", 1_000_000));
    }

    @Test
    public void recordingASamRecordDoesNotReadItsNameForALineThatDoesNotPrintIt() {
        final CapturingProgressLogger logger = new CapturingProgressLogger(10);
        final List<NameCountingRecord> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            records.add(new NameCountingRecord("r" + i, 1 + i));
        }
        records.forEach(logger::record);
        Assert.assertEquals(logger.lines.size(), 10);
        Assert.assertEquals(records.stream().mapToInt(r -> r.readNameCalls).sum(), 0);
    }

    @Test
    public void theLastReadNameIsLoggedOnceManyPositionsHaveGoneBackwards() {
        final CapturingProgressLogger logger = new CapturingProgressLogger(2_000);
        for (int i = 0; i < 2_000; i++) {
            logger.record(new NameCountingRecord("r" + i, 2_000 - i));
        }
        Assert.assertEquals(logger.lines.size(), 1);
        Assert.assertTrue(logger.lines.get(0).endsWith("Last read name: r1999"), logger.lines.get(0));
    }
}
