package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntSupplier;

public class AsyncReadTaskRunnerTest extends HtsjdkTest {
    public class CountingAsyncReadTaskRunner extends AsyncReadTaskRunner<Integer, Integer> {
        private volatile int readCalledCount;
        private volatile int readCompleteCount;
        private volatile int transformCalledCount;
        private volatile int transformCompleteCount;
        private int readSleepTime = 0;
        private int transformSleepTime = 0;
        private int stopAfter = Integer.MAX_VALUE;
        private int readSleepIncrement = 0;
        private int getTransformSleepIncrement = 0;
        private RuntimeException readException = null;
        private RuntimeException transformException = null;
        private int readExceptionOn = Integer.MAX_VALUE;
        private int transformExceptionOn = Integer.MAX_VALUE;
        public CountingAsyncReadTaskRunner(int minBatchBufferBudget, int minTotalBufferBudget) {
            super(minBatchBufferBudget, minTotalBufferBudget);
        }

        @Override
        public Integer nextRecord() throws IOException {
            Integer x = super.nextRecord();
            if (readException != null) {
                Assert.assertNotEquals(x, readExceptionOn);
            }
            if (transformException != null) {
                Assert.assertNotEquals(x, transformExceptionOn);
            }
            return x;
        }

        @Override
        public Tuple<Integer, Integer> performReadAhead(int bufferBudget) throws IOException {
            int count = sync(() -> ++readCalledCount);
            int sleepTime = readSleepTime + (count - 1) * readSleepIncrement;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                }
            }
            sync(() -> ++readCompleteCount);
            if (count == readExceptionOn) {
                throw readException;
            }
            return new Tuple<>(count <= stopAfter ? count : null, 1);
        }

        @Override
        public Integer transform(Integer record) {
            Assert.assertNotNull(record); // sentinel EOF should not be transformed
            int count = sync(() -> ++transformCalledCount);
            int sleepTime = transformSleepTime + (count - 1) * getTransformSleepIncrement;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                }
            }
            sync(() -> ++transformCompleteCount);
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
        public synchronized int sync(IntSupplier p) {
            return p.getAsInt();
        }

        public void setReadException(RuntimeException readException) {
            this.readException = readException;
        }

        public void setTransformException(RuntimeException transformException) {
            this.transformException = transformException;
        }

        public void setReadExceptionOn(int readExceptionOn) {
            this.readExceptionOn = readExceptionOn;
        }

        public void setTransformExceptionOn(int transformExceptionOn) {
            this.transformExceptionOn = transformExceptionOn;
        }
    }
    @Test
    public void testDisableAsyncProcessingLetsExistingTasksComplete() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10);
        runner.setGetTransformSleepIncrement(5);
        runner.setStopAfter(4);
        runner.nextRecord();
        Thread.sleep(1);
        // 4 records + EOF
        Assert.assertEquals(runner.sync(() -> runner.readCompleteCount), 5);
        // should still have 3 transform tasks running
        Assert.assertEquals(runner.sync(() -> runner.transformCompleteCount), 1);
        runner.disableAsyncProcessing();
        // should have let the background task run to completion
        // Transform is not called on the EOF indicator
        Assert.assertEquals(runner.sync(() -> runner.transformCompleteCount), 4);
    }
    @Test
    public void testReadAheadExceptionIsPassedToCaller() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10);
        runner.setReadException(new RuntimeException("Test"));
        runner.setReadExceptionOn(4);
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
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 10);
        runner.setTransformException(new RuntimeException("Test"));
        runner.setTransformExceptionOn(4);
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
            CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(2, 4);
            runner.setStopAfter(4);
            runner.setTransformSleepTime(10);
            runner.startAsyncProcessing();
            Thread.sleep(5);
            // 2 batches of 2
            Assert.assertEquals(runner.sync(() -> runner.readCompleteCount), 4);
            // Thread scheduling/timing sanity check: nothing should have finished yet
            Assert.assertEquals(runner.sync(() -> runner.transformCompleteCount), 0);
            // only the first record of each batch should have tasks scheduled
            Assert.assertEquals(runner.sync(() -> runner.transformCalledCount), 2);
        } finally {
            AsyncReadTaskRunner.setNonblockingThreadpool(defaultPool);
        }
    }

    @Test
    public void testBufferLimitIsRespected() throws Exception {
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(1, 4);
        runner.setStopAfter(10);
        runner.startAsyncProcessing();
        Thread.sleep(10);
        // should have only read 4 records
        Assert.assertEquals(runner.sync(() -> runner.readCompleteCount), 4);
    }
    //@Test
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
        CountingAsyncReadTaskRunner runner = new CountingAsyncReadTaskRunner(batch, buffer);
        if (raiseException && exceptionOnRead) {
            runner.readException = new RuntimeException("Test");
            runner.readExceptionOn = raiseExceptionOn + 1;
        } else if (raiseException && !exceptionOnRead) {
            runner.transformException = new RuntimeException("Test");
            runner.transformExceptionOn = raiseExceptionOn + 1;
        }
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
                    if (exceptionOnRead || raiseExceptionOn < nRecords) {
                        Assert.assertNotEquals(i, raiseExceptionOn);
                    }
                }
                if (next == resetAfter) {
                    // By doing this we will lose the records that were in-flight
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