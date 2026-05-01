package htsjdk.utils;

import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.ProcessExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test utilities for running samtools from htsjdk tests.
 */
public class SamtoolsTestUtils {
    private static final String SAMTOOLS_BINARY_ENV_VARIABLE = "HTSJDK_SAMTOOLS_BIN";
    public static final String minimumSamtoolsVersion = "1.23.1";

    private static final Pattern SAMTOOLS_VERSION_PATTERN = Pattern.compile("(?m)^samtools\\s+(\\d+(?:\\.\\d+)*)");

    /**
     * Extracts the version string (e.g. "1.23.1") from the output of `samtools --version`,
     * which begins with a line of the form {@code samtools <version>}.
     *
     * @return the version string, or null if no version line was found.
     */
    static String parseSamtoolsVersion(final String samtoolsVersionOutput) {
        final Matcher m = SAMTOOLS_VERSION_PATTERN.matcher(samtoolsVersionOutput);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Compares two dotted-numeric version strings (e.g. "1.23.1") component-by-component.
     * Missing trailing components are treated as zero, so "1.23" is equal to "1.23.0".
     */
    static int compareVersions(final String a, final String b) {
        final String[] aParts = a.split("\\.");
        final String[] bParts = b.split("\\.");
        final int len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            final int av = i < aParts.length ? Integer.parseInt(aParts[i]) : 0;
            final int bv = i < bParts.length ? Integer.parseInt(bParts[i]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

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
            throw new RuntimeException(String.format(
                    "No samtools executable can be found."
                            + " The %s environment variable must be set to the name of the local samtools executable.",
                    SAMTOOLS_BINARY_ENV_VARIABLE));
        }
    }

    /**
     * @return the name and location of the local samtools executable. Checks, in order:
     * 1. The HTSJDK_SAMTOOLS_BIN environment variable
     * 2. The system PATH (via "which samtools")
     * 3. The default path "/usr/local/bin/samtools"
     */
    public static String getSamtoolsBin() {
        final String envPath = System.getenv(SAMTOOLS_BINARY_ENV_VARIABLE);
        if (envPath != null) {
            return envPath;
        }

        final String pathResult = findSamtoolsOnPath();
        if (pathResult != null) {
            return pathResult;
        }

        return "/usr/local/bin/samtools";
    }

    /**
     * Attempts to find samtools on the system PATH using "which samtools".
     * @return the path to samtools if found, or null if not found or if the lookup fails
     */
    private static String findSamtoolsOnPath() {
        try {
            final Process process = new ProcessBuilder("which", "samtools")
                    .redirectErrorStream(true)
                    .start();
            final int exitCode = process.waitFor();
            if (exitCode == 0) {
                final String path = new String(process.getInputStream().readAllBytes()).trim();
                if (!path.isEmpty() && Files.exists(Paths.get(path))) {
                    return path;
                }
            }
        } catch (final IOException | InterruptedException e) {
            // Fall through to return null
        }
        return null;
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
            throw new RuntimeException(String.format(
                    "Failure code %d returned from samtools command %s\n (stderr: %.500s)\n (stdout: %.500s)\n",
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
     * @param inputSAMBAMCRAMPath input file to convert
     * @param referencePath a valid reference file
     * @param commandLineOptions additional command line options (--input-fmt-option or --output-fmt-option)
     * @return a temporary file containing the samtools-generated results.
     */
    public static final IOPath convertToCRAM(
            final IOPath inputSAMBAMCRAMPath, final IOPath referencePath, final String commandLineOptions) {
        final IOPath tempCRAMPath = IOUtils.createTempPath("samtoolsTemporaryCRAM", FileExtensions.CRAM);
        tempCRAMPath.toPath().toFile().deleteOnExit();
        convertToCRAM(inputSAMBAMCRAMPath, tempCRAMPath, referencePath, commandLineOptions);
        return tempCRAMPath;
    }

    public static final void convertToCRAM(
            final IOPath inputSAMBAMCRAMPath,
            final IOPath outputPath,
            final IOPath referencePath,
            final String commandLineOptions) {
        assertSamtoolsAvailable();
        final String commandString = String.format(
                "view -h -C -T %s %s %s -o %s",
                referencePath.toPath().toAbsolutePath(),
                commandLineOptions == null ? "" : commandLineOptions,
                inputSAMBAMCRAMPath.toPath().toAbsolutePath(),
                outputPath.toPath().toAbsolutePath());
        executeSamToolsCommand(commandString);
    }
}
