package htsjdk.utils;

import htsjdk.samtools.util.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BamtoolsTestUtils {
    private static final String BAMTOOLS_BINARY_ENV_VARIABLE = "HTSJDK_BCFTOOLS_BIN";
    public final static String expectedBcftoolsVersion = "1.12";

    /**
     * @return true if bcftools is available, otherwise false
     */
    public static boolean isBamtoolsAvailable() {
        final String binPath = getBcftoolsBin();
        final Path binFile = Paths.get(binPath);
        return Files.exists(binFile);
    }

    /**
     * @return true if a local bcftools executable is available, otherwise throws a runtimeException
     */
    public static void assertBamtoolsAvailable() {
        if (!isBamtoolsAvailable()) {
            throw new RuntimeException(
                String.format(
                    "No bcftools executable can be found." +
                        " The %s environment variable must be set to the name of the local bcftools executable.",
                    BAMTOOLS_BINARY_ENV_VARIABLE));
        }
    }

    /**
     * @return the name and location of the local bcftools executable as specified by the environment
     * variable HTSJDK_BCFTOOLS_BIN, or the default value of "/usr/local/bin/bcftools" if the environment
     * variable is not set
     */
    public static String getBcftoolsBin() {
        final String bcftoolsPath = System.getenv(BAMTOOLS_BINARY_ENV_VARIABLE);
        return bcftoolsPath == null ? "/usr/local/bin/bcftools" : bcftoolsPath;
    }

    /**
     * Execute a bcftools command line if a local bcftools executable is available see {@link #isBamtoolsAvailable()}.
     *
     * @param commandLine bcftools command line string, excluding the "bcftools" prefix. For example:
     *                    {@code "query -f'[%POS %SAMPLE %GT\n]' -i'N_PASS(GT="alt")==1'"}
     * @return the {@link ProcessExecutor.ExitStatusAndOutput} resulting from the command execution, if
     * the command succeeds
     * @throws RuntimeException if the command fails, or if a local bcftools executable is not available.
     */
    public static ProcessExecutor.ExitStatusAndOutput executeBcfToolsCommand(final String commandLine) {
        assertBamtoolsAvailable();
        final String commandString = String.format("%s %s", getBcftoolsBin(), commandLine);
        final ProcessExecutor.ExitStatusAndOutput processStatus =
            ProcessExecutor.executeAndReturnInterleavedOutput(commandString);
        if (processStatus.exitStatus != 0) {
            // bcftools seems to write some errors to stdout
            throw new RuntimeException(
                String.format("Failure code %d returned from bcftools command %s\n (stderr: %.500s)\n (stdout: %.500s)\n",
                    processStatus.exitStatus,
                    commandString,
                    processStatus.stderr == null ? "" : processStatus.stderr,
                    processStatus.stdout == null ? "" : processStatus.stdout));
        }
        return processStatus;
    }
}
