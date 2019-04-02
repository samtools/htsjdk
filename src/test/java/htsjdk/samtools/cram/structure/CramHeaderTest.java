package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by vadim on 25/08/2015.
 */
public class CramHeaderTest extends HtsjdkTest {
    private static final Random RANDOM = new Random(TestUtil.RANDOM_SEED);
    private static final byte[] EOF_21 = CramVersions.eofForVersion(CramVersions.CRAM_v2_1);
    private static final byte[] EOF_3 = CramVersions.eofForVersion(CramVersions.CRAM_v3);

    @DataProvider(name = "cramHeaderEOFCases")
    private Object[][] cramHeaderEOFCases() {
        return new Object[][]{
                // expected cases

                { CramVersions.CRAM_v2_1, EOF_21, true },
                { CramVersions.CRAM_v3, EOF_3, true },

                // we still match if byte 8 is incorrect
                // see "relaxing the ITF8 hanging bits" in streamEndsWith()

                { CramVersions.CRAM_v2_1, randomByteSubstitution(EOF_21, 8), true },
                { CramVersions.CRAM_v3, randomByteSubstitution(EOF_3, 8), true },

                // but any other mismatch is bad

                { CramVersions.CRAM_v2_1, randomByteSubstitution(EOF_21, 7), false },
                { CramVersions.CRAM_v2_1, new byte[0], false },
                { CramVersions.CRAM_v2_1, EOF_3, false },

                { CramVersions.CRAM_v3, randomByteSubstitution(EOF_3, 6), false },
                { CramVersions.CRAM_v3, new byte[0], false },
                { CramVersions.CRAM_v3, EOF_21, false },

                // invalid version

                { new Version(1, 0, 0), EOF_21, false },
                { new Version(1, 0, 0), EOF_3, false },

        };
    }

    @Test(dataProvider = "cramHeaderEOFCases")
    public void testCheckHeaderAndEOF(final Version cramVersion,
                                      final byte[] eofBytes,
                                      final boolean expectation) throws IOException {
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();

        final String id = "testid";
        final CramHeader cramHeader = new CramHeader(cramVersion, id, new SAMFileHeader());

        try (final FileOutputStream fos = new FileOutputStream(file)) {
            CramIO.writeCramHeader(cramHeader, fos);
            fos.write(eofBytes);
        }

        Assert.assertEquals(CramIO.checkHeaderAndEOF(file), expectation);
    }

    private byte[] randomByteSubstitution(final byte[] source, final int targetByte) {
        if (source.length <= targetByte) {
            throw new RuntimeException("Test code error: source input too short");
        }

        final byte[] retval = Arrays.copyOf(source, source.length);
        retval[targetByte] = (byte) RANDOM.nextInt(Byte.MAX_VALUE);
        return retval;
    }

    @Test
    public void testReplaceCramHeader() throws IOException {
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();

        final String id = "testid";
        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v3, id, new SAMFileHeader());
        Assert.assertTrue(cramHeader.getSamFileHeader().getSequenceDictionary().isEmpty());

        try (final FileOutputStream fos = new FileOutputStream(file)) {
            CramIO.writeCramHeader(cramHeader, fos);
            CramIO.issueEOF(cramHeader.getVersion(), fos);
        }

        final long originalFileLength = file.length();

        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final SAMSequenceRecord sequenceRecord = new SAMSequenceRecord("1", 123);
        samFileHeader.addSequence(sequenceRecord);

        final String id2 = "testid2";
        final CramHeader cramHeader2 = new CramHeader(CramVersions.CRAM_v3, id2, samFileHeader);
        final boolean replaced = CramIO.replaceCramHeader(file, cramHeader2);
        Assert.assertTrue(replaced);
        Assert.assertEquals(file.length(), originalFileLength);
        Assert.assertTrue(CramIO.checkHeaderAndEOF(file));

        final CramHeader cramHeader3 = CramIO.readCramHeader(new FileInputStream(file));
        Assert.assertEquals(cramHeader3.getVersion(), CramVersions.CRAM_v3);
        Assert.assertFalse(cramHeader3.getSamFileHeader().getSequenceDictionary().isEmpty());
        Assert.assertNotNull(cramHeader3.getSamFileHeader().getSequenceDictionary().getSequence(0));
        Assert.assertEquals(cramHeader3.getSamFileHeader().getSequence(sequenceRecord.getSequenceName()).getSequenceLength(), sequenceRecord.getSequenceLength());
    }

    @Test
    public void testReplaceCramHeaderTooBig() throws IOException {
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();

        final String id = "testid";
        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v3, id, new SAMFileHeader());
        Assert.assertTrue(cramHeader.getSamFileHeader().getSequenceDictionary().isEmpty());

        try (final FileOutputStream fos = new FileOutputStream(file)) {
            CramIO.writeCramHeader(cramHeader, fos);
            CramIO.issueEOF(cramHeader.getVersion(), fos);
        }

        // this header will be too big

        final SAMFileHeader samFileHeader = getSamFileHeaderWithSequences(100);

        final String id2 = "testid2";
        final CramHeader cramHeader2 = new CramHeader(CramVersions.CRAM_v3, id2, samFileHeader);
        final boolean replaced = CramIO.replaceCramHeader(file, cramHeader2);
        Assert.assertFalse(replaced);
   }

    private SAMFileHeader getSamFileHeaderWithSequences(final int sequenceCount) {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        for (int i = 0; i < sequenceCount; i++) {
            samFileHeader.addSequence(new SAMSequenceRecord("" + i, i));
        }
        return samFileHeader;
    }

    @DataProvider(name = "cramVersions")
    private Object[][] cramVersions() {
        return new Object[][]{
                {CramVersions.CRAM_v2_1},
                {CramVersions.CRAM_v3}
        };
    }

    // demonstrates the fix for https://github.com/samtools/htsjdk/issues/1307

    @Test(dataProvider = "cramVersions")
    public void testShortCramHeaderId(final Version cramVersion) throws IOException {
        final String shortId = "CRAM ID";
        final byte[] smallBytes = shortId.getBytes();

        final CramHeader header = new CramHeader(cramVersion, shortId, null);
        Assert.assertEquals(header.getId(), smallBytes);

        // show that it pads a short ID to 20 bytes on write

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            header.writeId(baos);
            Assert.assertEquals(baos.toByteArray().length, CramHeader.MAX_ID_LENGTH);
        }
    }

    // stores only the first 20 bytes of given ID

    @Test(dataProvider = "cramVersions")
    public void testLongCramHeaderId(final Version cramVersion) {
        final String fullId = "An inappropriately long ID String for CramHeader";
        final byte[] bytes20 = fullId.substring(0, CramHeader.MAX_ID_LENGTH).getBytes();

        final CramHeader header = new CramHeader(cramVersion, fullId, null);
        Assert.assertEquals(header.getId(), bytes20);
    }
}
