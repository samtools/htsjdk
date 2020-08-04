package htsjdk.beta.plugin;

import htsjdk.utils.ValidationUtils;

/**
 * A codec (file format) version.
 */
//TODO: this should have a different name (HtsVersion ?) since it refers to both codec and format
public class HtsCodecVersion implements Comparable<HtsCodecVersion> {

    private static final String FORMAT_STRING = "%d.%d.%d";

    private final int majorVersion;
    private final int minorVersion;
    private final int patchVersion;

    public HtsCodecVersion(final int major, final int minor, final int patch) {
        this.majorVersion = major;
        this.minorVersion = minor;
        this.patchVersion = patch;
    }

    public HtsCodecVersion(final String versionString) {
        ValidationUtils.nonNull(versionString);
        final String[] parts = versionString.split(".", 0);
        if (parts.length != 3) {
            throw new IllegalArgumentException(String.format("Can parse version string: '%s'", versionString));
        }
        try {
            majorVersion = Integer.parseInt(parts[0]);
            minorVersion = Integer.parseInt(parts[1]);
            patchVersion = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Can parse version string: '%s'", versionString));
        }
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getPatchVersion() {
        return patchVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HtsCodecVersion)) return false;

        HtsCodecVersion codecVersion = (HtsCodecVersion) o;

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

    //TODO: needs tests
    //a negative integer, zero, or a positive integer as this object
    //is less than, equal to, or greater than the specified object.
    @Override
    public int compareTo(HtsCodecVersion o) {
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
