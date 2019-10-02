package htsjdk.samtools.cram.common;

import java.util.Objects;

/**
 * A class to represent a version information, 3 number: major, minor and build number.
 */
public class Version implements Comparable<Version> {
    public final int major;
    public final int minor;

    public Version(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
    }

    public Version(final String version) {
        final String[] numbers = version.split("[\\.\\-b]");
        major = Integer.parseInt(numbers[0]);
        minor = Integer.parseInt(numbers[1]);
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
    public int compareTo(final Version o) {
        if (o == null) return -1;
        if (major - o.major != 0) {
            return major - o.major;
        }
        return minor - o.minor;
    }

    public boolean compatibleWith(final Version version) {
        return compareTo(version) >= 0;
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
        Version version = (Version) o;
        return major == version.major &&
                minor == version.minor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }
}