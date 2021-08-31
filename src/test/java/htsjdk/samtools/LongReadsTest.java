package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.CloseableIterator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
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
        final IOPath copiedBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setValidationStringency(ValidationStringency.STRICT)
                .setDecodeEagerly(true);

        try (final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(PAC_BIO_READS, readsDecoderOptions);
             final ReadsDecoder copyDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(copiedBAMPath, readsDecoderOptions);
             final CloseableIterator<SAMRecord> originalIt = originalDecoder.iterator();
             final CloseableIterator<SAMRecord> copyIt = copyDecoder.iterator()) {

            Assert.assertEquals(copyDecoder.getHeader(), originalDecoder.getHeader());

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
        final IOPath copiedBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        // for each mapped read in the original source BAM, query both the original and the copy for
        // all reads that overlap that read's coordinates, and compare the results
        try (final ReadsDecoder sourceDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(PAC_BIO_READS));
             final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(PAC_BIO_READS));
             final ReadsDecoder copyDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(copiedBAMPath))) {
            for (final SAMRecord samRecord : sourceDecoder) {
                //skip unmapped records since we're going to use the read's coordinates as a query
                if (!samRecord.getReadUnmappedFlag()) {
                    try (final CloseableIterator<SAMRecord> originalIt = originalDecoder.queryOverlapping(
                            samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd());
                         final CloseableIterator<SAMRecord> copyIt = copyDecoder.queryOverlapping(
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

        // for each mapped read in the original source BAM, query both the original and the copy for
        // all reads that are contained within that read's coordinates, and compare the results
        try (final ReadsDecoder sourceDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(PAC_BIO_READS));
             final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(PAC_BIO_READS));
             final ReadsDecoder copyDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(tmpBAMPath))) {
            for (final SAMRecord samRecord : sourceDecoder) {
                //skip unmapped records since we're going to use the read's coordinates as a query
                if (!samRecord.getReadUnmappedFlag()) {
                    try (final CloseableIterator<SAMRecord> originalIt = originalDecoder.queryContained(
                            samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd());
                         final CloseableIterator<SAMRecord> copyIt = copyDecoder.queryContained(
                                 samRecord.getContig(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd())) {
                        compareQueryResults(originalIt, copyIt);
                    }
                }
            }
        }
    }

    @Test
    final public void testQueryUnmapped() throws IOException {
        final IOPath tmpBAMPath = createBAMCopyWithIndex(PAC_BIO_READS);

        // get all unmapped reads from the original BAM and the copy and compare the results
        try (final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(PAC_BIO_READS));
             final ReadsDecoder copyDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(ReadsBundle.resolveIndex(tmpBAMPath));
            final CloseableIterator<SAMRecord> originalIt = originalDecoder.queryUnmapped();
            final CloseableIterator<SAMRecord> copyIt = copyDecoder.queryUnmapped()) {
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

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setValidationStringency(ValidationStringency.STRICT)
                .setDecodeEagerly(true);

        // get the original header, and the original reads in memory
        try (final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(
                             ReadsBundle.resolveIndex(PAC_BIO_READS),
                             readsDecoderOptions)) {
            samFileHeader = originalDecoder.getHeader();
            for (final SAMRecord r : originalDecoder) {
                samRecords.add(r);
            }
        }

        // reverse the order of the records, and write them back out with presorted=false
        final List<SAMRecord> recordsInReverseOrder = new ArrayList(samRecords);
        Collections.reverse(recordsInReverseOrder);
        Assert.assertNotEquals(samRecords, recordsInReverseOrder);
        final IOPath sortedOutputPath = IOPathUtils.createTempPath("testSortOnWrite", ".bam");

        try (final ReadsEncoder bamEncoder = HtsDefaultRegistry.getReadsResolver().getReadsEncoder(
                new ReadsBundle(sortedOutputPath),
                new ReadsEncoderOptions().setPreSorted(false))) {
            bamEncoder.setHeader(samFileHeader);
            for (final SAMRecord samRecord : recordsInReverseOrder) {
                bamEncoder.write(samRecord);
            }
        }

        // now read back in both the original and the newly sorted reads that we wrote out, and compare the order
        try (final ReadsDecoder originalDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(
                             ReadsBundle.resolveIndex(PAC_BIO_READS),
                             readsDecoderOptions);
             final ReadsDecoder sortedDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(
                             sortedOutputPath,
                             readsDecoderOptions);
             final CloseableIterator<SAMRecord> originalIt = originalDecoder.iterator();
             final CloseableIterator<SAMRecord> sortedIt = sortedDecoder.iterator()) {
            Assert.assertEquals(sortedDecoder.getHeader(), originalDecoder.getHeader());
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
        // we need a better way to resolve the sibling - we can't use SamFiles.findIndex() because the file has
        // to already exist
        final IOPath tempIndexPath = new HtsPath(
                tmpBAMPath.toPath().resolveSibling(
                        tmpBAMPath.toPath().getFileName().toString().replaceFirst(".bam", ".bai")).toString());

        try (final ReadsDecoder bamDecoder = HtsDefaultRegistry.getReadsResolver().getReadsDecoder(
                ReadsBundle.resolveIndex(originalBAM));
             final ReadsEncoder bamEncoder = HtsDefaultRegistry.getReadsResolver().getReadsEncoder(
                     new ReadsBundle(tmpBAMPath, tempIndexPath),
                     new ReadsEncoderOptions().setPreSorted(true))) {
            bamEncoder.setHeader(bamDecoder.getHeader());
            for (final SAMRecord samRecord : bamDecoder) {
                bamEncoder.write(samRecord);
            }
        }
        // mark the index for deletion too once its been created
        tempIndexPath.toPath().toFile().deleteOnExit();
        return tmpBAMPath;
    }

    private void compareQueryResults(final CloseableIterator<SAMRecord> originalIt, final CloseableIterator<SAMRecord> copyIt) {
        while (originalIt.hasNext()) {
            final SAMRecord originalRecord = originalIt.next();
            Assert.assertTrue(copyIt.hasNext());
            final SAMRecord copyRecord = copyIt.next();
            Assert.assertEquals(copyRecord, originalRecord);
        }
        Assert.assertFalse(copyIt.hasNext());
    }

}
