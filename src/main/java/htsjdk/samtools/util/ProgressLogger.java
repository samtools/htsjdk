package htsjdk.samtools.util;

/**
 * Little progress logging class to facilitate consistent output of useful information when progressing
 * through a stream of SAM records.
 *
 * @author Tim Fennell
 */
public class ProgressLogger extends AbstractProgressLogger {

    private final Log log;

    /**
     * Construct a progress logger.
     * @param log the Log object to write outputs to
     * @param n the frequency with which to output (i.e. every N records)
     * @param verb the verb to log, e.g. "Processed, Read, Written".
     * @param noun the noun to use when logging, e.g. "Records, Variants, Loci"
     */
    public ProgressLogger(final Log log, final int n, final String verb, final String noun) {
        super(noun, verb, n);
        this.log = log;
    }

    /**
     * Construct a progress logger.
     * @param log the Log object to write outputs to
     * @param n the frequency with which to output (i.e. every N records)
     * @param verb the verb to log, e.g. "Processed, Read, Written".
     */
    public ProgressLogger(final Log log, final int n, final String verb) {
        this(log, n, verb, "records");
    }

    /**
     * Construct a progress logger with the desired log and frequency and the verb "Processed".
     * @param log the Log object to write outputs to
     * @param n the frequency with which to output (i.e. every N records)
     */
    public ProgressLogger(final Log log, final int n) { this(log, n, "Processed"); }

    /**
     * Construct a progress logger with the desired log, the verb "Processed" and a period of 1m records.
     * @param log the Log object to write outputs to
     */
    public ProgressLogger(final Log log) { this(log, 1000000); }

    @Override
    protected void log(final String... message) {
        log.info((Object[])message);
    }
}
