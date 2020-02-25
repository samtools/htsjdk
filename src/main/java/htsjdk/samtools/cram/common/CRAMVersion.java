package htsjdk.samtools.cram.common;

import java.util.Objects;

/**
 * A class to represent a version information, 3 number: major, minor and build number.
 */
public final class CRAMVersion implements Comparable<CRAMVersion> {
    private final int major;
    private final int minor;

    public CRAMVersion(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
    }

    public CRAMVersion(final String version) {
        final String[] numbers = version.split("[\\.\\-b]");
        major = Integer.parseInt(numbers[0]);
        minor = Integer.parseInt(numbers[1]);
    }

    /**
     * @return the CRAM major version for this CRAMVersion
     */
    public int getMajor() {
        return major;
    }

    /**
     * @return the CRAM minor version for this CRAMVersion
     */
    public int getMinor() {
        return minor;
    }

    @Override
    public String toString() {
        return String.format("%d.%d", major, minor);
    }

    /**
     * Compare with another version.
     *
     * @param o another version
     * @return 0 if both versions are the same, a negative if the other version is higher and a positive otherwise.
     */
    @Override
    public int compareTo(final CRAMVersion o) {
        if (o == null) return -1;
        if (major - o.major != 0) {
            return major - o.major;
        }
        return minor - o.minor;
    }

    public boolean compatibleWith(final CRAMVersion cramVersion) {
        return compareTo(cramVersion) >= 0;
    }

    /**
     * Check if another version is exactly the same as this one.
     *
     * @param o another version object
     * @return true if both versions are the same, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CRAMVersion cramVersion = (CRAMVersion) o;
        return major == cramVersion.major &&
                minor == cramVersion.minor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }
}