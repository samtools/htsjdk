/*
 * The MIT License
 *
 * Copyright (c) 2016 Daniel Gomez-Sanchez
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
package htsjdk.samtools.fastq;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.SAMTag;
import org.testng.Assert;
import org.testng.annotations.Test;

/** @author Daniel Gomez-Sanchez (magicDGS) */
public class FastqEncoderTest extends HtsjdkTest {

  @Test
  public void testAsFastqRecord() throws Exception {
    final SAMRecord record =
        new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "10M", null, 2);
    record.setReadPairedFlag(true);
    // test first of pair encoding
    record.setFirstOfPairFlag(true);
    testRecord(
        record.getReadName() + FastqConstants.FIRST_OF_PAIR,
        FastqEncoder.asFastqRecord(record),
        record);
    record.setFirstOfPairFlag(false);
    record.setSecondOfPairFlag(true);
    testRecord(
        record.getReadName() + FastqConstants.SECOND_OF_PAIR,
        FastqEncoder.asFastqRecord(record),
        record);
    record.setSecondOfPairFlag(false);
    testRecord(record.getReadName(), FastqEncoder.asFastqRecord(record), record);
    record.setAttribute(SAMTag.CO.name(), "Comment in SAM tag");
    testRecord(record.getReadName(), FastqEncoder.asFastqRecord(record), record);
  }

  private void testRecord(
      final String expectedReadName, final FastqRecord fastqRecord, final SAMRecord samRecord) {
    Assert.assertEquals(fastqRecord.getReadName(), expectedReadName);
    Assert.assertEquals(fastqRecord.getBaseQualities(), samRecord.getBaseQualities());
    Assert.assertEquals(fastqRecord.getReadBases(), samRecord.getReadBases());
    Assert.assertEquals(
        fastqRecord.getBaseQualityHeader(), samRecord.getStringAttribute(SAMTag.CO.name()));
  }

  @Test
  public void testAsSAMRecord() throws Exception {
    // create a random record
    final SAMRecord samRecord =
        new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "10M", null, 2);
    FastqRecord fastqRecord =
        new FastqRecord(
            samRecord.getReadName(), samRecord.getReadBases(), "", samRecord.getBaseQualities());
    testConvertedSAMRecord(FastqEncoder.asSAMRecord(fastqRecord, samRecord.getHeader()), samRecord);
    fastqRecord =
        new FastqRecord(
            samRecord.getReadName() + FastqConstants.FIRST_OF_PAIR,
            samRecord.getReadBases(),
            "",
            samRecord.getBaseQualities());
    testConvertedSAMRecord(FastqEncoder.asSAMRecord(fastqRecord, samRecord.getHeader()), samRecord);
    fastqRecord =
        new FastqRecord(
            samRecord.getReadName() + FastqConstants.SECOND_OF_PAIR,
            samRecord.getReadBases(),
            "",
            samRecord.getBaseQualities());
    testConvertedSAMRecord(FastqEncoder.asSAMRecord(fastqRecord, samRecord.getHeader()), samRecord);
    fastqRecord =
        new FastqRecord(
            samRecord.getReadName() + FastqConstants.SECOND_OF_PAIR,
            samRecord.getReadBases(),
            "Quality header comment",
            samRecord.getBaseQualities());
    // default method does not include the comment header
    testConvertedSAMRecord(FastqEncoder.asSAMRecord(fastqRecord, samRecord.getHeader()), samRecord);
    // test with qualityHeaderToComment=true populates the CO tag
    samRecord.setAttribute(SAMTag.CO.name(), fastqRecord.getBaseQualityHeader());
    testConvertedSAMRecord(
        FastqEncoder.asSAMRecord(
            fastqRecord, samRecord.getHeader(), FastqEncoder.QUALITY_HEADER_TO_COMMENT_TAG),
        samRecord);
  }

  @Test
  public void testAsSAMRecordParsedTags() throws Exception {
    final String bc = "ACTG";
    final String rg = "sample1";
    final int fi = 1;
    final String customTag = "ct";
    final char ct = 'a';
    final String tags =
        String.format(
            "%s:Z:%s\t%s:Z:%s\t%s:i:%d\t%s:A:%c",
            SAMTag.BC, bc, SAMTag.RG, rg, SAMTag.FI, fi, customTag, ct);
    final SAMRecord samRecord =
        new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "10M", null, 2);
    final FastqRecord record =
        new FastqRecord(
            samRecord.getReadName(), samRecord.getReadBases(), tags, samRecord.getBaseQualities());
    final SAMRecord converted =
        FastqEncoder.asSAMRecord(
            record, samRecord.getHeader(), FastqEncoder.QUALITY_HEADER_PARSE_SAM_TAGS);
    testConvertedSAMRecord(converted, samRecord);
    Assert.assertEquals(converted.getAttribute(SAMTag.BC.name()), bc);
    Assert.assertEquals(converted.getAttribute(SAMTag.RG.name()), rg);
    Assert.assertEquals(converted.getAttribute(SAMTag.FI.name()), fi);
    Assert.assertEquals(converted.getAttribute(customTag), ct);
  }

  private void testConvertedSAMRecord(final SAMRecord converted, final SAMRecord original) {
    Assert.assertEquals(converted.getReadName(), original.getReadName());
    Assert.assertEquals(converted.getBaseQualities(), original.getBaseQualities());
    Assert.assertEquals(converted.getReadBases(), original.getReadBases());
    Assert.assertEquals(
        converted.getStringAttribute(SAMTag.CO.name()),
        original.getStringAttribute(SAMTag.CO.name()));
    Assert.assertTrue(converted.getReadUnmappedFlag());
  }
}
