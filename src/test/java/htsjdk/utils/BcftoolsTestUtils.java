package htsjdk.utils;

import htsjdk.samtools.util.ProcessExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code bcftools} so that tests can check htsjdk's VCF and BCF output against the reference implementation, and
 * read what it writes, in both directions.
 */
public class BcftoolsTestUtils {
    private static final String BCFTOOLS_BINARY_ENV_VARIABLE = "HTSJDK_BCFTOOLS_BIN";

    /**
     * @return the local bcftools executable: the HTSJDK_BCFTOOLS_BIN environment variable if set, else whatever is on
     *     the PATH, else {@code /usr/local/bin/bcftools}
     */
    public static String getBcftoolsBin() {
        final String envPath = System.getenv(BCFTOOLS_BINARY_ENV_VARIABLE);
        if (envPath != null) {
            return envPath;
        }
        try {
            final Process which = new ProcessBuilder("which", "bcftools")
                    .redirectErrorStream(true)
                    .start();
            if (which.waitFor() == 0) {
                final String path = new String(which.getInputStream().readAllBytes()).trim();
                if (!path.isEmpty() && Files.exists(Paths.get(path))) {
                    return path;
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            // fall through to the default
        } catch (final IOException e) {
            // fall through to the default
        }
        return "/usr/local/bin/bcftools";
    }

    /**
     * @return true if a local bcftools executable is available
     */
    public static boolean isBcftoolsAvailable() {
        final Path bcftools = Paths.get(getBcftoolsBin());
        return Files.isRegularFile(bcftools) && Files.isExecutable(bcftools);
    }

    /**
     * Runs bcftools with the given arguments.
     *
     * @return what bcftools wrote, to stdout and stderr, one element per line
     * @throws RuntimeException if bcftools exits with a failure status
     */
    public static List<String> executeBcftools(final String... arguments) {
        final List<String> command = new ArrayList<>();
        command.add(getBcftoolsBin());
        command.addAll(List.of(arguments));
        final ProcessExecutor.ExitStatusAndOutput result =
                ProcessExecutor.executeAndReturnInterleavedOutput(command.toArray(new String[0]));
        if (result.exitStatus != 0) {
            throw new RuntimeException(
                    String.format("bcftools failed with status %d: %s%n%s", result.exitStatus, command, result.stdout));
        }
        return result.stdout.isEmpty() ? List.of() : List.of(result.stdout.split("\n"));
    }

    /**
     * Runs bcftools with the given arguments and returns only what it wrote to stdout, so that the warnings bcftools
     * writes to stderr (undefined contigs, undeclared INFO keys, ...) do not end up among the data.
     *
     * @return the lines bcftools wrote to stdout
     * @throws RuntimeException if bcftools exits with a failure status; the message includes its stderr
     */
    public static List<String> executeBcftoolsForStdout(final String... arguments) {
        final List<String> command = new ArrayList<>();
        command.add(getBcftoolsBin());
        command.addAll(List.of(arguments));
        try {
            final Path stderrFile = Files.createTempFile("bcftools", ".stderr");
            try {
                final Process process = new ProcessBuilder(command)
                        .redirectError(stderrFile.toFile())
                        .start();
                final String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                final int exitStatus = process.waitFor();
                if (exitStatus != 0) {
                    throw new RuntimeException(String.format(
                            "bcftools failed with status %d: %s%n%s",
                            exitStatus, command, Files.readString(stderrFile)));
                }
                return stdout.isEmpty() ? List.of() : List.of(stdout.split("\n"));
            } finally {
                Files.deleteIfExists(stderrFile);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running " + command, e);
        } catch (final IOException e) {
            throw new RuntimeException("Could not run " + command, e);
        }
    }

    /**
     * Has bcftools render a VCF or BCF as uncompressed VCF text, header included but without the bcftools_viewVersion
     * and bcftools_viewCommand header lines it would otherwise add.
     *
     * @return the lines of the VCF
     */
    public static List<String> viewAsVcf(final Path vcfOrBcf) {
        return executeBcftoolsForStdout("view", "--no-version", "-Ov", vcfOrBcf.toString());
    }
}
