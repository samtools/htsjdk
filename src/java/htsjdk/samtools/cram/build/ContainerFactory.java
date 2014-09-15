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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.writer.DataWriterFactory;
import htsjdk.samtools.cram.encoding.writer.Writer;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.Block;
import htsjdk.samtools.cram.structure.BlockCompressionMethod;
import htsjdk.samtools.cram.structure.BlockContentType;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerFactory {
	SAMFileHeader samFileHeader;
	int recordsPerSlice = 10000;
	boolean preserveReadNames = true;
	long globalRecordCounter = 0;
	boolean AP_delta = true;

	public ContainerFactory(SAMFileHeader samFileHeader, int recordsPerSlice) {
		this.samFileHeader = samFileHeader;
		this.recordsPerSlice = recordsPerSlice;
	}

	public Container buildContainer(List<CramCompressionRecord> records)
			throws IllegalArgumentException, IllegalAccessException,
			IOException {
		return buildContainer(records, null);
	}

	public Container buildContainer(List<CramCompressionRecord> records,
			SubstitutionMatrix substitutionMatrix)
			throws IllegalArgumentException, IllegalAccessException,
			IOException {
		// get stats, create compression header and slices
		long time1 = System.nanoTime();
		CompressionHeader h = new CompressionHeaderFactory().build(records,
				substitutionMatrix);
		h.AP_seriesDelta = AP_delta;
		long time2 = System.nanoTime();

		h.readNamesIncluded = preserveReadNames;
		h.AP_seriesDelta = true;

		List<Slice> slices = new ArrayList<Slice>();

		Container c = new Container();
		c.h = h;
		c.nofRecords = records.size();
		c.globalRecordCounter = globalRecordCounter;
		c.bases = 0;
		c.blockCount = 0;

		long time3 = System.nanoTime();
		long lastGlobalRecordCounter = c.globalRecordCounter;
		for (int i = 0; i < records.size(); i += recordsPerSlice) {
			List<CramCompressionRecord> sliceRecords = records.subList(i,
					Math.min(records.size(), i + recordsPerSlice));
			Slice slice = buildSlice(sliceRecords, h, samFileHeader);
			slice.globalRecordCounter = lastGlobalRecordCounter;
			lastGlobalRecordCounter += slice.nofRecords;
			c.bases += slice.bases;
			slices.add(slice);

			// assuming one sequence per container max:
			if (c.sequenceId == -1 && slice.sequenceId != -1)
				c.sequenceId = slice.sequenceId;
		}

		long time4 = System.nanoTime();

		c.slices = (Slice[]) slices.toArray(new Slice[slices.size()]);
		calculateAlignmentBoundaries(c);

		c.buildHeaderTime = time2 - time1;
		c.buildSlicesTime = time4 - time3;

		globalRecordCounter += records.size();
		return c;
	}

	private static void calculateAlignmentBoundaries(Container c) {
		int start = Integer.MAX_VALUE;
		int end = Integer.MIN_VALUE;
		for (Slice s : c.slices) {
			if (s.sequenceId != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
				start = Math.min(start, s.alignmentStart);
				end = Math.max(end, s.alignmentStart + s.alignmentSpan);
			}
		}

		if (start < Integer.MAX_VALUE) {
			c.alignmentStart = start;
			c.alignmentSpan = end - start;
		}
	}

	private static Slice buildSlice(List<CramCompressionRecord> records,
			CompressionHeader h, SAMFileHeader fileHeader)
			throws IllegalArgumentException, IllegalAccessException,
			IOException {
		Map<Integer, ExposedByteArrayOutputStream> map = new HashMap<Integer, ExposedByteArrayOutputStream>();
		for (int id : h.externalIds) {
			map.put(id, new ExposedByteArrayOutputStream());
		}

		DataWriterFactory f = new DataWriterFactory();
		ExposedByteArrayOutputStream bitBAOS = new ExposedByteArrayOutputStream();
		DefaultBitOutputStream bos = new DefaultBitOutputStream(bitBAOS);

		Slice slice = new Slice();
		slice.nofRecords = records.size();

		{

			// @formatter:off
			/*
			 * 1) Count slice bases. 
			 * 2) Decide if the slice is single ref, unmapped or multiref. 
			 * 3) Detect alignment boundaries for the slice if not multiref.
			 */
			// @formatter:on
			slice.sequenceId = Slice.UNMAPPED_OR_NOREF;
			int minAlStart = Integer.MAX_VALUE;
			int maxAlEnd = SAMRecord.NO_ALIGNMENT_START;
			for (CramCompressionRecord r : records) {
				slice.bases += r.readLength;

				if (slice.sequenceId != Slice.MUTLIREF
						&& r.alignmentStart != SAMRecord.NO_ALIGNMENT_START
						&& r.sequenceId != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
					switch (slice.sequenceId) {
					case Slice.UNMAPPED_OR_NOREF:
						slice.sequenceId = r.sequenceId;
						break;
					case Slice.MUTLIREF:
						break;

					default:
						if (slice.sequenceId != r.sequenceId)
							slice.sequenceId = Slice.UNMAPPED_OR_NOREF;
						break;
					}

					minAlStart = Math.min(r.alignmentStart, minAlStart);
					maxAlEnd = Math.max(r.getAlignmentEnd(), maxAlEnd);
				}
			}

			/*
			 * Set the slice boundaries if the slice have records mapped to a
			 * single ref.
			 */
			if (slice.sequenceId == Slice.MUTLIREF
					|| minAlStart == Integer.MAX_VALUE) {
				slice.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
				slice.alignmentSpan = 0;
			} else {
				slice.alignmentStart = minAlStart;
				slice.alignmentSpan = maxAlEnd - minAlStart + 1;
			}
		}

		Writer writer = f.buildWriter(bos, map, h, slice.sequenceId);
		int prevAlStart = slice.alignmentStart;
		for (CramCompressionRecord r : records) {
			r.alignmentDelta = r.alignmentStart - prevAlStart;
			prevAlStart = r.alignmentStart;
			writer.write(r);
		}

		slice.contentType = slice.alignmentSpan > -1 ? BlockContentType.MAPPED_SLICE
				: BlockContentType.RESERVED;

		bos.close();
		slice.coreBlock = new Block();
		slice.coreBlock.method = BlockCompressionMethod.RAW;
		slice.coreBlock.setRawContent(bitBAOS.toByteArray());
		slice.coreBlock.contentType = BlockContentType.CORE;

		slice.external = new HashMap<Integer, Block>();
		for (Integer i : map.keySet()) {
			ExposedByteArrayOutputStream os = map.get(i);

			Block externalBlock = new Block();
			externalBlock.contentType = BlockContentType.EXTERNAL;
			externalBlock.method = BlockCompressionMethod.GZIP;
			externalBlock.contentId = i;

			externalBlock.setRawContent(os.toByteArray());
			slice.external.put(i, externalBlock);
		}

		return slice;
	}

	public boolean isPreserveReadNames() {
		return preserveReadNames;
	}

	public void setPreserveReadNames(boolean preserveReadNames) {
		this.preserveReadNames = preserveReadNames;
	}
}
