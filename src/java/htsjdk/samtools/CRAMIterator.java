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

import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.cram.build.ContainerParser;
import htsjdk.samtools.cram.build.Cram2BamRecordFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.common.Utils;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.CramRecord;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeEOFException;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CRAMIterator implements SAMRecordIterator {
	private static Log log = Log.getInstance(CRAMIterator.class);
	private CountingInputStream is;
	private CramHeader cramHeader;
	private ArrayList<SAMRecord> records;
	private int recordCounter = 0;
	private SAMRecord nextRecord = null;
	private boolean restoreNMTag = true;
	private boolean restoreMDTag = false;
	private CramNormalizer normalizer;
	private byte[] refs;
	private int prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
	private Container container;
	private long containerOffset = 0;
	private SamReader mReader;

	private ContainerParser parser;
	private ReferenceSource referenceSource;

	private ValidationStringency validationStringency = ValidationStringency.SILENT;

	public ValidationStringency getValidationStringency() {
		return validationStringency;
	}

	public void setValidationStringency(
			ValidationStringency validationStringency) {
		this.validationStringency = validationStringency;
	}

	private long samRecordIndex;
	private ArrayList<CramRecord> cramRecords;

	public CRAMIterator(InputStream is, ReferenceSource referenceSource)
			throws IOException {
		this.is = new CountingInputStream(is);
		this.referenceSource = referenceSource;
		cramHeader = CramIO.readCramHeader(this.is);
		records = new ArrayList<SAMRecord>(10000);
		normalizer = new CramNormalizer(cramHeader.samFileHeader,
				referenceSource);
		parser = new ContainerParser(cramHeader.samFileHeader);
	}

	public CramHeader getCramHeader() {
		return cramHeader;
	}

	private void nextContainer() throws IOException, IllegalArgumentException,
			IllegalAccessException {
		recordCounter = 0;

		containerOffset = is.getCount();
		container = CramIO.readContainer(is);
		if (container == null || container.isEOF())
			return;

		if (records == null)
			records = new ArrayList<SAMRecord>(container.nofRecords);
		else
			records.clear();
		if (cramRecords == null)
			cramRecords = new ArrayList<CramRecord>(container.nofRecords);
		else
			cramRecords.clear();

		try {
			parser.getRecords(container, cramRecords);
		} catch (EOFException e) {
			throw e;
		}

		if (container.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
			refs = new byte[] {};
		} else if (container.sequenceId == -2) {
			refs = null;
			prevSeqId = -2;
		} else if (prevSeqId < 0 || prevSeqId != container.sequenceId) {
			SAMSequenceRecord sequence = cramHeader.samFileHeader
					.getSequence(container.sequenceId);
			refs = referenceSource.getReferenceBases(sequence, true);
			prevSeqId = container.sequenceId;
		}

		long time1 = System.nanoTime();

		normalizer.normalize(cramRecords, true, refs, container.alignmentStart,
				container.h.substitutionMatrix, container.h.AP_seriesDelta);
		long time2 = System.nanoTime();

		Cram2BamRecordFactory c2sFactory = new Cram2BamRecordFactory(
				cramHeader.samFileHeader);

		long c2sTime = 0;

		for (CramRecord r : cramRecords) {
			long time = System.nanoTime();
			SAMRecord s = c2sFactory.create(r);
			c2sTime += System.nanoTime() - time;
			if (!r.isSegmentUnmapped()) {
				SAMSequenceRecord sequence = cramHeader.samFileHeader
						.getSequence(r.sequenceId);
				refs = referenceSource.getReferenceBases(sequence, true);
				Utils.calculateMdAndNmTags(s, refs, restoreMDTag, restoreNMTag);
			}

			s.setValidationStringency(validationStringency);

			if (validationStringency != ValidationStringency.SILENT) {
				final List<SAMValidationError> validationErrors = s.isValid();
				SAMUtils.processValidationErrors(validationErrors,
						samRecordIndex, validationStringency);
			}

			if (mReader != null) {
				final long chunkStart = (containerOffset << 16) | r.sliceIndex;
				final long chunkEnd = ((containerOffset << 16) | r.sliceIndex) + 1;
				nextRecord.setFileSource(new SAMFileSource(mReader,
						new BAMFileSpan(new Chunk(chunkStart, chunkEnd))));
			}

			records.add(s);
		}
		cramRecords.clear();

		log.info(String.format(
				"CONTAINER READ: io %dms, parse %dms, norm %dms, convert %dms",
				container.readTime / 1000000, container.parseTime / 1000000,
				c2sTime / 1000000, (time2 - time1) / 1000000));
	}

	@Override
	public boolean hasNext() {
		if (recordCounter >= records.size()) {
			try {
				nextContainer();
				if (records.isEmpty())
					return false;
			} catch (Exception e) {
				throw new RuntimeEOFException(e);
			}
		}

		nextRecord = records.get(recordCounter++);
		return true;
	}

	@Override
	public SAMRecord next() {
		return nextRecord;
	}

	@Override
	public void remove() {
		throw new RuntimeException("Removal of records not implemented.");
	}

	@Override
	public void close() {
		records.clear();
		try {
			is.close();
		} catch (IOException e) {
		}
	}

	public static class CramFileIterable implements Iterable<SAMRecord> {
		private ReferenceSource referenceSource;
		private File cramFile;
		private ValidationStringency validationStringency;

		public CramFileIterable(File cramFile, ReferenceSource referenceSource,
				ValidationStringency validationStringency) {
			this.referenceSource = referenceSource;
			this.cramFile = cramFile;
			this.validationStringency = validationStringency;

		}

		public CramFileIterable(File cramFile, ReferenceSource referenceSource) {
			this(cramFile, referenceSource,
					ValidationStringency.DEFAULT_STRINGENCY);
		}

		@Override
		public Iterator<SAMRecord> iterator() {
			try {
				FileInputStream fis = new FileInputStream(cramFile);
				BufferedInputStream bis = new BufferedInputStream(fis);
				CRAMIterator iterator = new CRAMIterator(bis, referenceSource);
				iterator.setValidationStringency(validationStringency);
				return iterator;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

	}

	@Override
	public SAMRecordIterator assertSorted(SortOrder sortOrder) {
		throw new RuntimeException("Not implemented.");
	}

	public SamReader getFileSource() {
		return mReader;
	}

	public void setFileSource(SamReader mReader) {
		this.mReader = mReader;
	}

}
