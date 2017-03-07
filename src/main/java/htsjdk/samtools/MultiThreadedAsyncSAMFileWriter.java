package htsjdk.samtools;

/**
 * Created by farjoun on 2/25/17.
 */
public class MultiThreadedAsyncSAMFileWriter extends AsyncSAMFileWriter {

    public MultiThreadedAsyncSAMFileWriter(SAMFileWriter out) {
        super(out);
    }

    public MultiThreadedAsyncSAMFileWriter(SAMFileWriter out, int queueSize) {
        super(out, queueSize);
    }
}
