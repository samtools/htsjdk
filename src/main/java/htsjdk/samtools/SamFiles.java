package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.build.CramIO;

import java.io.File;
import java.io.IOException;

/**
 * @author mccowan
 */
public class SamFiles {

    /**
     * Finds the index file associated with the provided SAM file.  The index file must exist and be reachable to be found.
     *
     * If the file is a symlink and the index cannot be found, try to unsymlink the file and look for the bai in the actual file path.
     *
     * @return The index for the provided SAM, or null if one was not found.
     */
    public static File findIndex(final File samFile) {
        final File indexFile = lookForIndex(samFile); //try to find the index
        if (indexFile == null) {
            return unsymlinkAndLookForIndex(samFile);
        } else {
            return indexFile;
        }
    }

    /**
     * resolve the canonical path of samFile and attempt to find an index there.
     * @return an index file or null if no index is found.
     */
    private static File unsymlinkAndLookForIndex(File samFile) {
        try {
            final File canonicalSamFile = samFile.getCanonicalFile();
            final File canonicalIndexFile = lookForIndex(canonicalSamFile);
            if ( canonicalIndexFile != null) {
                System.err.println("The index file " + canonicalIndexFile.getPath()
                        + " was found by resolving the canonical path of a symlink: "
                        + samFile.getPath() + " -> " + samFile.getCanonicalPath());
            }
            return canonicalIndexFile;
        } catch (IOException e) {
            return null;
        }
    }

    private static File lookForIndex(final File samFile) {// If input is foo.bam, look for foo.bai
        File indexFile;
        final String fileName = samFile.getName();
        if (fileName.endsWith(BamFileIoUtils.BAM_FILE_EXTENSION)) {
            final String bai = fileName.substring(0, fileName.length() - BamFileIoUtils.BAM_FILE_EXTENSION.length()) + BAMIndex.BAMIndexSuffix;
            indexFile = new File(samFile.getParent(), bai);
            if (indexFile.isFile()) {
                return indexFile;
            }


        } else if (fileName.endsWith(CramIO.CRAM_FILE_EXTENSION)) {
            final String crai = fileName.substring(0, fileName.length() - CramIO.CRAM_FILE_EXTENSION.length()) + CRAIIndex.CRAI_INDEX_SUFFIX;
            indexFile = new File(samFile.getParent(), crai);
            if (indexFile.isFile()) {
                return indexFile;
            }

            indexFile = new File(samFile.getParent(), samFile.getName() + CRAIIndex.CRAI_INDEX_SUFFIX);
            if (indexFile.isFile()) {
                return indexFile;
            }
        }

        // If foo.bai doesn't exist look for foo.bam.bai
        indexFile = new File(samFile.getParent(), samFile.getName() + BAMIndex.BAMIndexSuffix);
        if (indexFile.isFile()) {
            return indexFile;
        }

        return null;
    }
}
