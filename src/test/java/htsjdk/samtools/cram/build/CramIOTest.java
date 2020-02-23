package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
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
    public void testCheckHeaderAndEOF(final CRAMVersion cramVersion) throws IOException {
        final CramHeader cramHeader = new CramHeader(cramVersion, testID);
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();
        try (final FileOutputStream fos = new FileOutputStream(file)) {
            CramIO.writeCramHeader(cramHeader, fos);
            CramIO.writeCramEOF(cramHeader.getCRAMVersion(), fos);
        }
        Assert.assertTrue(checkHeaderAndEOF(file));
    }

    @DataProvider(name="unsupportedCRAMVersions")
    private Object[] getUnsupportedCRAMVersions() {
        return new Object[] {
                new CRAMVersion(1, 0),
                new CRAMVersion(2, 0),
                new CRAMVersion(3, 1),
                new CRAMVersion(4, 0),
        };
    }

    @Test(dataProvider = "unsupportedCRAMVersions", expectedExceptions = RuntimeException.class)
    public void testRejectUnknownCRAMVersion(final CRAMVersion badCRAMVersion) throws IOException {
        final CramHeader badCRAMHeader = new CramHeader(badCRAMVersion, testID);
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIO.writeCramHeader(badCRAMHeader, baos);
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                CramIO.readCramHeader(bais);
            }
        }
    }

    /**
     * Check if the {@link SeekableStream} is properly terminated with a end-of-file marker.
     *
     * @param cramVersion        CRAM version to assume
     * @param seekableStream the stream to read from
     * @return true if the stream ends with a correct EOF marker, false otherwise
     * @throws IOException as per java IO contract
     */
    private static boolean checkEOF(final CRAMVersion cramVersion, final SeekableStream seekableStream) throws IOException {
        if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
            return streamEndsWith(seekableStream, ZERO_F_EOF_MARKER);
        }
        if (cramVersion.compatibleWith(CramVersions.CRAM_v2_1)) {
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
            return checkEOF(cramHeader.getCRAMVersion(), seekableStream);
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
