package htsjdk.tribble;

import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.variant.vcf.VCFCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test codec which redirects to another location after reading the input file
 * It's an example of a codec which uses {@link FeatureCodec#getPathToDataFile(String)}
 */
public class VCFRedirectCodec extends VCFCodec {
    public static final String REDIRECTING_CODEC_TEST_FILE_ROOT = "src/test/resources/htsjdk/tribble/AbstractFeatureReaderTest/redirectingCodecTest/";

    @Override
    public boolean canDecode(final String potentialInput) {
        return super.canDecode(this.getPathToDataFile(potentialInput));
    }

    @Override
    public String getPathToDataFile(final String path) {
        try {
            final Path inputPath = IOUtil.getPath(path);
            final Path dataFilePath = IOUtil.getPath(Files.readAllLines(inputPath).get(0));
            return inputPath.getParent().resolve(dataFilePath).toString();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
