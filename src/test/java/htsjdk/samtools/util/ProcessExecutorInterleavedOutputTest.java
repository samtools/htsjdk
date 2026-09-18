package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link ProcessExecutor#executeAndReturnInterleavedOutput}. */
public class ProcessExecutorInterleavedOutputTest extends HtsjdkTest {

    @Test(timeOut = 60_000)
    public void testOutputLargerThanAPipeBufferDoesNotHang() {
        // 100k lines of "y" is ~200 KB, well past the 64 KB a pipe holds before the writer blocks.
        final ProcessExecutor.ExitStatusAndOutput result =
                ProcessExecutor.executeAndReturnInterleavedOutput(new String[] {"sh", "-c", "yes | head -n 100000"});
        Assert.assertEquals(result.exitStatus, 0);
        Assert.assertEquals(result.stdout.length(), 200_000);
    }

    @Test
    public void testStderrIsCapturedAlongsideStdout() {
        final ProcessExecutor.ExitStatusAndOutput result = ProcessExecutor.executeAndReturnInterleavedOutput(
                new String[] {"sh", "-c", "echo to-stdout; echo to-stderr 1>&2"});
        Assert.assertEquals(result.exitStatus, 0);
        Assert.assertTrue(result.stdout.contains("to-stdout\n"), result.stdout);
        Assert.assertTrue(result.stdout.contains("to-stderr\n"), result.stdout);
        Assert.assertNull(result.stderr);
    }

    @Test
    public void testExitStatusIsReported() {
        final ProcessExecutor.ExitStatusAndOutput result =
                ProcessExecutor.executeAndReturnInterleavedOutput(new String[] {"sh", "-c", "exit 3"});
        Assert.assertEquals(result.exitStatus, 3);
    }

    @Test
    public void testCommandStringIsSplitOnWhitespace() {
        final ProcessExecutor.ExitStatusAndOutput result =
                ProcessExecutor.executeAndReturnInterleavedOutput("echo  one\ttwo three");
        Assert.assertEquals(result.stdout, "one two three\n");
    }
}
