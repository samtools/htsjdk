package htsjdk.samtools.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Common thread pool for tasks related to asynchronous IO
 */
public class AsynchronousIOThreadPools {
    private static final Executor nonblockingThreadpool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("htsjdk-asyncio-nonblocking");
        t.setDaemon(true);
        return t;
    });

    private static final Executor blockingThreadpool = Executors.newCachedThreadPool(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("htsjdk-asyncio-blocking");
        t.setDaemon(true);
        return t;
    });

    /**
     * Thread pool for non-blocking computational tasks that do not perform any blocking operations.
     */
    public static Executor getNonBlockingThreadpool() { return nonblockingThreadpool; }

    /**
     * Thread pool for blocking IO tasks
     */
    public static Executor getBlockingThreadpool() { return blockingThreadpool; }
}
