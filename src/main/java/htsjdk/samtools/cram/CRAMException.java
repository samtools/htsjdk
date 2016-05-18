package htsjdk.samtools.cram;

import htsjdk.samtools.SAMException;

/**
 * Created by edwardk on 8/13/15.
 */
public class CRAMException extends SAMException {
    public CRAMException() {}

    public CRAMException(final String s) {
        super(s);
    }

    public CRAMException(final String s, final Throwable throwable) {
        super(s, throwable);
    }

    public CRAMException(final Throwable throwable) {
        super(throwable);
    }
}