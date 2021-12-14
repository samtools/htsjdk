package htsjdk.beta.plugin;

import htsjdk.utils.ValidationUtils;

/**
 * A class for representing 3-part versions with major, minor and patch segments. Used by
 * {@link HtsCodec}, {@link HtsEncoder} and {@link HtsDecoder} for HTS file format and codec
 * versions.
 */
public class HtsVersion implements Comparable<HtsVersion> {
    /** Sentinel constant to match any version */
    public static int ANY_VERSION = -1;
    /** Sentinel constant used to indicate the newest version available */
    public static final HtsVersion NEWEST_VERSION = new HtsVersion(ANY_VERSION, ANY_VERSION, ANY_VERSION);

    private static final String FORMAT_STRING = "%d.%d.%d";

    private final int majorVersion;
    private final int minorVersion;
    private final int patchVersion;

    /**
     * Construct a 3 part version number.
     *
     * @param major major version number
     * @param minor minor version number
     * @param patch patch number
     */
    public HtsVersion(final int major, final int minor, final int patch) {
        this.majorVersion = major;
        this.minorVersion = minor;
        this.patchVersion = patch;
    }

    /**
     * Construct a 3 part version number from a string withe the format {@code major.minor.patch}, where
     * each of major/minor/patch is an integer.
     *
     * @param versionString the version string from which to construct this version
     */
    public HtsVersion(final String versionString) {
        ValidationUtils.nonNull(versionString);
        final String[] parts = versionString.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException(String.format("Unable parse version string as major.minor.patch: '%s'", versionString));
        }
        try {
            majorVersion = Integer.parseInt(parts[0]);
            minorVersion = Integer.parseInt(parts[1]);
            patchVersion = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Can't parse version string: '%s'", versionString));
        }
    }

    /**
     * Get the major version integer for this version.
     *
     * @return the major version integer for this version
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Get the minor version integer for this version.
     *
     * @return the minor version integer for this version
     */
    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * Get the patch version integer for this version.
     *
     * @return the patch version integer for this version
     */
    public int getPatchVersion() {
        return patchVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HtsVersion)) return false;

        HtsVersion codecVersion = (HtsVersion) o;

        if (getMajorVersion() != codecVersion.getMajorVersion()) return false;
        if (getMinorVersion() != codecVersion.getMinorVersion()) return false;
        return getPatchVersion() == codecVersion.getPatchVersion();
    }

    @Override
    public int hashCode() {
        int result = getMajorVersion();
        result = 31 * result + getMinorVersion();
        result = 31 * result + getPatchVersion();
        return result;
    }

    @Override
    public String toString() {
        return String.format(FORMAT_STRING, getMajorVersion(), getMinorVersion(), getPatchVersion());
    }

    @Override
    public int compareTo(HtsVersion o) {
        ValidationUtils.nonNull(o);
        if (this.majorVersion == o.majorVersion) {
            if (this.minorVersion == o.minorVersion) {
                if (this.patchVersion == o.patchVersion){
                    return 0;
                }
                return this.patchVersion - o.patchVersion;
            } else {
                return this.minorVersion - o.minorVersion;
            }
        } else {
            return this.majorVersion - o.majorVersion;
        }
    }
}
