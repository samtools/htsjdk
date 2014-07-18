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
package htsjdk.samtools.cram.structure;

public class Container {
	// container header as defined in the specs:
	/**
	 * Byte size of the content excluding header.
	 */
	public int containerByteSize;
	public int sequenceId = -1;
	public int alignmentStart = -1;
	public int alignmentSpan = -1;
	public int nofRecords = -1;
	public long globalRecordCounter = -1;

	public long bases = 0;
	public int blockCount = -1;
	public int[] landmarks;

	/**
	 * Container data
	 */
	public Block[] blocks;

	public CompressionHeader h;

	// slices found in the container:
	public Slice[] slices;

	// for performance measurement:
	public long buildHeaderTime;
	public long buildSlicesTime;
	public long writeTime;
	public long parseTime;
	public long readTime;

	// for indexing:
	/**
	 * Container start in the stream.
	 */
	public long offset;

	@Override
	public String toString() {
		return String.format("seqid=%d, astart=%d, aspan=%d, records=%d, slices=%d, blocks=%d.", sequenceId,
				alignmentStart, alignmentSpan, nofRecords, slices == null ? -1 : slices.length, blockCount);
	}

	public boolean isEOF() {
		return containerByteSize == 11 && sequenceId == -1 && alignmentStart == 4542278 && blockCount == 1
				&& nofRecords == 0 && (slices == null || slices.length == 0);
	}
}
