package htsjdk.samtools.util.htsget;

public class HtsgetMalformedResponseException extends RuntimeException {
    public HtsgetMalformedResponseException() {}

    public HtsgetMalformedResponseException(final String s) {
        super(s);
    }

    public HtsgetMalformedResponseException(final String s, final Throwable throwable) {
        super(s, throwable);
    }

    public HtsgetMalformedResponseException(final Throwable throwable) {
        super(throwable);
    }
}