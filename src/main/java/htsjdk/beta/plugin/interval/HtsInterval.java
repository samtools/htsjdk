package htsjdk.beta.plugin.interval;

public interface HtsInterval {
    String getQueryName();

    long getStart();

    long getEnd();
}
