package htsjdk.samtools.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.SystemJimfsFileSystemProvider;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class IntervalListWriterTest extends HtsjdkTest {
    private SAMSequenceDictionary dict;

    @BeforeTest
    void setup() {
        this.dict = new SAMSequenceDictionary();
        this.dict.addSequence(new SAMSequenceRecord("chr1", 10000));
        this.dict.addSequence(new SAMSequenceRecord("chr2", 20000));
        this.dict.addSequence(new SAMSequenceRecord("chr3", 30000));
    }

    @Test
    public void testEndToEnd() throws IOException {
        final File tempFile = File.createTempFile("IntervalListWriterTest.", ".interval_list");
        tempFile.deleteOnExit();
        testEndToEnd(tempFile.toPath());
    }

    @Test
    public void testEndToEndOnPath() throws IOException {
        try(FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path tempFile = Files.createTempFile(jimfs.getRootDirectories().iterator().next(), "IntervalListWriterTest.", ".interval_list");
            testEndToEnd(tempFile);
        }
    }

    private void testEndToEnd(Path tempFile) throws IOException {
        final IntervalList expectedList = new IntervalList(this.dict);

        expectedList.add(new Interval("chr1", 50, 150));
        expectedList.add(new Interval("chr1", 150, 250));
        expectedList.add(new Interval("chr2", 50, 150));
        expectedList.add(new Interval("chr3", 50, 150));
        expectedList.add(new Interval("chr1", 50, 150, true, "number-5"));
        expectedList.add(new Interval("chr1", 150, 250, false, "number-6"));

        try (final IntervalListWriter writer = new IntervalListWriter(tempFile, new SAMFileHeader(this.dict))) {
            for (final Interval interval : expectedList.getIntervals()) {
                writer.write(interval);
            }
        }

        final IntervalList actualList = IntervalList.fromPath(tempFile);
        final SAMSequenceDictionary actualDict = actualList.getHeader().getSequenceDictionary();
        final SAMSequenceDictionary expectedDict = expectedList.getHeader().getSequenceDictionary();
        Assert.assertTrue(actualDict.isSameDictionary(expectedDict));
        Assert.assertEquals(actualList.getIntervals(), expectedList.getIntervals());
    }
}
