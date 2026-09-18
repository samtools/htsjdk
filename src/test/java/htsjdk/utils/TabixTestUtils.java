package htsjdk.utils;

import htsjdk.samtools.util.ProcessExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs htslib's {@code tabix} so that tests can check htsjdk's tabix indexes against the reference implementation,
 * in both directions.
 */
public class TabixTestUtils {
    private static final String TABIX_BINARY_ENV_VARIABLE = "HTSJDK_TABIX_BIN";

    /**
     * @return the local tabix executable: the HTSJDK_TABIX_BIN environment variable if set, else whatever is on the
     *     PATH, else {@code /usr/local/bin/tabix}
     */
    public static String getTabixBin() {
        final String envPath = System.getenv(TABIX_BINARY_ENV_VARIABLE);
        if (envPath != null) {
            return envPath;
        }
        try {
            final Process which = new ProcessBuilder("which", "tabix")
                    .redirectErrorStream(true)
                    .start();
            if (which.waitFor() == 0) {
                final String path = new String(which.getInputStream().readAllBytes()).trim();
                if (!path.isEmpty() && Files.exists(Paths.get(path))) {
                    return path;
                }
            }
        } catch (final IOException | InterruptedException e) {
            // fall through to the default
        }
        return "/usr/local/bin/tabix";
    }

    /**
     * @return true if a local tabix executable is available
     */
    public static boolean isTabixAvailable() {
        final Path tabix = Paths.get(getTabixBin());
        return Files.isRegularFile(tabix) && Files.isExecutable(tabix);
    }

    /**
     * Runs tabix with the given arguments.
     *
     * @return what tabix wrote, to stdout and stderr, one element per line
     * @throws RuntimeException if tabix exits with a failure status
     */
    public static List<String> executeTabix(final String... arguments) {
        final List<String> command = new ArrayList<>();
        command.add(getTabixBin());
        command.addAll(List.of(arguments));
        final ProcessExecutor.ExitStatusAndOutput result =
                ProcessExecutor.executeAndReturnInterleavedOutput(command.toArray(new String[0]));
        if (result.exitStatus != 0) {
            throw new RuntimeException(
                    String.format("tabix failed with status %d: %s%n%s", result.exitStatus, command, result.stdout));
        }
        return result.stdout.isEmpty() ? List.of() : List.of(result.stdout.split("\n"));
    }

    /**
     * Has tabix build a TBI index beside a block-compressed VCF.
     */
    public static void indexVcf(final Path vcf) {
        executeTabix("-f", "-p", "vcf", vcf.toString());
    }
}
