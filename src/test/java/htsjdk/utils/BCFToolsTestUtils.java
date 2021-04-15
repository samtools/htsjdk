package htsjdk.utils;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.ProcessExecutor;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BCFToolsTestUtils {
    private static final String BCFTOOLS_BINARY_ENV_VARIABLE = "HTSJDK_BCFTOOLS_BIN";
    public static final String expectedBCFtoolsVersion = "1.13";

    /**
     * @return true if bcftools is available, otherwise false
     */
    public static boolean isBCFToolsAvailable() {
        final String binPath = getBCFToolsBin();
        final Path binFile = Paths.get(binPath);
        return Files.exists(binFile);
    }

    /**
     * @throws RuntimeException if bcftools executable is not available
     */
    public static void assertBCFToolsAvailable() {
        if (!isBCFToolsAvailable()) {
            throw new RuntimeException(String.format(
                "No bcftools executable can be found." +
                    " The %s environment variable must be set to the name of the local bcftools executable.",
                BCFTOOLS_BINARY_ENV_VARIABLE
            ));
        }
    }

    /**
     * @return the name and location of the local bcftools executable as specified by the environment
     * variable HTSJDK_BCFTOOLS_BIN, or the default value of "/usr/local/bin/bcftools" if the environment
     * variable is not set
     */
    public static String getBCFToolsBin() {
        final String bcftoolsPath = System.getenv(BCFTOOLS_BINARY_ENV_VARIABLE);
        return bcftoolsPath == null ? "/usr/local/bin/bcftools" : bcftoolsPath;
    }

    /**
     * Execute a bcftools command line if a local bcftools executable is available see {@link #isBCFToolsAvailable()}.
     *
     * @param commandLine bcftools command line string, excluding the "bcftools" prefix. For example:
     *                    {@code "view my.vcf > my.bcf"}
     * @return the {@link ProcessExecutor.ExitStatusAndOutput} resulting from the command execution, if
     * the command succeeds
     * @throws RuntimeException if the command fails, or if a local bcftools executable is not available.
     */
    public static ProcessExecutor.ExitStatusAndOutput executeBCFToolsCommand(final String commandLine) {
        assertBCFToolsAvailable();
        final String commandString = String.format("%s %s", getBCFToolsBin(), commandLine);
        final ProcessExecutor.ExitStatusAndOutput processStatus =
            ProcessExecutor.executeAndReturnInterleavedOutput(commandString);
        if (processStatus.exitStatus != 0) {
            // bcftools seems to write some errors to stdout
            throw new RuntimeException(
                String.format(
                    "Failure code %d returned from bcftools command %s\n (stderr: %.500s)\n (stdout: %.500s)\n",
                    processStatus.exitStatus,
                    commandString,
                    processStatus.stderr == null ? "" : processStatus.stderr,
                    processStatus.stdout == null ? "" : processStatus.stdout
                )
            );
        }
        return processStatus;
    }

    /**
     * Convert an input VCF file to a temporary BCF file using the bcftools "view" command. The temp
     * file will be deleted when the process exits. Use {@link #isBCFToolsAvailable()} to determine if it's safe
     * to use this method.
     *
     * @param inputVCF           input file to convert
     * @param commandLineOptions additional command line options (--input-fmt-option or --output-fmt-option)
     * @return a temporary file containing the bcftools-generated results.
     */
    public static File VCFtoBCF(
        final File inputVCF,
        final String commandLineOptions
    ) {
        assertBCFToolsAvailable();
        try {
            final File tempBCFFile = File.createTempFile("bcftoolsTemporaryBCF", FileExtensions.BCF);
            tempBCFFile.deleteOnExit();
            final String commandString = String.format(
                "view %s %s -o %s",
                commandLineOptions == null ? "" : commandLineOptions,
                inputVCF.getAbsolutePath(),
                tempBCFFile.getAbsolutePath()
            );
            executeBCFToolsCommand(commandString);
            return tempBCFFile;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Convert an input BCF file to a temporary VCF file using the bcftools "view" command. The temp
     * file will be deleted when the process exits. Use {@link #isBCFToolsAvailable()} to determine if it's safe
     * to use this method.
     *
     * @param inputBCF           input file to convert
     * @param commandLineOptions additional command line options (--input-fmt-option or --output-fmt-option)
     * @return a temporary file containing the bcftools-generated results.
     */
    public static File BCFToVCF(
        final File inputBCF,
        final String commandLineOptions
    ) {
        assertBCFToolsAvailable();
        try {
            final File tempVCFFile = File.createTempFile("bcftoolsTemporaryVCF" + inputBCF, FileExtensions.VCF);
            final String commandString = String.format(
                "view %s %s -o %s",
                commandLineOptions == null ? "" : commandLineOptions,
                inputBCF.getAbsolutePath(),
                tempVCFFile.getAbsolutePath()
            );
            executeBCFToolsCommand(commandString);
            return tempVCFFile;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
