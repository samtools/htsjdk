package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;

import static htsjdk.samtools.cram.build.CramIO.*;

public class CramIOTest extends HtsjdkTest {
    private final String testID = "testid";

    @DataProvider(name="cramHeaderAndEOF")
    private Object[] getCRAMHeaderAndEOF() {
        return new Object[] {
                CramVersions.CRAM_v2_1,
                CramVersions.CRAM_v3
        };
    }

    @Test(dataProvider="cramHeaderAndEOF")
    public void testCheckHeaderAndEOF(final Version cramVersion) throws IOException {
        final CramHeader cramHeader = new CramHeader(cramVersion, testID, new SAMFileHeader());
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();
        try (final FileOutputStream fos = new FileOutputStream(file)) {
            CramIO.writeCramHeader(cramHeader, fos);
            CramIO.writeCRAMEOF(cramHeader.getVersion(), fos);
        }
        Assert.assertTrue(checkHeaderAndEOF(file));
    }

    @DataProvider(name="unsupportedCRAMVersions")
    private Object[] getUnsupportedCRAMVersions() {
        return new Object[] {
                new Version(1, 0),
                new Version(2, 0),
                new Version(3, 1),
                new Version(4, 0),
        };
    }

    @Test(dataProvider = "unsupportedCRAMVersions", expectedExceptions = RuntimeException.class)
    public void testRejectUnknownCRAMVersion(final Version badCRAMVersion) throws IOException {
        final CramHeader badCRAMHeader = new CramHeader(badCRAMVersion, testID, new SAMFileHeader());
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIO.writeCramHeader(badCRAMHeader, baos);
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                CramIO.readCramHeader(bais);
            }
        }
    }

    @Test
    public void testReplaceCramHeader() throws IOException {
        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v3, testID, new SAMFileHeader());
        Assert.assertTrue(cramHeader.getSamFileHeader().getSequenceDictionary().isEmpty());

        final File tempCramfile = File.createTempFile("test", ".cram");
        tempCramfile.deleteOnExit();

        try (final FileOutputStream fos = new FileOutputStream(tempCramfile)) {
            CramIO.writeCramHeader(cramHeader, fos);
            CramIO.writeCRAMEOF(cramHeader.getVersion(), fos);
        }

        final long length = tempCramfile.length();
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final SAMSequenceRecord sequenceRecord = new SAMSequenceRecord("1", 123);
        samFileHeader.addSequence(sequenceRecord);

        final String id2 = "testid2";
        final CramHeader cramHeader2 = new CramHeader(CramVersions.CRAM_v3, id2, samFileHeader);
        final boolean replaced = CramIO.replaceCramHeader(tempCramfile, cramHeader2);

        Assert.assertTrue(replaced);
        Assert.assertEquals(tempCramfile.length(), length);
        Assert.assertTrue(checkHeaderAndEOF(tempCramfile));

        final CramHeader cramHeader3 = readCramHeader(new FileInputStream(tempCramfile));
        Assert.assertEquals(cramHeader3.getVersion(), CramVersions.CRAM_v3);
        Assert.assertFalse(cramHeader3.getSamFileHeader().getSequenceDictionary().isEmpty());
        Assert.assertNotNull(cramHeader3.getSamFileHeader().getSequenceDictionary().getSequence(0));
        Assert.assertEquals(
                cramHeader3.getSamFileHeader().getSequence(sequenceRecord.getSequenceName()).getSequenceLength(),
                sequenceRecord.getSequenceLength()
        );
    }

    /**
     * Check if the {@link SeekableStream} is properly terminated with a end-of-file marker.
     *
     * @param version        CRAM version to assume
     * @param seekableStream the stream to read from
     * @return true if the stream ends with a correct EOF marker, false otherwise
     * @throws IOException as per java IO contract
     */
    private static boolean checkEOF(final Version version, final SeekableStream seekableStream) throws IOException {
        if (version.compatibleWith(CramVersions.CRAM_v3)) {
            return streamEndsWith(seekableStream, ZERO_F_EOF_MARKER);
        }
        if (version.compatibleWith(CramVersions.CRAM_v2_1)) {
            return streamEndsWith(seekableStream, ZERO_B_EOF_MARKER);
        }

        return false;
    }

    /**
     * Check if the file: 1) contains proper CRAM header. 2) given the version info from the header check the end of file marker.
     *
     * @param file the CRAM file to check
     * @return true if the file is a valid CRAM file and is properly terminated with respect to the version.
     */
    private static boolean checkHeaderAndEOF(final File file) {
        try (final SeekableStream seekableStream = new SeekableFileStream(file)) {
            final CramHeader cramHeader = readCramHeader(seekableStream);
            return checkEOF(cramHeader.getVersion(), seekableStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private static boolean streamEndsWith(final SeekableStream seekableStream, final byte[] target) throws IOException {
        final byte[] tail = new byte[target.length];
        seekableStream.seek(seekableStream.length() - target.length);
        InputStreamUtils.readFully(seekableStream, tail, 0, tail.length);
        return Arrays.equals(tail, target);
    }

}
