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
    private static final Log LOG = Log.getInstance(SamtoolsTestUtils.class);
    private static final String SAMTOOLS_BIN_PROPERTY = "HTSJDK_SAMTOOLS_BIN";
    public final static String expectedSamtoolsVersion = "1.9";

    /**
     * @return true if samtools is available
     */
    public static boolean isSamtoolsAvailable() {
        final String binPath = getSamtoolsBin();
        if (binPath == null) {
            LOG.warn(String.format(
                    "No samtools binary found. Set property %s to enable testing with samtools.",
                    SAMTOOLS_BIN_PROPERTY));
            return false;
        }
        final Path binFile = Paths.get(binPath);
        if (!Files.exists(binFile)) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s property is set to non-existent file: %s", SAMTOOLS_BIN_PROPERTY, binFile));
        }
        return true;
    }

    /**
     * @return the path of the location of the local samtools executable (excluding the name of the executeable
     * itself)
     */
    public static String getSamtoolsBin() {
        final String samtoolsPath = System.getenv(SAMTOOLS_BIN_PROPERTY);
        return samtoolsPath == null ? "/usr/local/bin/samtools" : samtoolsPath;
    }

    /**
     * Execute a samtools command line.
     *
     * @param commandLine samtools command line string, excluding the "samtools" prefix. For example:
     *                    {@code "view -h -b my.sam -o my.bam"}
     * @return the {@link ProcessExecutor.ExitStatusAndOutput} resulting from the command execution, if
     * the command succeeds
     * @throws RuntimeException if the command fails
     */
    public static ProcessExecutor.ExitStatusAndOutput executeSamToolsCommand(final String commandLine) {
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

    public static final File convertToCRAM(
            final File inputSAMBAMCRAMFile,
            final File referenceFile,
            final String commandLineOptions) {
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