/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class has exactly the same API that SortingCollection, however,
 * sorts and spills the data to disk in a separate ExecutorService. Identify the maximum number of records
 * in memory that is guaranteed not to exceed the number of records that would have the object class SortingCollection.
 *
 * @author Pavel_Silin@epam.com, EPAM Systems, Inc. <www.epam.com>
 */
public class AsyncWriteSortingCollection<T> extends SortingCollection<T> {

	private static final ExecutorService service = Executors.newFixedThreadPool(
			Defaults.SORTING_COLLECTION_THREADS,
			r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
	);

	private final BlockingQueue<T[]> instancePool;
	private CompletableFuture finishFlagFuture;

	@SuppressWarnings("unchecked")
	public AsyncWriteSortingCollection(final Class<T> componentType,
									   final SortingCollection.Codec<T> codec,
									   final Comparator<T> comparator, final int maxRecordsInRam,
									   final File... tmpDir) {
		super(componentType, codec, comparator,
				// calculate available space for one buffer
				maxRecordsInRam / (Defaults.SORTING_COLLECTION_THREADS + 1),
				tmpDir
		);

		if (Defaults.SORTING_COLLECTION_THREADS <= 0) {
			throw new IllegalArgumentException("JVM parameter sort_col_threads can't be <= 0");
		}

		instancePool = new LinkedBlockingQueue<>(Defaults.SORTING_COLLECTION_THREADS);
		for (int i = 0; i < Defaults.SORTING_COLLECTION_THREADS; i++) {
			instancePool.offer((T[]) Array.newInstance(componentType,
                    maxRecordsInRam / (Defaults.SORTING_COLLECTION_THREADS + 1))
            );
		}
		finishFlagFuture = CompletableFuture.completedFuture(null);
	}

	@Override
	public void doneAdding() {
		super.doneAdding();
		if (instancePool != null && !instancePool.isEmpty()) {
			instancePool.clear();
		}
	}

	/**
	 * @see SortingCollection
	 */
	@SuppressWarnings("unused")
	public static <T> AsyncWriteSortingCollection<T> newInstance(
			final Class<T> componentType,
			final SortingCollection.Codec<T> codec,
			final Comparator<T> comparator, final int maxRecordsInRAM,
			final File... tmpDir) {
		return new AsyncWriteSortingCollection<>(componentType, codec,
				comparator, maxRecordsInRAM, tmpDir);

	}

	/**
	 * @see SortingCollection
	 */
	@SuppressWarnings("unused")
	public static <T> AsyncWriteSortingCollection<T> newInstance(
			final Class<T> componentType,
			final SortingCollection.Codec<T> codec,
			final Comparator<T> comparator, final int maxRecordsInRAM,
			final Collection<File> tmpDirs) {
		return new AsyncWriteSortingCollection<>(componentType, codec,
				comparator, maxRecordsInRAM,
				tmpDirs.toArray(new File[tmpDirs.size()]));
	}

	/**
	 * @see SortingCollection
	 */
	@SuppressWarnings("unused")
	public static <T> AsyncWriteSortingCollection<T> newInstance(
			final Class<T> componentType,
			final SortingCollection.Codec<T> codec,
			final Comparator<T> comparator, final int maxRecordsInRAM) {

		final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
		return new AsyncWriteSortingCollection<>(componentType, codec,
				comparator, maxRecordsInRAM, tmpDir);
	}

	/**
	 * This method is called from SortingCollection.add method to perform spill
	 * to disk operation. The method puts collected rumRecords to the new task
	 * and then it will be sorting and spilling to disk in a separate thread.
	 */
	@Override
	protected void performSpillToDisk() {
		try {
			final T[] buffRamRecords = this.ramRecords;
			final int buffNumRecordsInRam = this.numRecordsInRam;
			this.ramRecords = instancePool.take();
			this.numRecordsInRam = 0;

			//run task, and when it's done, put buffer in pool
			CompletableFuture<Void> sortSpillTask = CompletableFuture.supplyAsync(
					() -> {
						Arrays.sort(buffRamRecords, 0, buffNumRecordsInRam, comparator);
						spill(buffRamRecords, buffNumRecordsInRam, codec.clone());
						return buffRamRecords;
					},
					service
			).thenAccept(this::returnRamRecordsToPool);

			finishFlagFuture = CompletableFuture.allOf(finishFlagFuture, sortSpillTask);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SAMException("Failed to spill to disk once the tmp file.", e);
		}
	}

	private void returnRamRecordsToPool(T[] t) {
		try {
			instancePool.put(t);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SAMException(
					"Failed to put in ramRecordPool once the ramRecords array.", e
			);
		}
	}

	// Wait until the end of the work.
	@Override
	protected void finish() {
		finishFlagFuture.join();
	}
}
