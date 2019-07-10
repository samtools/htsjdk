package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Collections;
import java.util.Set;

public class CompressionHeaderEncodingMapTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram/json");

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
        final CompressionHeader compressionHeader = new CompressionHeader();
        final CompressionHeaderEncodingMap encodingMap = compressionHeader.getEncodingMap();
        for (final DataSeries dataSeries : StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK) {
            encodingMap.addExternalEncoding(dataSeries, new GZIPExternalCompressor(new CRAMEncodingStrategy().getGZIPCompressionLevel()));
        }

        // serialize and then resurrect the encoding map
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            encodingMap.write(baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                final CompressionHeaderEncodingMap roundTippedEncodingMap = new CompressionHeaderEncodingMap(is);
                assertExpectedHTSJDKGeneratedEncodings(roundTippedEncodingMap, Collections.EMPTY_SET);
            }
        }
    }

    @Test
    public void testAllHTSJDKWriteEncodingsRoundTripThroughJSON() throws IOException {
        final File tempFile = File.createTempFile("encodingMapTest", ".json");
        tempFile.deleteOnExit();

        final CompressionHeaderEncodingMap originalEncodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
        originalEncodingMap.writeToPath(tempFile.toPath());
        final CompressionHeaderEncodingMap roundTripEncodingMap = CompressionHeaderEncodingMap.readFromPath(tempFile.toPath());

        Assert.assertEquals(roundTripEncodingMap, originalEncodingMap);
    }

    @DataProvider
    public Object[][] getBadEncodingMapJSONs() {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "encodingMapWithBadMapVersion.json") }, // htsjdkCRAMEncodingMapVersion 99999
                { new File(TEST_DATA_DIR, "encodingMapWithBadCRAMVersion.json") } // CRAM version 271.1.8
        };
    }

    @Test(dataProvider="getBadEncodingMapJSONs", expectedExceptions = IllegalArgumentException.class)
    public void testRejectJSONWithEncodingVersionMismatch(final File badJSONFile) {
        CompressionHeaderEncodingMap.readFromPath(badJSONFile.toPath());
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
