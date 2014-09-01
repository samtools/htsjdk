/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Log.LogLevel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CramFileWriterTest {

	@BeforeClass
	public void initClass() {
		Log.setGlobalLogLevel(LogLevel.ERROR);
	}

	@Test(description = "Test for lossy CRAM compression invariants.")
	public void lossy_CRAM_invariants() throws Exception {
		final SAMFileHeader header = new SAMFileHeader();
		header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
		header.addSequence(new SAMSequenceRecord("chr1", 123));
		SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord("1");
		header.addReadGroup(readGroupRecord);

		byte[] refBases = new byte[1024 * 1024];
		Arrays.fill(refBases, (byte) 'A');
		InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
		rsf.add("chr1", refBases);
		ReferenceSource source = new ReferenceSource(rsf);

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		CRAMFileWriter writer = new CRAMFileWriter(os, source, header, null);
		List<SAMRecord> samRecords = createRecords(1000,
				readGroupRecord.getId());
		for (SAMRecord record : samRecords) {
			writer.writeAlignment(record);
		}
		writer.finish();
		writer.close();

		CRAMFileReader cReader = new CRAMFileReader(null,
				new ByteArrayInputStream(os.toByteArray()),
				new ReferenceSource(rsf));
		SAMRecordIterator iterator2 = cReader.iterator();
		int index = 0;
		while (iterator2.hasNext()) {
			SAMRecord r1 = iterator2.next();
			SAMRecord r2 = samRecords.get(index++);

			Assert.assertEquals(r1.getReadName(), r2.getReadName());
			Assert.assertEquals(r1.getFlags(), r2.getFlags());
			Assert.assertEquals(r1.getAlignmentStart(), r2.getAlignmentStart());
			Assert.assertEquals(r1.getAlignmentEnd(), r2.getAlignmentEnd());
			Assert.assertEquals(r1.getReferenceName(), r2.getReferenceName());
			Assert.assertEquals(r1.getMateAlignmentStart(),
					r2.getMateAlignmentStart());
			Assert.assertEquals(r1.getMateReferenceName(),
					r2.getMateReferenceName());
			Assert.assertEquals(r1.getReadBases(), r2.getReadBases());
			Assert.assertEquals(r1.getBaseQualities(), r2.getBaseQualities());
		}
		cReader.close();
	}

	private List<SAMRecord> createRecords(int count, String rg) {
		List<SAMRecord> list = new ArrayList<SAMRecord>(count);
		final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
		if (null == builder.getHeader().getReadGroup(rg))
			builder.setReadGroup(new SAMReadGroupRecord(rg));

		int posInRef = 1;
		for (int i = 0; i < count / 2; i++)
			builder.addPair(Integer.toString(i), 0, posInRef += 1,
					posInRef += 3);
		list.addAll(builder.getRecords());

		Collections.sort(list, new SAMRecordCoordinateComparator());

		return list;
	};
}
