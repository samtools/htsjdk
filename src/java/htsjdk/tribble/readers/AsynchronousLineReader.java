package htsjdk.tribble.readers;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;

import java.io.Reader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A LineReader implementation that delegates the work of reading and fetching lines to another thread.  The thread terminates when it
 * encounters EOF in the underlying reader, or when this LineReader is closed.
 *
 * @author mccowan
 */
public class AsynchronousLineReader implements LineReader {
    public static final int DEFAULT_NUMBER_LINES_BUFFER = 100;
    private static final Log log = Log.getInstance(AsynchronousLineReader.class);

    private final LongLineBufferedReader bufferedReader;
    private final BlockingQueue<String> lineQueue;

    private final Worker workerRunnable;
    private final Thread workerThread;

    private final AtomicReference<Throwable> workerException = new AtomicReference<>(null);
    private final AtomicBoolean eofReached = new AtomicBoolean(false);

    public AsynchronousLineReader(final Reader reader, final int lineReadAheadSize) {
        bufferedReader = new LongLineBufferedReader(reader);
        lineQueue = new LinkedBlockingQueue<>(lineReadAheadSize);
        workerRunnable = new Worker();
        workerThread = new Thread(workerRunnable, "LineReader");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("started");
    }

    public AsynchronousLineReader(final Reader reader) {
        this(reader, DEFAULT_NUMBER_LINES_BUFFER);
    }

    @Override
    public String readLine() {
        try {
            // Continually poll until we get a result, unless the underlying reader is finished.
            for (; ; ) {
                checkAndThrowIfWorkerException();
                final String pollResult = this.lineQueue.poll(100L, TimeUnit.MILLISECONDS); // Not ideal for small files.
                if (pollResult == null) {
                    if (eofReached.get()) {
                        checkAndThrowIfWorkerException();
                        return lineQueue.poll(); // If there is nothing left, returns null as expected.  Otherwise, grabs next element.
                    }
                } else {
                    return pollResult;
                }
            }
        } catch (final InterruptedException e) {
            throw new TribbleException("Line polling interrupted.", e);
        }
    }

    private void checkAndThrowIfWorkerException() {
        final Throwable t = this.workerException.get();//copy to a temp so that it does not get reset in the meantime
        if (t != null) {
            throw new TribbleException("Exception encountered in workerThread thread.", t);
        }
    }

    @Override
    public void close() {
        workerRunnable.terminate();
        workerThread.interrupt(); // Allow the workerThread to close gracefully.
        try {
            workerThread.join();  //wait for the workerThread to actually finish
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();  //remember that we got interrupted
        }
        //now that the workerThread is dead, we can close the buffer
        CloserUtil.close(bufferedReader);
        log.info("closed");
    }

    private class Worker implements Runnable {
        private AtomicBoolean terminate = new AtomicBoolean(false);

        /**
         * Indicate to this thread that it should stop.
         */
        public void terminate() {
            terminate.set(true);
        }

        @Override
        public void run() {
            try {
                while (! terminate.get()) {
                    try {
                        while (true) {
                            final String line = bufferedReader.readLine();
                            if (line == null) {
                                eofReached.set(true);
                                terminate.set(true);
                                break;
                            } else {
                                lineQueue.put(line);
                            }
                        }
                    } catch (final InterruptedException e) {
                        // Reset interrupt status
                        Thread.interrupted();
                    }
                }
            } catch (final Throwable e) {
                workerException.compareAndSet(null, e);
            }
        }
    }
}
