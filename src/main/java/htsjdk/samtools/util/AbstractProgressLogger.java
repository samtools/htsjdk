package htsjdk.samtools.util;

import htsjdk.samtools.SAMRecord;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Abstract implementation of a Little progress logging class to facilitate consistent output of useful information when progressing
 * through a stream of SAM records.
 *
 * Concrete subclasses must provide the logger
 */
abstract public class AbstractProgressLogger implements ProgressLoggerInterface {
    private final int n;
    private final String verb;
    private final String noun;
    private long startTime;
    private final NumberFormat fmt = new DecimalFormat("#,###");
    private final NumberFormat timeFmt = new DecimalFormat("00");
    private long processed = 0;
    // Set to -1 until the first record is added
    private long lastStartTime = -1;
    private String lastChrom = null;
    private int lastPos = 0;
    private String lastReadName = null;
    private long countNonIncreasing = 0;
    final static private long PRINT_READ_NAME_THRESHOLD = 1000;

    /**
     * Construct an AbstractProgressLogger.
     *
     * This must be called by any subclasses
     *
     * @param n the frequency with which to output (i.e. every N records)
     * @param verb the verb to log, e.g. "Processed, Read, Written".
     * @param noun the noun to use when logging, e.g. "Records, Variants, Loci"
     */
    protected AbstractProgressLogger(final String noun, final String verb, final int n) {
        this.noun = noun;
        this.verb = verb;
        this.n = n;
        reset();
    }

    /**
     * Log a message to whatever logger is being used
     *
     * @param message a message to be logged by the logger (recommended output level is INFO or the equivalent)
     */
    abstract protected void log(String ... message);

    private synchronized void record() {
        final long now = System.currentTimeMillis();
        final long lastPeriodSeconds = (now - this.lastStartTime) / 1000;
        this.lastStartTime = now;

        final long seconds = (now - startTime) / 1000;
        final String elapsed   = formatElapseTime(seconds);
        final String period    = pad(fmt.format(lastPeriodSeconds), 4);
        final String processed = pad(fmt.format(this.processed), 13);

        final String readInfo;
        if (this.lastChrom == null) readInfo = "*/*";
        else readInfo = this.lastChrom + ":" + fmt.format(this.lastPos);

        final String rnInfo;

        if (lastReadName != null && countNonIncreasing > PRINT_READ_NAME_THRESHOLD) {
            rnInfo = ".  Last read name: " + lastReadName;
        } else {
            rnInfo = "";
        }

        final long n = (this.processed % this.n == 0) ? this.n : this.processed % this.n;

        log(this.verb, " ", processed, " " + noun + ".  Elapsed time: ", elapsed, "s.  Time for last ", fmt.format(n),
                ": ", period, "s.  Last read position: ", readInfo, rnInfo);
    }

    /**
     * Logs the last last record if it wasn't previously logged.
     * @return boolean true if logging was triggered, false otherwise
     */
    public synchronized boolean log() {
        if (processed % this.n != 0) {
            record();
            return true;
        }
        else {
            return false;
        }
    }

    protected synchronized boolean record(final String chrom, final int pos, final String rname) {
        if (chrom != null && chrom.equals(lastChrom) && pos < lastPos) {
            countNonIncreasing++;
            if (countNonIncreasing == PRINT_READ_NAME_THRESHOLD) {
                log("Seen many non-increasing record positions. Printing Read-names as well.");
            }
        } else {
            lastChrom = chrom;
        }
        lastPos = pos;
        lastReadName = rname;
        if (lastStartTime == -1) {
            lastStartTime = System.currentTimeMillis();
        }
        if (++processed % n == 0) {
            record();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean record(final String chrom, final int pos) {
        return record(chrom, pos, null);
    }

    /**
     * Records that a given record has been processed and triggers logging if necessary.
     * @return boolean true if logging was triggered, false otherwise
     */
    @Override
    public synchronized boolean record(final SAMRecord rec) {
        if (SAMRecord.NO_ALIGNMENT_REFERENCE_NAME.equals(rec.getReferenceName())) {
            return record(null, 0, rec.getReadName());
        } else {
            return record(rec.getReferenceName(), rec.getAlignmentStart(), rec.getReadName());
        }
    }

    /** Records multiple SAMRecords and triggers logging if necessary. */
    @Override
    public boolean record(final SAMRecord... recs) {
        boolean triggered = false;
        for (final SAMRecord rec : recs) {
            triggered = record(rec) || triggered;
        }
        return triggered;
    }

    /** Returns the count of records processed. */
    public synchronized long getCount() { return this.processed; }

    /** Returns the number of seconds since progress tracking began. */
    public long getElapsedSeconds() { return (System.currentTimeMillis() - this.startTime) / 1000; }

    /** Resets the start time to now and the number of records to zero. */
    public synchronized void reset() {
        startTime = System.currentTimeMillis();
        processed = 0;
        // Set to -1 until the first record is added
        lastStartTime = -1;
        lastChrom = null;
        lastPos = 0;
        lastReadName = null;
        countNonIncreasing = 0;
    }

    /** Left pads a string until it is at least the given length. */
    static String pad(final String in, final int length) {
        final StringBuilder inBuilder = new StringBuilder(Math.max(length, in.length()));
        while (inBuilder.length() < length - in.length()) {
            inBuilder.append(" ");
        }

        return inBuilder.append(in).toString();
    }

    /** Formats a number of seconds into hours:minutes:seconds. */
    private String formatElapseTime(final long seconds) {
        final long s = seconds % 60;
        final long allMinutes = seconds / 60;
        final long m = allMinutes % 60;
        final long h = allMinutes / 60;

        return timeFmt.format(h) + ":" + timeFmt.format(m) + ":" + timeFmt.format(s);
    }
}
