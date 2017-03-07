package htsjdk.tribble.util;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 *
 * A generic iterator that will process the elements in parralel prior to providing them in next()
 *
 */
public class ConcurrentMappingIterator<T, R> implements Iterator<R> {

    final protected ArrayBlockingQueue<Future<Queue<R>>> futureQueue;
    protected Queue<R> currentQueue;
    final protected Iterator<T> encapsulatedIterator;
    final protected ExecutorService executorService;
    final protected Function<T, R> map;
    final private int queueSize;

    @Override
    public boolean hasNext() {
        fillQueue();
        return !currentQueue.isEmpty() || !futureQueue.isEmpty() ;
    }

    @Override
    public R next() {
        fillQueue();
        try {

            if (currentQueue.isEmpty()) {
                currentQueue = futureQueue.poll().get();
            }
            return currentQueue.poll();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public ConcurrentMappingIterator(Iterator<T> inputIterator, Function<T, R> map, int numberOfThreads, int sizeOfQueue) {
        assert numberOfThreads > 0;
        assert sizeOfQueue > 0;

        futureQueue = new ArrayBlockingQueue<>(numberOfThreads, true);
        currentQueue = new ArrayBlockingQueue<>(1);

        encapsulatedIterator = inputIterator;
        executorService = Executors.newFixedThreadPool(numberOfThreads);
        this.map = map;
        this.queueSize = sizeOfQueue;
    }

    private void fillQueue() {
        while (encapsulatedIterator.hasNext() && futureQueue.remainingCapacity() > 0) {
            final ArrayBlockingQueue<T> block = new ArrayBlockingQueue<>(this.queueSize);
            while(encapsulatedIterator.hasNext() & block.remainingCapacity()>0) {
                block.add(encapsulatedIterator.next());
            }
            final Future<Queue<R>> submit = executorService.submit(new ProcessElement<>(block, map));
            futureQueue.add(submit);
        }
    }

    private class ProcessElement<O, S> implements Callable<Queue<S>> {
        Queue<O> queue;
        Function<O, S> map;

        ProcessElement(Queue<O> queue, Function<O, S> map) {
            this.queue=queue;
            this.map=map;
        }

        @Override
        public Queue<S> call() throws Exception {
            ArrayBlockingQueue<S> outputQueue = new ArrayBlockingQueue<>(queue.size());
            queue.stream().map(this.map).forEach(outputQueue::add);
            return outputQueue;
        }
    }
}
