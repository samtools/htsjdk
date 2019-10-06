package htsjdk.utils;

import htsjdk.samtools.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SamtoolsTestUtils {
    private static final Log LOG = Log.getInstance(SamtoolsTestUtils.class);
    private static final String SAMTOOLS_BIN_PROPERTY = "htsjdk.samtools.bin";

    public static boolean isSamtoolsAvailable() {
        String bin = getSamtoolsBin();
        if (bin == null) {
            LOG.warn(String.format(
                    "No samtools binary found. Set property &s to enable testing with samtools.",
                    SAMTOOLS_BIN_PROPERTY));
            return false;
        }
        final Path binFile = Paths.get(bin);
        if (!Files.exists(binFile)) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s property is set to non-existent file: %s", SAMTOOLS_BIN_PROPERTY, binFile));
        }
        return true;
    }

    private static String getSamtoolsBin() {
        final String samtoolsPath = System.getProperty(SAMTOOLS_BIN_PROPERTY);
        return samtoolsPath == null ? "/usr/local/bin" : samtoolsPath;
    }

    public static final File getWriteToTemporaryCRAM(
            final File inputSAMBAMCRAMFile,
            final File referenceFile,
            final String commandLineOptions) {
        try {
            final File tempCRAMFile = File.createTempFile("getWriteToTemporaryCRAM", FileExtensions.CRAM);
            tempCRAMFile.deleteOnExit();
            final ProcessExecutor pe = new ProcessExecutor();
            final String commandString = String.format("%s/samtools view -h -C %s -T %s %s -o %s",
                    getSamtoolsBin(),
                    commandLineOptions == null ? "" : commandLineOptions,
                    referenceFile.getAbsolutePath(),
                    inputSAMBAMCRAMFile.getAbsolutePath(),
                    tempCRAMFile.getAbsolutePath());
            final int ret = pe.execute(commandString);
            if (ret != 0) {
                throw new RuntimeException(String.format("Return code %d from command: %s", ret, commandString));
            }
            return tempCRAMFile;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }

    }
}