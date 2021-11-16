package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.io.CountingInputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Set;

public class CompressionHeaderEncodingMapTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    @Test
    public void testAllHTSJDKWriteEncodings() {
        // make sure all of the expected encodings that HTSJDK writes are present in the default encoding map
        assertExpectedHTSJDKGeneratedEncodings(
                new CompressionHeaderEncodingMap(new CRAMEncodingStrategy()),
                StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK);
    }

    @Test
    public void testAllHTSJDKWriteEncodingsRoundTripThroughStream() throws IOException {
        // make sure all of the default encodings that HTSJDK writes can be round tripped
        final CompressionHeaderEncodingMap encodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            encodingMap.write(baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                final CompressionHeaderEncodingMap roundTippedEncodingMap = new CompressionHeaderEncodingMap(is);
                assertExpectedHTSJDKGeneratedEncodings(
                        roundTippedEncodingMap,
                        StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK);
            }
        }
    }

    @Test
    public void testAllCRAMEncodingsRoundTripThroughStream() throws IOException {
        // Test that ALL data series can be round tripped through an encoding map, including the
        // data series that htsjdk doesn't normally write, by starting with a default compression
        // header and default encoding map, and synthesizing and adding the remaining data series
        // encodings to the map as if we were some other CRAM implementation, to ensure that we can
        // resurrect them on read.
        // NOTE: The obsolete DataSeries, TC and TN should be ignored on CRAM read and should not be written by HTSJDK
        final CompressionHeader compressionHeader = new CompressionHeader();
        final CompressionHeaderEncodingMap encodingMap = compressionHeader.getEncodingMap();
        for (final DataSeries dataSeries : StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK) {
            encodingMap.putExternalEncoding(dataSeries, new GZIPExternalCompressor(new CRAMEncodingStrategy().getGZIPCompressionLevel()));
        }

        // serialize and then resurrect the encoding map
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            encodingMap.write(baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                final CompressionHeaderEncodingMap roundTippedEncodingMap = new CompressionHeaderEncodingMap(is);
                assertExpectedHTSJDKGeneratedEncodings(roundTippedEncodingMap, CompressionHeaderEncodingMap.DATASERIES_NOT_READ_BY_HTSJDK);
            }
        }
    }

    @Test(description = "make sure that obsolete dataseries, TC and TN are ignored on CRAM read")
    public void testIgnoreObsoleteDataseriesOnRead() throws IOException{

        // 1301_slice_aux.cram has legacy dataseries TC, TN present in it.
        // TC, TN are now removed and are no longer written by htsjdk
        // This file is used to test if CRAM reader can ignore these dataseries.
        // 1301_slice_aux.cram is taken from hts-specs repo. It uses reference files, ce.fa and ce.fa.fai.
        // 1301_slice_aux.cram file: https://github.com/samtools/hts-specs/blob/master/test/cram/3.0/passed/1301_slice_aux.cram
        // Reference files: https://github.com/samtools/hts-specs/tree/master/test/cram
        final File sourceFile = new File(TEST_DATA_DIR, "1301_slice_aux.cram");

        // iterate through the CRAM file
        try (final InputStream cramInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
             final CramContainerIterator containerIterator = new CramContainerIterator(cramInputStream)) {
            while(containerIterator.hasNext()) {

                // read the container and its CompressionHeader
                final Container container = containerIterator.next();;
                final CompressionHeader compressionHeader = container.getCompressionHeader();

                // get the CompressionHeaderEncodingMap
                final CompressionHeaderEncodingMap encodingMap = compressionHeader.getEncodingMap();

                // make sure obsolete DataSeries TC, TN are not present in the CompressionHeaderEncodingMap
                for (final DataSeries dataseries : CompressionHeaderEncodingMap.DATASERIES_NOT_READ_BY_HTSJDK) {
                    Assert.assertNull(encodingMap.getEncodingDescriptorForDataSeries(dataseries),
                            "Unexpected encoding key found: " + dataseries.getCanonicalName());
                }
            }
        }
    }

    private void assertExpectedHTSJDKGeneratedEncodings(
            final CompressionHeaderEncodingMap encodingMap,
            final Set<DataSeries> expectedMissingDataSeries) {
        for (final DataSeries dataSeries : DataSeries.values()) {
            // skip test marks and data series that are unused when writing:
            if (expectedMissingDataSeries.contains(dataSeries)) {
                Assert.assertNull(encodingMap.getEncodingDescriptorForDataSeries(dataSeries),
                        "Unexpected encoding key found: " + dataSeries.name());
            } else {
                Assert.assertNotNull(encodingMap.getEncodingDescriptorForDataSeries(dataSeries),
                        "Encoding key not found: " + dataSeries.name());
                Assert.assertFalse(encodingMap.getEncodingDescriptorForDataSeries(dataSeries).getEncodingID() == EncodingID.NULL);
            }
        }
    }

}