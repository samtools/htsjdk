package htsjdk.samtools.cram.common;

import java.util.HashSet;
import java.util.Set;

public final class CramVersions {
    public static final CRAMVersion CRAM_v2_1 = new CRAMVersion(2, 1);
    public static final CRAMVersion CRAM_v3 = new CRAMVersion(3, 0);

    final static Set<CRAMVersion> supportedCRAMVersions = new HashSet<CRAMVersion>() {{
        add(CRAM_v2_1);
        add(CRAM_v3);
    }};

    /**
     * The default CRAM version when creating a new CRAM output file or stream.
     */
    public static final CRAMVersion DEFAULT_CRAM_VERSION = CRAM_v3;

    /**
     * Return true if {@code candidateVersion} is a supported CRAM version.
     * @param candidateVersion version to test
     * @return true if {@code candidateVersion} is a supported CRAM version othrwise false
     */
    public static boolean isSupportedVersion(final CRAMVersion candidateVersion) {
        return supportedCRAMVersions.contains(candidateVersion);
    }

}
