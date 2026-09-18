package htsjdk.tribble.index.tabix;

import htsjdk.samtools.util.FileExtensions;

/**
 * The two file formats a tabix index can take. Both hold the same information; CSI also stores the binning
 * scheme, so it can address sequences longer than the 2^29 bases TBI's fixed scheme allows.
 */
public enum TabixIndexType {
    /** The original tabix format, {@code .tbi}. */
    TBI(FileExtensions.TABIX_INDEX),
    /** The coordinate-sorted index format, {@code .csi}. */
    CSI(FileExtensions.CSI);

    private final String extension;

    TabixIndexType(final String extension) {
        this.extension = extension;
    }

    /**
     * @return the extension, including the dot, appended to the indexed file's name to name its index
     */
    public String getExtension() {
        return extension;
    }
}
