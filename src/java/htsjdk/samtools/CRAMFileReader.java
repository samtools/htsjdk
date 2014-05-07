/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools;

import htsjdk.samtools.BAMFileReader.BAMIteratorFilter;
import htsjdk.samtools.BAMFileReader.FilteringIteratorState;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileReader.QueryInterval;
import htsjdk.samtools.SAMFileReader.ValidationStringency;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeEOFException;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class CRAMFileReader extends SAMFileReader.ReaderImplementation {
	private File file;
	private ReferenceSource referenceSource;
	private CramHeader header;
	private InputStream is;
	private SAMRecordIterator it;
	private BAMIndex mIndex;
	private File mIndexFile;
	private boolean mEnableIndexCaching;
	private File mIndexStream;
	private boolean mEnableIndexMemoryMapping;

	private ValidationStringency validationStringency;

	private static volatile long counter = 1L;

	public CRAMFileReader(File file, InputStream is,
			ReferenceSource referenceSource) {
		this.file = file;
		this.is = is;
		this.referenceSource = referenceSource;
		counter++;
		if (counter % 1000 == 0)
			System.err.println("open cram files: " + counter);

		if (file == null)
			getIterator();
	}

	public CRAMFileReader(File bamFile, File indexFile,
			ReferenceSource referenceSource) {
		this.file = bamFile;
		this.mIndexFile = indexFile;
		this.referenceSource = referenceSource;
		counter++;
		if (counter % 1000 == 0)
			System.err.println("open cram files: " + counter);

		if (file == null)
			getIterator();
	}

	public SAMRecordIterator iterator() {
		return getIterator();
	}

	private void readHeader() throws FileNotFoundException, IOException {
		header = CramIO.readCramHeader(new FileInputStream(file));
	}

	@Override
	void enableFileSource(SAMFileReader reader, boolean enabled) {
		// throw new RuntimeException("Not implemented.");
	}

	@Override
	void enableIndexCaching(boolean enabled) {
		// throw new RuntimeException("Not implemented.");
	}

	@Override
	void enableIndexMemoryMapping(boolean enabled) {
		// throw new RuntimeException("Not implemented.");
	}

	@Override
	void enableCrcChecking(boolean enabled) {
		// throw new RuntimeException("Not implemented.");
	}

	@Override
	void setSAMRecordFactory(SAMRecordFactory factory) {
	}

	@Override
	boolean hasIndex() {
		return mIndex != null || mIndexFile != null || mIndexStream != null;
	}

	@Override
	BAMIndex getIndex() {
		if (!hasIndex())
			throw new SAMException("No index is available for this BAM file.");
		if (mIndex == null) {
			if (mIndexFile != null)
				mIndex = mEnableIndexCaching ? new CachingBAMFileIndex(
						mIndexFile, getFileHeader().getSequenceDictionary(),
						mEnableIndexMemoryMapping) : new DiskBasedBAMFileIndex(
						mIndexFile, getFileHeader().getSequenceDictionary(),
						mEnableIndexMemoryMapping);
			else
				mIndex = mEnableIndexCaching ? new CachingBAMFileIndex(
						mIndexStream, getFileHeader().getSequenceDictionary())
						: new DiskBasedBAMFileIndex(mIndexStream,
								getFileHeader().getSequenceDictionary());
		}
		return mIndex;
	}

	@Override
	SAMFileHeader getFileHeader() {
		try {
			if (header == null)
				readHeader();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return header.samFileHeader;
	}

	@Override
	SAMRecordIterator getIterator() {
		if (it != null && file == null)
			return it;
		try {
			SAMIterator si = null;
			if (file != null)
				si = new SAMIterator(new FileInputStream(file), referenceSource);
			else
				si = new SAMIterator(is, referenceSource);

			si.setValidationStringency(validationStringency);
			header = si.getCramHeader();
			it = si;
			return it;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	CloseableIterator<SAMRecord> getIterator(SAMFileSpan fileSpan) {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	SAMFileSpan getFilePointerSpanningReads() {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public CloseableIterator<SAMRecord> query(String sequence, int start,
			int end, boolean contained) {
		CloseableIterator<SAMRecord> iterator = queryAlignmentStart(sequence,
				start);
		int seqIndex = header.samFileHeader.getSequenceIndex(sequence);
		BAMIteratorFilter filter = new BAMStartingAtIteratorFilter(seqIndex,
				start);
		return new BAMQueryFilteringIterator(iterator, filter);
	}

	/**
	 * Pull SAMRecords from a coordinate-sorted iterator, and filter out any
	 * that do not match the filter.
	 */
	private class BAMQueryFilteringIterator extends AbstractBamIterator {
		/**
		 * The wrapped iterator.
		 */
		protected final CloseableIterator<SAMRecord> wrappedIterator;
		/**
		 * The next record to be returned. Will be null if no such record
		 * exists.
		 */
		protected SAMRecord mNextRecord;
		private final BAMIteratorFilter iteratorFilter;

		public BAMQueryFilteringIterator(
				final CloseableIterator<SAMRecord> iterator,
				final BAMIteratorFilter iteratorFilter) {
			this.wrappedIterator = iterator;
			this.iteratorFilter = iteratorFilter;
			mNextRecord = advance();
		}

		/**
		 * Returns true if a next element exists; false otherwise.
		 */
		public boolean hasNext() {
			assertOpen();
			return mNextRecord != null;
		}

		/**
		 * Gets the next record from the given iterator.
		 * 
		 * @return The next SAM record in the iterator.
		 */
		public SAMRecord next() {
			if (!hasNext())
				throw new NoSuchElementException(
						"BAMQueryFilteringIterator: no next element available");
			final SAMRecord currentRead = mNextRecord;
			mNextRecord = advance();
			return currentRead;
		}

		SAMRecord advance() {
			while (true) {
				// Pull next record from stream
				if (!wrappedIterator.hasNext())
					return null;

				final SAMRecord record = wrappedIterator.next();
				switch (iteratorFilter.compareToFilter(record)) {
				case MATCHES_FILTER:
					return record;
				case STOP_ITERATION:
					return null;
				case CONTINUE_ITERATION:
					break; // keep looping
				default:
					throw new SAMException(
							"Unexpected return from compareToFilter");
				}
			}
		}
	}

	/**
	 * Encapsulates the restriction that only one iterator may be open at a
	 * time.
	 */
	private abstract class AbstractBamIterator implements
			CloseableIterator<SAMRecord> {

		private boolean isClosed = false;

		public void close() {
			if (!isClosed) {
				if (it != null && this != it) {
					throw new IllegalStateException(
							"Attempt to close non-current iterator");
				}
				it = null;
				isClosed = true;
			}
		}

		protected void assertOpen() {
			if (isClosed)
				throw new AssertionError("Iterator has been closed");
		}

		public void remove() {
			throw new UnsupportedOperationException("Not supported: remove");
		}

	}

	private class EmptyBamIterator extends AbstractBamIterator {
		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public SAMRecord next() {
			throw new NoSuchElementException("next called on empty iterator");
		}
	}

	/**
	 * A decorating iterator that filters out records that do not match the
	 * given reference and start position.
	 */
	class BAMStartingAtIteratorFilter implements BAMIteratorFilter {

		private final int mReferenceIndex;
		private final int mRegionStart;

		public BAMStartingAtIteratorFilter(final int referenceIndex,
				final int start) {
			mReferenceIndex = referenceIndex;
			mRegionStart = start;
		}

		/**
		 *
		 * @return MATCHES_FILTER if this record matches the filter;
		 *         CONTINUE_ITERATION if does not match filter but iteration
		 *         should continue; STOP_ITERATION if does not match filter and
		 *         iteration should end.
		 */
		@Override
		public FilteringIteratorState compareToFilter(final SAMRecord record) {
			// If beyond the end of this reference sequence, end iteration
			final int referenceIndex = record.getReferenceIndex();
			if (referenceIndex < 0 || referenceIndex > mReferenceIndex) {
				return FilteringIteratorState.STOP_ITERATION;
			} else if (referenceIndex < mReferenceIndex) {
				// If before this reference sequence, continue
				return FilteringIteratorState.CONTINUE_ITERATION;
			}
			final int alignmentStart = record.getAlignmentStart();
			if (alignmentStart > mRegionStart) {
				// If scanned beyond target region, end iteration
				return FilteringIteratorState.STOP_ITERATION;
			} else if (alignmentStart == mRegionStart) {
				return FilteringIteratorState.MATCHES_FILTER;
			} else {
				return FilteringIteratorState.CONTINUE_ITERATION;
			}
		}

	}

	private static SAMRecordIterator emptyIterator = new SAMRecordIterator() {

		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public SAMRecord next() {
			throw new RuntimeException("No records.");
		}

		@Override
		public void remove() {
			throw new RuntimeException("Remove not supported.");
		}

		@Override
		public void close() {
		}

		@Override
		public SAMRecordIterator assertSorted(SortOrder sortOrder) {
			// TODO Auto-generated method stub
			return null;
		}
	};

	@Override
	public CloseableIterator<SAMRecord> queryAlignmentStart(String sequence,
			int start) {
		long[] filePointers = null;

		// Hit the index to determine the chunk boundaries for the required
		// data.
		final SAMFileHeader fileHeader = getFileHeader();
		final int referenceIndex = fileHeader.getSequenceIndex(sequence);
		if (referenceIndex != -1) {
			final BAMIndex fileIndex = getIndex();
			final BAMFileSpan fileSpan = fileIndex.getSpanOverlapping(
					referenceIndex, start, -1);
			filePointers = fileSpan != null ? fileSpan.toCoordinateArray()
					: null;
		}

		if (filePointers == null || filePointers.length == 0)
			return emptyIterator;

		SeekableStream s = null;
		if (file != null) {
			try {
				s = new SeekableFileStream(file);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		} else if (is instanceof SeekableStream)
			s = (SeekableStream) is;

		SAMIterator si = null;
		try {
			s.seek(0);
			si = new SAMIterator(s, referenceSource);
			si.setValidationStringency(validationStringency);
			it = si;
		} catch (IOException e) {
			throw new RuntimeEOFException(e);
		}

		Container c = null;
		for (int i = 0; i < filePointers.length; i += 2) {
			long containerOffset = filePointers[i] >>> 16;
			int sliceIndex = (int) ((filePointers[i] << 48) >>> 48);
			try {
				s.seek(containerOffset);
				// the following is not optimal because this is container-level
				// access, not slice:

				// CountingInputStream cis = new CountingInputStream(s) ;
				c = CramIO.readContainerHeader(s);
				// long headerSize = cis.getCount() ;
				// int sliceOffset = c.landmarks[sliceIndex] ;
				if (c.alignmentStart + c.alignmentSpan > start) {
					s.seek(containerOffset);
					// s.seek(containerOffset + headerSize + sliceOffset);
					return si;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		it = emptyIterator;
		return it;
	}

	@Override
	public CloseableIterator<SAMRecord> queryUnmapped() {
		final long startOfLastLinearBin = getIndex().getStartOfLastLinearBin();

		SeekableStream s = null;
		if (file != null) {
			try {
				s = new SeekableFileStream(file);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		} else if (is instanceof SeekableStream)
			s = (SeekableStream) is;

		SAMIterator si = null;
		try {
			s.seek(0);
			si = new SAMIterator(s, referenceSource);
			si.setValidationStringency(validationStringency);
			s.seek(startOfLastLinearBin);
			it = si;
		} catch (IOException e) {
			throw new RuntimeEOFException(e);
		}

		return it;
	}

	@Override
	void close() {
		if (it != null)
			it.close();
		if (is != null)
			try {
				is.close();
			} catch (IOException e) {
			}

		if (mIndex != null)
			mIndex.close();
	}

	@Override
	void setValidationStringency(ValidationStringency validationStringency) {
		this.validationStringency = validationStringency;
	}

	@Override
	ValidationStringency getValidationStringency() {
		return validationStringency;
	}

	public static boolean isCRAMFile(File file) throws IOException {
		final int buffSize = CramHeader.magick.length;
		FileInputStream fis = new FileInputStream(file);
		DataInputStream dis = new DataInputStream(fis);
		final byte[] buffer = new byte[buffSize];
		dis.readFully(buffer);
		dis.close();

		return Arrays.equals(buffer, CramHeader.magick);
	}

	@Override
	CloseableIterator<SAMRecord> query(QueryInterval[] intervals,
			boolean contained) {
		if (is == null) {
			throw new IllegalStateException("File reader is closed");
		}
		if (it != null) {
			throw new IllegalStateException("Iteration in progress");
		}
		if (mIndex == null && mIndexFile == null) {
			throw new UnsupportedOperationException(
					"Cannot query stream-based BAM file");
		}
		throw new SAMException("Multiple interval queries not implemented.");
	}
}
