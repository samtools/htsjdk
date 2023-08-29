package htsjdk.samtools;

import htsjdk.io.HtsPath;

import java.io.File;

public class HtsjdkTestUtils {
    public static final String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";
    public static final File NA12878_8000 = new File(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam");
    public static final File NA12878_20_21 = new File(TEST_DATA_DIR + "NA12878.20.21.unmapped.orig.bam");
    final static File NA12878_500 = new File(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.500.bam");
}
