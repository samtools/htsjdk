package htsjdk.samtools.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsyncChunkedOperationIterator<T,R> implements CloseableIterator<R> {

    private final CloseableIterator<T> input;
    private final Function<T,R> f;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    private final int BUFFER_SIZE = 1000;

    private final Queue<Future<Queue<R>>> buffers = new ArrayDeque<>();


    private Queue<R> current;

    public AsyncChunkedOperationIterator(final CloseableIterator<T> input, final Function<T, R> f) {
        this.input = input;
        this.f = f;
    }

    @Override
    public boolean hasNext() {
        return (input.hasNext() || !buffers.isEmpty() || !current.isEmpty());
    }

    @Override
    public R next() {
        if(!hasNext()){
            throw new RuntimeException();
        }
        if(current != null && !current.isEmpty()){
            return current.remove();
        } else if (!buffers.isEmpty()){
            try {
                if(! buffers.peek().isDone()){
                    loadBuffers();
                }
                current = buffers.remove().get();
                return next();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else if (input.hasNext()){
            loadBuffers();
            return next();
        } else {
            throw new RuntimeException();
        }
    }

    private void loadBuffers(){
        while( buffers.size() < 10 ) {
            if(buffers.peek() != null && buffers.peek().isDone()) {
                break;
            }
            final List<T> buff = new ArrayList<>(BUFFER_SIZE);
            int added = 0;
            while (input.hasNext() && added < BUFFER_SIZE) {
                buff.add(input.next());
                added++;
            }
            if(!buff.isEmpty()) {
                final FutureTask<Queue<R>> bufferFuture = new FutureTask<>(() -> {
                    final List<R> updated = buff.stream().map(f).collect(Collectors.toList());
                    return new ArrayDeque<R>(updated);
                });
                pool.execute(bufferFuture);
                buffers.add(bufferFuture);
            }
        }
    }

    @Override
    public void close() {
        input.close();
    }
}
