package htsjdk.samtools;

import java.nio.file.Path;

public class HtsjdkTestUtils {
    public static final String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";
    public static final Path NA12878_8000 = Path.of(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam");
    public static final Path NA12878_20_21 = Path.of(TEST_DATA_DIR + "NA12878.20.21.unmapped.orig.bam");
    static final Path NA12878_500 = Path.of(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.500.bam");
}
