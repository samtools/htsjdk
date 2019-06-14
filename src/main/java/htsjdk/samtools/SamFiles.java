package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author mccowan
 */
public class SamFiles {

    private final static Log LOG = Log.getInstance(SamFiles.class);

    /**
     * Finds the index file associated with the provided SAM file.  The index file must exist and be reachable to be found.
     *
     * If the file is a symlink and the index cannot be found, try to unsymlink the file and look for the bai in the actual file path.
     *
     * @return The index for the provided SAM, or null if one was not found.
     */
    public static File findIndex(final File samFile) {
        final Path path = findIndex(IOUtil.toPath(samFile));
        return path == null ? null : path.toFile();
    }

    /**
     * Finds the index file associated with the provided SAM file.  The index file must exist and be reachable to be found.
     *
     * If the file is a symlink and the index cannot be found, try to unsymlink the file and look for the bai in the actual file path.
     *
     * @return The index for the provided SAM, or null if one was not found.
     */
    public static Path findIndex(final Path samPath) {
        final Path indexPath = lookForIndex(samPath); //try to find the index
        if (indexPath == null) {
            return unsymlinkAndLookForIndex(samPath);
        } else {
            return indexPath;
        }
    }

    /**
     * resolve the canonical path of samFile and attempt to find an index there.
     * @return an index file or null if no index is found.
     */
    private static Path unsymlinkAndLookForIndex(Path samPath) {
        try {
            final Path canonicalSamPath = samPath.toRealPath(); // resolve symbolic links
            final Path canonicalIndexPath = lookForIndex(canonicalSamPath);
            if ( canonicalIndexPath != null) {
                LOG.warn("The index file " + canonicalIndexPath.toAbsolutePath()
                        + " was found by resolving the canonical path of a symlink: "
                        + samPath.toAbsolutePath() + " -> " + samPath.toRealPath());
            }
            return canonicalIndexPath;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path lookForIndex(final Path samPath) {// If input is foo.bam, look for foo.bai or foo.csi
        Path indexPath;
        final String fileName = samPath.getFileName().toString(); // works for all path types (e.g. HDFS)
        if (fileName.endsWith(FileExtensions.BAM)) {
            final String bai = fileName.substring(0, fileName.length() - FileExtensions.BAM.length()) + FileExtensions.BAM_INDEX;
            final String csi = fileName.substring(0, fileName.length() - FileExtensions.BAM.length()) + FileExtensions.CSI;
            indexPath = samPath.resolveSibling(bai);
            if (Files.isRegularFile(indexPath)) { // works for all path types (e.g. HDFS)
                return indexPath;
            } else { // if there is no .bai index, look for .csi index
                indexPath = samPath.resolveSibling(csi);
                if (Files.isRegularFile(indexPath)) {
                    return indexPath;
                }
            }


        } else if (fileName.endsWith(FileExtensions.CRAM)) {
            final String crai = fileName.substring(0, fileName.length() - FileExtensions.CRAM.length()) + FileExtensions.CRAM_INDEX;
            indexPath = samPath.resolveSibling(crai);
            if (Files.isRegularFile(indexPath)) {
                return indexPath;
            }

            indexPath = samPath.resolveSibling(fileName + FileExtensions.CRAM_INDEX);
            if (Files.isRegularFile(indexPath)) {
                return indexPath;
            }
        }

        // If foo.bai doesn't exist look for foo.bam.bai or foo.cram.bai
        indexPath = samPath.resolveSibling(fileName + FileExtensions.BAM_INDEX);
        if (Files.isRegularFile(indexPath)) {
            return indexPath;
        } else {
            indexPath = samPath.resolveSibling(fileName + FileExtensions.CSI);
            if (Files.isRegularFile(indexPath)) {
                return indexPath;
            }
        }

        return null;
    }
}
