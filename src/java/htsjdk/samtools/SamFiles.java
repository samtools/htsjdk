package htsjdk.samtools;

import java.io.File;

/**
 * @author mccowan
 */
public class SamFiles {
    public static final String CRAI_SUFFIX = ".crai";
    public static final String CRAM_SUFFIX = ".cram";

    /**
     * Finds the index file associated with the provided SAM file.  The index file must exist and be reachable to be found.
     *
     * @return The index for the provided SAM, or null if one was not found.
     */
    public static File findIndex(final File samFile) {
        // If input is foo.bam, look for foo.bai
        File indexFile;
        final String fileName = samFile.getName();
        if (fileName.endsWith(BamFileIoUtils.BAM_FILE_EXTENSION)) {
            final String bai = fileName.substring(0, fileName.length() - BamFileIoUtils.BAM_FILE_EXTENSION.length()) + BAMIndex.BAMIndexSuffix;
            indexFile = new File(samFile.getParent(), bai);
            if (indexFile.isFile())
                return indexFile;


        } else if (fileName.endsWith(CRAM_SUFFIX)) {
            final String crai = fileName.substring(0, fileName.length() - CRAM_SUFFIX.length()) + CRAI_SUFFIX;
            indexFile = new File(samFile.getParent(), crai);
            if (indexFile.isFile())
                return indexFile;

            indexFile = new File(samFile.getParent(), samFile.getName() + CRAI_SUFFIX);
            if (indexFile.isFile())
                return indexFile;
        }

        // If foo.bai doesn't exist look for foo.bam.bai
        indexFile = new File(samFile.getParent(), samFile.getName() + BAMIndex.BAMIndexSuffix);
        if (indexFile.isFile())
            return indexFile;

        return null;
    }
}
