package htsjdk.samtools.cram.common;

import htsjdk.samtools.util.Log;

/**
 * The class provides version-dependant rules and policies for CRAM data.
 */
public class CramVersionPolicies {
    private static final Log log = Log.getInstance(CramVersionPolicies.class);

    /**
     * The method holds the behaviour for when the EOF marker is not found. Depending on the CRAM version this will be ignored, a warning
     * issued or an exception produced.
     *
     * @param version CRAM version to assume
     */
    public static void eofNotFound(final Version version) {
        if (version.compatibleWith(CramVersions.CRAM_v3)) {
            log.error("Incomplete data: EOF marker not found.");
            throw new RuntimeException("EOF not found.");
        }
        if (version.compatibleWith(CramVersions.CRAM_v2_1)) log.warn("EOF marker not found, possibly incomplete file/stream.");
    }
}
