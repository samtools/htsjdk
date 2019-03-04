package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncReadTaskRunnerTest extends HtsjdkTest {
    private static final Log log = Log.getInstance(AsyncReadTaskRunnerTest.class);
    public class CountingAsyncReadTaskRunner extends AsyncReadTaskRunner<Integer, Integer> {
        private AtomicInteger readCalledCount = new AtomicInteger();
        private AtomicInteger readCompleteCount = new AtomicInteger();
        private AtomicInteger transformCalledCount = new AtomicInteger();
        private AtomicInteger transformCompleteCount = new AtomicInteger();
        private int readSleepTime = 0;
        private int transformSleepTime = 0;
        private int stopAfter = Integer.MAX_VALUE;
        private int readSleepIncrement = 0;
        private int getTransformSleepIncrement = 0;
        private final RuntimeException readException;
        private final RuntimeException transformException;
        private final int readExceptionOn;
        private final int transformExceptionOn;
        public CountingAsyncReadTaskRunner(int recordsPerBatch, int batches) {
            super(recordsPerBatch, batches);
            readExceptionOn = Integer.MAX_VALUE;
            transformExceptionOn = Integer.MAX_VALUE;
            readException = null;
            transformException = null;
        }
        public CountingAsyncReadTaskRunner(
                int recordsPerBatch, int batches,
                RuntimeException readException, RuntimeException transformException,
                int readExceptionOn, int transformExceptionOn) {
            super(recordsPerBatch, batches);
            this.readException = readException;
            this.transformException = transformException;
            this.readExceptionOn = readExceptionOn;
            this.transformExceptionOn = transformExceptionOn;
        }

        @Override
        public Integer nextRecord() throws IOException {
            Integer x = super.nextRecord();
            if (readException != null) {
                if (x == readExceptionOn) {
                    Assert.fail("Read ahead exception should have been raised");
                }
                // Assert.assertNotEquals(x, readExceptionOn);
            }
            if (transformException != null) {
                if (x == transformExceptionOn) {
                    Assert.fail("Transform exception should have been raised");
                }
            }
            return x;
        }

        @Override
        public Tuple<Integer, Long> performReadAhead(long bufferBudget) throws IOException {
            assert(bufferBudget > 0);
            int count = readCalledCount.incrementAndGet();
            //log.error(String.format("performReadAhead start %d @ %d", count, System.nanoTime()));
            int sleepTime = readSleepTime + (count - 1) * readSleepIncrement;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                }
            }
            int complete = readCompleteCount.incrementAndGet();
            //log.error(String.format("performReadAhead complete %d @ %d", count, System.nanoTime()));
            Assert.assertEquals(count, complete);
            if (count == readExceptionOn) {
                throw readException;
            }
            return new Tuple<>(count <= stopAfter ? count : null, 1L);
        }

        @Override
        public Integer transform(Integer record) {
            Assert.assertNotNull(record); // sentinel EOF should not be transformed
            int count = transformCalledCount.incrementAndGet();
            int sleepTime = transformSleepTime + (count - 1) * getTransformSleepIncrement;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                }
            }
            transformCompleteCount.incrementAndGet();
            int rec = record;
            if (rec == transformExceptionOn) {
                throw transformException;
            }
            return record;
        }

        public void setTransformSleepTime(int transformSleepTime) {
            this.transformSleepTime = transformSleepTime;
        }

        public void setStopAfter(int stopAfter) {
            this.stopAfter = stopAfter;
        }

        public void setReadSleepIncrement(int readSleepIncrement) {
            this.readSleepIncrement = readSleepIncrement;
        }

        public void setGetTransformSleepIncrement(int getTransformSleepIncrement) {
            this.getTransformSleepIncrement = getTransformSleepIncrement;
        }
    }
    @Test
    public void testDisableAsyncProcessingLetsExistingTasksComplete() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10);
        runner.setGetTransformSleepIncrement(10);
        runner.setStopAfter(4);
        runner.nextRecord();
        Thread.sleep(1);
        // 4 records + EOF
        Assert.assertEquals(runner.readCompleteCount.get(), 5);
        // should have up to 3 transform tasks still running
        // Assert.assertEquals(runner.sync(() -> runner.transformCompleteCount), 1);
        runner.flushAsyncProcessing();
        // should have let the background task run to completion
        // Transform is not called on the EOF indicator
        Assert.assertEquals(runner.transformCompleteCount.get(), 4);
    }
    @Test
    public void testReadAheadExceptionIsPassedToCaller() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10,
                new RuntimeException("Test"), null,
                4, Integer.MAX_VALUE);
        runner.nextRecord();
        runner.nextRecord();
        runner.nextRecord();
        try {
            runner.nextRecord();
            Assert.fail("Exception not raised");
        } catch (RuntimeException e) {
            Assert.assertEquals(e.getMessage(), "Test");
        }
    }
    @Test
    public void testTransformExceptionIsPassedToCaller() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10,
                null, new RuntimeException("Test"),
                Integer.MAX_VALUE, 4);
        runner.nextRecord();
        runner.nextRecord();
        runner.nextRecord();
        try {
            runner.nextRecord();
            Assert.fail("Exception not raised");
        } catch (RuntimeException e) {
            Assert.assertEquals(e.getMessage(), "Test");
        }
    }
    @Test
    public void testRecordOrderingIsStable() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 12);
        runner.setStopAfter(8);
        runner.setGetTransformSleepIncrement(-1);
        runner.setTransformSleepTime(8);
        int[] result = new int[8];
        for (int i = 0; i < 8; i++) {
            result[i] = runner.nextRecord();
        }
        for (int i = 0; i < 8; i++) {
            Assert.assertEquals(i + 1, result[i]);
        }
    }

    @Test
    public void testBatchingIsPerformed() throws Exception {
        Executor defaultPool = AsyncReadTaskRunner.getNonBlockingThreadpool();
        AsyncReadTaskRunner.setNonblockingThreadpool(Executors.newFixedThreadPool(4));
        try {
            CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(2, 2);
            runner.setStopAfter(4);
            runner.setTransformSleepTime(10);
            runner.startAsyncProcessing();
            Thread.sleep(5);
            // 2 batches of 2
            Assert.assertEquals(runner.readCompleteCount.get(), 4);
            // Thread scheduling/timing sanity check: nothing should have finished yet
            Assert.assertEquals(runner.transformCompleteCount.get(), 0);
            // only the first record of each batch should have tasks scheduled
            Assert.assertEquals(runner.transformCalledCount.get(), 2);
        } finally {
            AsyncReadTaskRunner.setNonblockingThreadpool(defaultPool);
        }
    }

    @Test
    public void testBufferLimitIsRespected() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(2, 4);
        runner.setStopAfter(10);
        runner.startAsyncProcessing();
        Thread.sleep(10);
        // should have only read 4 records
        Assert.assertEquals(runner.readCompleteCount.get(), 8);
    }
    @Test
    public void testReadAheadContinuesAfterBatchIsRead() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(2, 4);
        runner.setStopAfter(10);
        runner.startAsyncProcessing();
        Thread.sleep(10);
        // should have only read 4 records
        Assert.assertEquals(runner.readCompleteCount.get(), 8);
        runner.nextRecord();
        Thread.sleep(1);
        Assert.assertEquals(runner.readCompleteCount.get(), 8);
        runner.nextRecord();
        // ok, now that we've readthe first batch, we should now have run the next batch
        Thread.sleep(10);
        Assert.assertEquals(runner.readCompleteCount.get(), 10);
    }
    @Test
    public void testReadExceptionIsRaisedOnSameRecordAsWhenSynchronousProcessing() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(4, 2,
                new RuntimeException("Read"), null,
                3, Integer.MAX_VALUE);
        runner.startAsyncProcessing();
        Thread.sleep(10);
        runner.nextRecord();
        runner.nextRecord();
        try {
            runner.nextRecord();
        } catch (RuntimeException e) {
            Assert.assertEquals(e, runner.readException);
        }
    }
    @Test
    public void testTransformExceptionIsRaisedOnSameRecordAsWhenSynchronousProcessing() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(4, 2,
                null, new RuntimeException("Test"),
                Integer.MAX_VALUE, 3);
        runner.startAsyncProcessing();
        Thread.sleep(10);
        runner.nextRecord();
        runner.nextRecord();
        try {
            runner.nextRecord();
        } catch (RuntimeException e) {
            Assert.assertEquals(e, runner.transformException);
        }
    }
    @Test
    public void stressTest() {
        for (int i = 0 ; i < 1000000; i++) {
            runStressTestTask(i);
        }
    }
    private void runStressTestTask(int seed) {
        int buffer = seed % 8 + 1;
        int batch = seed % buffer + 1;
        int nRecords = seed % 64;
        boolean raiseException = seed % 2 == 0;
        int raiseExceptionOn = nRecords == 0 ? 0 : (seed >> 16) % (nRecords + 2);
        boolean exceptionOnRead = (seed >> 4) % 2 == 0;
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(batch, buffer,
                raiseException && exceptionOnRead ? new RuntimeException("Test") : null,
                raiseException && !exceptionOnRead ? new RuntimeException("Test") : null,
                raiseException && exceptionOnRead ? raiseExceptionOn + 1 : Integer.MAX_VALUE,
                raiseException && !exceptionOnRead ? raiseExceptionOn + 1 : Integer.MAX_VALUE);
        int resetAfter = nRecords == 0 ? 0 : (seed >> 8) % nRecords;
        int n = -1;
        boolean expectEof = nRecords == 0;
        for (int i = 0; i < nRecords + 1; i++) {
            try {
                Integer next = runner.nextRecord();
                if (next == null) {
                    Assert.assertTrue(expectEof);
                    return;
                } else {
                    Assert.assertTrue(next > n);
                    n = next;
                    expectEof = next == nRecords;
                }
                if (raiseException) {
                    if (raiseExceptionOn == i) {
                        Assert.fail("Exception should have been raised");
                    }
                }
                if (next == resetAfter) {
                    runner.disableAsyncProcessing();
                    runner.enableAsyncProcessing();
                    expectEof = true; // we might be at EOF - we won't know till we read the next record
                }
            } catch (Exception e) {
                Assert.assertTrue(raiseException);
                Assert.assertEquals(i, raiseExceptionOn);
                Assert.assertEquals(e.getMessage(), "Test");
                return;
            }
        }
    }
}