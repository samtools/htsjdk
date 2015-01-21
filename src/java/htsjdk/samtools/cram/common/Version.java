package htsjdk.samtools.cram.common;

public class Version implements Comparable<Version> {
    public final int major;
    public final int minor;
    public final int build;

    public Version(int major, int minor, int build) {
        this.major = major;
        this.minor = minor;
        this.build = build;
    }

    public Version(String version) {
        String[] numbers = version.split("[\\.\\-b]");
        major = Integer.valueOf(numbers[0]);
        minor = Integer.valueOf(numbers[1]);
        if (numbers.length > 3)
            build = Integer.valueOf(numbers[3]);
        else
            build = 0;
    }

    @Override
    public String toString() {
        if (build > 0)
            return String.format("%d.%d-b%d", major, minor, build);
        else
            return String.format("%d.%d", major, minor);
    }

    @Override
    public int compareTo(Version o) {
        if (major - o.major != 0)
            return major - o.major;
        if (minor - o.minor != 0)
            return minor - o.minor;

        if (build < 1 || o.build < 1)
            return 0;
        return build - o.build;
    }
}