package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LongReadsTest extends HtsjdkTest {
    final String TEST_DATA = "src/test/resources/htsjdk/samtools/longreads/";

    // samtools-generated BAM of PacBio-sequenced reads, containing a subset of chr1, plus a handful of unmapped reads
    final IOPath PAC_BIO_READS = new HtsPath(TEST_DATA +
            "NA12878.m64020_190210_035026.chr21.5011316.5411316.unmapped.bam");

    @Test
    final public void testRoundTripBAM() throws IOException {
        // create a copy of the test bam, with an index, and then compare the header and all reads from
        // the copy with the original
        final IOPath tmpBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        try (final SamReader originalReader = SamReaderFactory.makeDefault()
                .validationStringency((ValidationStringency.STRICT))
                .setOption(SamReaderFactory.Option.EAGERLY_DECODE, true)
                .open(PAC_BIO_READS.toPath());
             final SamReader copyReader = SamReaderFactory.makeDefault()
                     .validationStringency((ValidationStringency.STRICT))
                     .setOption(SamReaderFactory.Option.EAGERLY_DECODE, true)
                     .open(tmpBAMPath.toPath())) {

            Assert.assertEquals(copyReader.getFileHeader(), originalReader.getFileHeader());

            final SAMRecordIterator originalIt = originalReader.iterator();
            final SAMRecordIterator copyIt = copyReader.iterator();

            while (originalIt.hasNext()) {
                final SAMRecord originalRecord = originalIt.next();
                Assert.assertTrue(copyIt.hasNext());
                final SAMRecord copyRecord = copyIt.next();

                // validate
                Assert.assertNull(originalRecord.isValid());
                Assert.assertNull(copyRecord.isValid());
                Assert.assertEquals(copyRecord, originalRecord);
            }
            Assert.assertFalse(copyIt.hasNext());
        }
    }

    @Test
    final public void testQueryOverlappingForEachRead() throws IOException {
        final IOPath tmpBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        // for each mapped read in the original BAM, query both the original and the copy for
        // all reads that overlap that read's coordinates, and compare the results
        try (final SamReader sourceReader = SamReaderFactory.makeDefault().open(PAC_BIO_READS.toPath());
             final SamReader originalReader = SamReaderFactory.makeDefault().open(PAC_BIO_READS.toPath());
             final SamReader copyReader = SamReaderFactory.makeDefault().open(tmpBAMPath.toPath())) {
            for (final SAMRecord samRecord : sourceReader) {
                //skip unmapped records since we're going to use the read's coordinates as a query
                if (!samRecord.getReadUnmappedFlag()) {
                    try (final SAMRecordIterator originalIt = originalReader.queryOverlapping(
                            samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd());
                         final SAMRecordIterator copyIt = copyReader.queryOverlapping(
                                 samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd())) {
                        compareQueryResults(originalIt, copyIt);
                    }
                }
            }
        }
    }

    @Test
    final public void testQueryContainedForEachRead() throws IOException {
        final IOPath tmpBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        // for each mapped read in the original BAM, query both the original and the copy for
        // all reads that overlap that read's coordinates, and compare the results
        try (final SamReader sourceReader = SamReaderFactory.makeDefault().open(PAC_BIO_READS.toPath());
             final SamReader originalReader = SamReaderFactory.makeDefault().open(PAC_BIO_READS.toPath());
             final SamReader copyReader = SamReaderFactory.makeDefault().open(tmpBAMPath.toPath())) {
            for (final SAMRecord samRecord : sourceReader) {
                //skip unmapped records since we're going to use the read's coordinates as a query
                if (!samRecord.getReadUnmappedFlag()) {
                    try (final SAMRecordIterator originalIt = originalReader.queryContained(
                            samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd());
                         final SAMRecordIterator copyIt = copyReader.queryContained(
                                 samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd())) {
                        compareQueryResults(originalIt, copyIt);
                    }
                }
            }
        }
    }

    private void compareQueryResults(final SAMRecordIterator originalIt, final SAMRecordIterator copyIt) {
        while (originalIt.hasNext()) {
            final SAMRecord originalRecord = originalIt.next();
            Assert.assertTrue(copyIt.hasNext());
            final SAMRecord copyRecord = copyIt.next();
            Assert.assertEquals(copyRecord, originalRecord);
        }
        Assert.assertFalse(copyIt.hasNext());
    }

    @Test
    final public void testQueryUnmapped() throws IOException {
        final IOPath tmpBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        // get all unmapped reads from the original BAM and the copy and compare the results
        try (final SamReader originalReader = SamReaderFactory.makeDefault().open(PAC_BIO_READS.toPath());
             final SamReader copyReader = SamReaderFactory.makeDefault().open(tmpBAMPath.toPath())) {

            final SAMRecordIterator originalIt = originalReader.queryUnmapped();
            final SAMRecordIterator copyIt = copyReader.queryUnmapped();
            while (originalIt.hasNext()) {
                final SAMRecord originalRecord = originalIt.next();
                Assert.assertTrue(copyIt.hasNext());
                final SAMRecord copyRecord = copyIt.next();

                Assert.assertEquals(copyRecord, originalRecord);
            }
            Assert.assertFalse(copyIt.hasNext());
        }
    }

    @Test
    final public void testSortOnWrite() throws IOException {
        final List<SAMRecord> samRecords = new ArrayList<>();
        SAMFileHeader samFileHeader;

        // get the original header, and the original reads in memory
        try (final SamReader bamReader = SamReaderFactory.makeDefault()
                .validationStringency((ValidationStringency.STRICT))
                .setOption(SamReaderFactory.Option.EAGERLY_DECODE, true)
                .open(PAC_BIO_READS.toPath())) {
            samFileHeader = bamReader.getFileHeader();
            for (final SAMRecord r : bamReader) {
                samRecords.add(r);
            }
        }

        // reverse the order of the records, and write back out with presorted=false
        final List<SAMRecord> recordsInReverseOrder = new ArrayList(samRecords);
        Collections.reverse(recordsInReverseOrder);
        Assert.assertNotEquals(samRecords, recordsInReverseOrder);
        final IOPath sortedOutputPath = IOPathUtils.createTempPath("testSortOnWrite", ".bam");
        try (final SAMFileWriter bamWriter =
                     new SAMFileWriterFactory()
                             .setCreateIndex(true)
                             .makeWriter(samFileHeader,
                                     false, // unsorted
                                     sortedOutputPath.toPath().toFile(),
                                     null)) {
            for (final SAMRecord samRecord : recordsInReverseOrder) {
                bamWriter.addAlignment(samRecord);
            }
        }

        // now read back in the newly sorted output, and compare with the original
        try (final SamReader originalReader = SamReaderFactory.makeDefault()
                .validationStringency((ValidationStringency.STRICT))
                .setOption(SamReaderFactory.Option.EAGERLY_DECODE, true)
                .open(PAC_BIO_READS.toPath());
             final SamReader sortedReader = SamReaderFactory.makeDefault()
                     .validationStringency((ValidationStringency.STRICT))
                     .setOption(SamReaderFactory.Option.EAGERLY_DECODE, true)
                     .open(sortedOutputPath.toPath())) {
            Assert.assertEquals(sortedReader.getFileHeader(), originalReader.getFileHeader());
            final SAMRecordIterator originalIt = originalReader.iterator();
            final SAMRecordIterator sortedIt = sortedReader.iterator();
            while (originalIt.hasNext()) {
                // since only the leftmost coordinate is considered when sorting, the newly sorted reads will not
                // necessarily be in exactly the same order as the original, so we can't check for read equality,
                // but the leftmost coordinate should always match
                Assert.assertEquals(sortedIt.next().getAlignmentStart(), originalIt.next().getAlignmentStart());
            }
            Assert.assertFalse(sortedIt.hasNext());
        }
    }

    private IOPath createBAMCopyWithIndex(final IOPath originalBAM) {
        final IOPath tmpBAMPath = IOPathUtils.createTempPath("longReadsRoundTrip", ".bam");
        final SamReader originalReader = SamReaderFactory.makeDefault().open(originalBAM.toPath());

        try (final SAMFileWriter bamWriter =
                     new SAMFileWriterFactory().setCreateIndex(true)
                             .makeWriter(originalReader.getFileHeader(), true, tmpBAMPath.toPath().toFile(), null)) {
            for (final SAMRecord samRecord : originalReader) {
                bamWriter.addAlignment(samRecord);
            }
        }

        // delete the index too
        final Path tempIndexPath = SamFiles.findIndex(tmpBAMPath.toPath());
        tempIndexPath.toFile().deleteOnExit();

        return tmpBAMPath;
    }

//    @Test
//    final public void testGetSomeUnmapped() throws IOException {
//        final IOPath inputPath = new HtsPath(
//                "/Users/cnorman/projects/testdata/longreads/NA12878.m64020_190210_035026.bam");
//        //final IOPath outputPath = IOPathUtils.createTempPath("test", ".bam");
//        final IOPath outputPath = new HtsPath(
//                "/Users/cnorman/projects/testdata/longreads/NA12878.m64020_190210_035026.unmapped.bam");
//        //final Path outputIndexPath = SamFiles.findIndex(outputPath.toPath());
//        //outputIndexPath.toFile().deleteOnExit();
//
//        final SamReader samReader = SamReaderFactory.makeDefault().open(inputPath.toPath());
//        try (final  SAMRecordIterator it = samReader.queryUnmapped()) {
//           final SAMFileWriterFactory samWriterFactory = new SAMFileWriterFactory().setCreateIndex(true);
//           try (final SAMFileWriter samWriter =
//                        samWriterFactory.makeWriter(samReader.getFileHeader(), true, outputPath.toPath().toFile(), null)) {
//               while (it.hasNext()) {
//                   final SAMRecord r = it.next();
//                   samWriter.addAlignment(r);
//                   //System.out.println(r.getReadLength());
//               }
//           }
//       }
//    }

}
