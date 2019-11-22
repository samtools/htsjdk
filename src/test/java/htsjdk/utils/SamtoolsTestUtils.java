package htsjdk.utils;

import htsjdk.samtools.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test utilities for running samtools from htsjdk tests.
 */
public class SamtoolsTestUtils {
    private static final String SAMTOOLS_BINARY_ENV_VARIABLE = "HTSJDK_SAMTOOLS_BIN";
    public final static String expectedSamtoolsVersion = "1.9";

    /**
     * @return true if samtools is available, otherwise false
     */
    public static boolean isSamtoolsAvailable() {
        final String binPath = getSamtoolsBin();
        final Path binFile = Paths.get(binPath);
        return Files.exists(binFile);
    }

    /**
     * @return true if a local samtools executable is available, otherwise throws a runtimeException
     */
    public static void assertSamtoolsAvailable() {
        if (!isSamtoolsAvailable()) {
            throw new RuntimeException(
                    String.format(
                            "No samtools executable can be found." +
                                    " The %s environment variable must be set to the name of the local samtools executable.",
                            SAMTOOLS_BINARY_ENV_VARIABLE));
        }
    }

    /**
     * @return the name and location of the local samtools executable as specified by the environment
     * variable HTSJDK_SAMTOOLS_BIN, or the default value of "/usr/local/bin/samtools" if the environment
     * variable is not set
     */
    public static String getSamtoolsBin() {
        final String samtoolsPath = System.getenv(SAMTOOLS_BINARY_ENV_VARIABLE);
        return samtoolsPath == null ? "/usr/local/bin/samtools" : samtoolsPath;
    }

    /**
     * Execute a samtools command line if a local samtools executable is available see {@link #isSamtoolsAvailable()}.
     *
     * @param commandLine samtools command line string, excluding the "samtools" prefix. For example:
     *                    {@code "view -h -b my.sam -o my.bam"}
     * @return the {@link ProcessExecutor.ExitStatusAndOutput} resulting from the command execution, if
     * the command succeeds
     * @throws RuntimeException if the command fails, or if a local samtools executable is not available.
     */
    public static ProcessExecutor.ExitStatusAndOutput executeSamToolsCommand(final String commandLine) {
        assertSamtoolsAvailable();
        final String commandString = String.format("%s %s", getSamtoolsBin(), commandLine);
        final ProcessExecutor.ExitStatusAndOutput processStatus =
                ProcessExecutor.executeAndReturnInterleavedOutput(commandString);
        if (processStatus.exitStatus != 0) {
            // samtools seems to write some errors to stdout
            throw new RuntimeException(
                    String.format("Failure code %d returned from samtools command %s\n (stderr: %.500s)\n (stdout: %.500s)\n",
                            processStatus.exitStatus,
                            commandString,
                            processStatus.stderr == null ? "" : processStatus.stderr,
                            processStatus.stdout == null ? "" : processStatus.stdout));
        }
        return processStatus;
    }

    /**
     * Convert an input sam/bam/cram file to a temporary CRAM file using the samtools "view" command. The temp
     * file will be deleted when the process exits. Use {@link #isSamtoolsAvailable()} to determine if its safe
     * to use this method.
     *
     * @param inputSAMBAMCRAMFile input file to convert
     * @param referenceFile a valid reference file
     * @param commandLineOptions additional command line options (--input-fmt-option or --output-fmt-option)
     * @return a temporary file containing the samtools-generated results.
     */
    public static final File convertToCRAM(
            final File inputSAMBAMCRAMFile,
            final File referenceFile,
            final String commandLineOptions) {
        assertSamtoolsAvailable();
        try {
            final File tempCRAMFile = File.createTempFile("samtoolsTemporaryCRAM", FileExtensions.CRAM);
            tempCRAMFile.deleteOnExit();
            final String commandString = String.format("view -h -C -T %s %s %s -o %s",
                    referenceFile.getAbsolutePath(),
                    commandLineOptions == null ? "" : commandLineOptions,
                    inputSAMBAMCRAMFile.getAbsolutePath(),
                    tempCRAMFile.getAbsolutePath());
            executeSamToolsCommand(commandString);
            return tempCRAMFile;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}