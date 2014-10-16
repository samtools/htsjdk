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

import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.Cram2SamRecordFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.lossy.PreservationPolicy;
import htsjdk.samtools.cram.lossy.QualityScorePreservation;
import htsjdk.samtools.cram.lossy.QualityScoreTreatment;
import htsjdk.samtools.cram.lossy.ReadCategory;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.StringLineReader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CRAMFileWriter extends SAMFileWriterImpl {
	private static final int REF_SEQ_INDEX_NOT_INITED = -2;
	private static final int DEFAULT_RECORDS_PER_SLICE = 10000;
	private static final int DEFAULT_SLICES_PER_CONTAINER = 1;
	private static final Version cramVersion = CramVersions.CRAM_v2_1;

	private String fileName;
	private List<SAMRecord> samRecords = new ArrayList<SAMRecord>();
	private ContainerFactory containerFactory;
	protected int recordsPerSlice = DEFAULT_RECORDS_PER_SLICE;
	protected int containerSize = recordsPerSlice
			* DEFAULT_SLICES_PER_CONTAINER;

	private Sam2CramRecordFactory sam2CramRecordFactory;
	private OutputStream os;
	private ReferenceSource source;
	private int refSeqIndex = REF_SEQ_INDEX_NOT_INITED;

	private static Log log = Log.getInstance(CRAMFileWriter.class);

	private SAMFileHeader samFileHeader;
	private boolean preserveReadNames = true;
	private QualityScorePreservation preservation = null;
	private boolean captureAllTags = true;
	private Set<String> captureTags = new TreeSet<String>();
	private Set<String> ignoreTags = new TreeSet<String>();

	public CRAMFileWriter(OutputStream os, ReferenceSource source,
			SAMFileHeader samFileHeader, String fileName) {
		this.os = os;
		this.source = source;
		this.samFileHeader = samFileHeader;
		this.fileName = fileName;
		setSortOrder(samFileHeader.getSortOrder(), true);
		setHeader(samFileHeader);

		if (this.source == null)
			this.source = new ReferenceSource(Defaults.REFERENCE_FASTA);

		containerFactory = new ContainerFactory(samFileHeader, recordsPerSlice);
	}

	/**
	 * Decide if the current container should be completed and flushed. The
	 * decision is based on a) number of records and b) if the reference
	 * sequence id has changed.
	 * 
	 * @param nextRecord
	 *            the record to be added into the current or next container
	 * @return true if the current container should be flushed and the following
	 *         records should go into a new container; false otherwise.
	 */
	protected boolean shouldFlushContainer(SAMRecord nextRecord) {
		if (samRecords.size() >= containerSize)
			return true;

		if (refSeqIndex != REF_SEQ_INDEX_NOT_INITED
				&& refSeqIndex != nextRecord.getReferenceIndex())
			return true;

		return false;
	}

	private static void updateTracks(List<SAMRecord> samRecords,
			ReferenceTracks tracks) {
		for (SAMRecord samRecord : samRecords) {
			if (samRecord.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
				int refPos = samRecord.getAlignmentStart();
				int readPos = 0;
				for (CigarElement ce : samRecord.getCigar().getCigarElements()) {
					if (ce.getOperator().consumesReferenceBases()) {
						for (int i = 0; i < ce.getLength(); i++)
							tracks.addCoverage(refPos + i, 1);
					}
					switch (ce.getOperator()) {
					case M:
					case X:
					case EQ:
						for (int i = readPos; i < ce.getLength(); i++) {
							byte readBase = samRecord.getReadBases()[readPos
									+ i];
							byte refBase = tracks.baseAt(refPos + i);
							if (readBase != refBase)
								tracks.addMismatches(refPos + i, 1);
						}
						break;

					default:
						break;
					}

					readPos += ce.getOperator().consumesReadBases() ? ce
							.getLength() : 0;
					refPos += ce.getOperator().consumesReferenceBases() ? ce
							.getLength() : 0;
				}
			}
		}
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

		byte[] refs;
		if (refSeqIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
			refs = new byte[0];
		else
			refs = source.getReferenceBases(
					samFileHeader.getSequence(refSeqIndex), true);

		int start = SAMRecord.NO_ALIGNMENT_START;
		int stop = SAMRecord.NO_ALIGNMENT_START;
		for (SAMRecord r : samRecords) {
			if (r.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START)
				continue;

			if (start == SAMRecord.NO_ALIGNMENT_START)
				start = r.getAlignmentStart();

			start = Math.min(r.getAlignmentStart(), start);
			stop = Math.max(r.getAlignmentEnd(), stop);
		}

		ReferenceTracks tracks = null;
		if (preservation != null && preservation.areReferenceTracksRequired()) {
			if (tracks == null || tracks.getSequenceId() != refSeqIndex)
				tracks = new ReferenceTracks(refSeqIndex, refs);
			tracks.ensureRange(start, stop - start + 1);
			updateTracks(samRecords, tracks);
		}

		List<CramCompressionRecord> cramRecords = new ArrayList<CramCompressionRecord>(
				samRecords.size());

		sam2CramRecordFactory = new Sam2CramRecordFactory(refSeqIndex, refs,
				samFileHeader);
		sam2CramRecordFactory.preserveReadNames = preserveReadNames;
		sam2CramRecordFactory.captureAllTags = captureAllTags;
		sam2CramRecordFactory.captureTags.addAll(captureTags);
		sam2CramRecordFactory.ignoreTags.addAll(ignoreTags);
		containerFactory.setPreserveReadNames(preserveReadNames);

		int index = 0;
		int prevAlStart = start;
		for (SAMRecord samRecord : samRecords) {
			CramCompressionRecord cramRecord = sam2CramRecordFactory
					.createCramRecord(samRecord);
			cramRecord.index = ++index;
			cramRecord.alignmentDelta = samRecord.getAlignmentStart()
					- prevAlStart;
			cramRecord.alignmentStart = samRecord.getAlignmentStart();
			prevAlStart = samRecord.getAlignmentStart();

			cramRecords.add(cramRecord);

			if (preservation != null)
				preservation.addQualityScores(samRecord, cramRecord, tracks);
			else
				cramRecord.setForcePreserveQualityScores(true);
		}

		// samRecords.clear();

		if (sam2CramRecordFactory.getBaseCount() < 3 * sam2CramRecordFactory
				.getFeatureCount())
			log.warn("Abnormally high number of mismatches, possibly wrong reference.");

		// mating:
		Map<String, CramCompressionRecord> primaryMateMap = new TreeMap<String, CramCompressionRecord>();
		Map<String, CramCompressionRecord> secondaryMateMap = new TreeMap<String, CramCompressionRecord>();
		for (CramCompressionRecord r : cramRecords) {
			if (!r.isMultiFragment()) {
				r.setDetached(true);

				r.setHasMateDownStream(false);
				r.recordsToNextFragment = -1;
				r.next = null;
				r.previous = null;
			} else {
				String name = r.readName;
				Map<String, CramCompressionRecord> mateMap = r
						.isSecondaryAlignment() ? secondaryMateMap
						: primaryMateMap;
				CramCompressionRecord mate = mateMap.get(name);
				if (mate == null) {
					mateMap.put(name, r);
				} else {
					mate.recordsToNextFragment = r.index - mate.index - 1;
					mate.next = r;
					r.previous = mate;
					r.previous.setHasMateDownStream(true);
					r.setHasMateDownStream(false);
					r.setDetached(false);
					r.previous.setDetached(false);

					mateMap.remove(name);
				}
			}
		}

		for (CramCompressionRecord r : primaryMateMap.values()) {
			r.setDetached(true);

			r.setHasMateDownStream(false);
			r.recordsToNextFragment = -1;
			r.next = null;
			r.previous = null;
		}

		for (CramCompressionRecord r : secondaryMateMap.values()) {
			r.setDetached(true);

			r.setHasMateDownStream(false);
			r.recordsToNextFragment = -1;
			r.next = null;
			r.previous = null;
		}

		Cram2SamRecordFactory f = new Cram2SamRecordFactory(samFileHeader);
		for (int i = 0; i < samRecords.size(); i++) {
			String s1 = samRecords.get(i).getSAMString();
			SAMRecord r = f.create(cramRecords.get(i));
			String s2 = r.getSAMString();
			assert (s1.equals(s2));
		}

		Container container = containerFactory.buildContainer(cramRecords);
		CramIO.writeContainer(container, os);
		samRecords.clear();
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

		samRecords.add(alignment);
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
		} else {
			int newRefSeqIndex = samRecordReferenceIndex;
			if (refSeqIndex != newRefSeqIndex) {
				refSeqIndex = newRefSeqIndex;
			}
		}
	}

	@Override
	protected void writeHeader(String textHeader) {
		// TODO: header must be written exactly once per writer life cycle.
		SAMFileHeader header = new SAMTextHeaderCodec().decode(
				new StringLineReader(textHeader), (fileName != null ? fileName
						: null));

		containerFactory = new ContainerFactory(header, recordsPerSlice);

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
		try {
			if (!samRecords.isEmpty())
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

	public boolean isPreserveReadNames() {
		return preserveReadNames;
	}

	public void setPreserveReadNames(boolean preserveReadNames) {
		this.preserveReadNames = preserveReadNames;
	}

	public List<PreservationPolicy> getPreservationPolicies() {
		if (preservation == null) {
			// set up greedy policy by default:
			preservation = new QualityScorePreservation("*8");
		}
		return preservation.getPreservationPolicies();
	}

	public boolean isCaptureAllTags() {
		return captureAllTags;
	}

	public void setCaptureAllTags(boolean captureAllTags) {
		this.captureAllTags = captureAllTags;
	}

	public Set<String> getCaptureTags() {
		return captureTags;
	}

	public void setCaptureTags(Set<String> captureTags) {
		this.captureTags = captureTags;
	}

	public Set<String> getIgnoreTags() {
		return ignoreTags;
	}

	public void setIgnoreTags(Set<String> ignoreTags) {
		this.ignoreTags = ignoreTags;
	}
}
