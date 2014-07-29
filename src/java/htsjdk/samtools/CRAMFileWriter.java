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
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CRAMFileWriter extends SAMFileWriterImpl {
	private static final int REF_SEQ_INDEX_NOT_INITED = -2;
	private static final int DEFAULT_RECORDS_PER_SLICE = 10000;
	private static final int DEFAULT_SLICES_PER_CONTAINER = 1;
	private static final Version cramVersion = CramVersions.CRAM_v2_1;

	private String fileName;
	private List<CramCompressionRecord> records = new ArrayList<CramCompressionRecord>();
	private ContainerFactory containerFactory;
	protected int recordsPerSlice = DEFAULT_RECORDS_PER_SLICE;
	protected int containerSize = recordsPerSlice
			* DEFAULT_SLICES_PER_CONTAINER;

	private Sam2CramRecordFactory sam2CramRecordFactory;
	private OutputStream os;
	private ReferenceSource source;
	private int refSeqIndex = REF_SEQ_INDEX_NOT_INITED;

	private boolean preserveReadNames = true;
	private SAMFileHeader samFileHeader;

	public CRAMFileWriter(OutputStream os, ReferenceSource source,
			SAMFileHeader samFileHeader, String fileName) {
		if (samFileHeader.getSortOrder() != SortOrder.coordinate)
			throw new RuntimeException(
					"Only coordinate sort order is supported in this version.");

		this.os = os;
		this.source = source;
		this.samFileHeader = samFileHeader;
		this.fileName = fileName;

		if (this.source == null)
			this.source = new ReferenceSource(Defaults.REFERENCE_FASTA);

		containerFactory = new ContainerFactory(samFileHeader, recordsPerSlice,
				preserveReadNames);

		CramHeader cramHeader = new CramHeader(cramVersion.major,
				cramVersion.minor, fileName == null ? "" : fileName,
				samFileHeader);
		try {
			CramIO.writeCramHeader(cramHeader, os);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Decide if the current container should be completed and flushed. The
	 * decision is based on a) number of records and b) if the reference
	 * sequence id has changed.
	 * 
	 * @param nextRecord
	 *            the record to be added into the current or next container
	 * @return true if the current container should be flished and the following
	 *         records should go into a new container; false otherwise.
	 */
	protected boolean shouldFlushContainer(SAMRecord nextRecord) {
		if (records.size() >= containerSize)
			return true;

		if (refSeqIndex != REF_SEQ_INDEX_NOT_INITED && refSeqIndex != nextRecord.getReferenceIndex())
			return true;

		return false;
	}

	/**
	 * Complete the current container and flush it to the output stream.
	 * 
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	protected void flushContainer() throws IllegalArgumentException,
			IllegalAccessException, IOException {
		Container container = containerFactory.buildContainer(records);
		CramIO.writeContainer(container, os);
		records.clear();
	}

	@Override
	protected void writeAlignment(SAMRecord alignment) {
		if (shouldFlushContainer(alignment))
			try {
				flushContainer();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		updateReferenceContext(alignment.getReferenceIndex());

		records.add(sam2CramRecordFactory.createCramRecord(alignment));
	}

	/**
	 * Check if the reference has changed and create a new record factory using
	 * the new reference.
	 * 
	 * @param samRecordReferenceIndex
	 *            index of the new reference sequence
	 */
	private void updateReferenceContext(int samRecordReferenceIndex) {
		if (refSeqIndex == REF_SEQ_INDEX_NOT_INITED) {
			refSeqIndex = samRecordReferenceIndex;
			createSam2CramRecordFactory();
		} else {
			int newRefSeqIndex = samRecordReferenceIndex;
			if (refSeqIndex != newRefSeqIndex) {
				refSeqIndex = newRefSeqIndex;
				createSam2CramRecordFactory();
			}
		}
	}

	/**
	 * Create a new factory for the current reference sequence
	 * {@link #refSeqIndex index}. For unmapped reads the factory will be
	 * initialized with an empty reference.
	 * 
	 */
	private void createSam2CramRecordFactory() {
		byte[] refs;
		if (refSeqIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
			refs = new byte[0];
		else
			refs = source.getReferenceBases(
					samFileHeader.getSequence(refSeqIndex), true);
		sam2CramRecordFactory = new Sam2CramRecordFactory(refs, samFileHeader);
	}

	@Override
	protected void writeHeader(String textHeader) {
		SAMFileHeader header = new SAMFileHeader();
		containerFactory = new ContainerFactory(header, recordsPerSlice,
				preserveReadNames);

		header.setTextHeader(textHeader);
		CramHeader cramHeader = new CramHeader(cramVersion.major,
				cramVersion.minor, fileName, header);
		try {
			CramIO.writeCramHeader(cramHeader, os);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void finish() {
		if (sam2CramRecordFactory != null)
			sam2CramRecordFactory = null;
		try {
			if (!records.isEmpty())
				flushContainer();
			CramIO.issueZeroB_EOF_marker(os);
			os.flush();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected String getFilename() {
		return fileName;
	}
}
