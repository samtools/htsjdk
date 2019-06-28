package htsjdk.samtools.cram.structure;

import com.google.gson.Gson;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Collections;
import java.util.Set;

public class CompressionHeaderEncodingMapTest extends HtsjdkTest {

    @Test
    public void testAllHTSJDKWriteEncodings() {
        // make sure all of the expected encodings that HTSJDK writes are present in the default encoding map
        assertExpectedHTSJDKGeneratedEncodings(
                new CompressionHeaderEncodingMap(),
                StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK);
    }

    @Test
    public void testAllHTSJDKWriteEncodingsRoundTripThroughStream() throws IOException {
        // make sure all of the default encodings that HTSJDK writes can be round tripped
        final CompressionHeaderEncodingMap encodingMap = new CompressionHeaderEncodingMap();
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
            encodingMap.addExternalEncoding(dataSeries, ExternalCompressor.createGZIP());
        }

        // serialize and then resurrect the encoding map
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            //final SliceBlocksWriteStreams sliceBlocksWriteStreams = new SliceBlocksWriteStreams(compressionHeader, sliceBlocks);
            //sliceBlocks.write(CramVersions.CRAM_v3,
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

        final CompressionHeaderEncodingMap originalEncodingMap = new CompressionHeaderEncodingMap();
        originalEncodingMap.writeToPath(tempFile.toPath());
        final CompressionHeaderEncodingMap roundTripEncodingMap = CompressionHeaderEncodingMap.readFromPath(tempFile.toPath());

        //final Gson gson = new Gson();
        //final String originalEncodingMapString = gson.toJson(originalEncodingMap);
        //final String roundTripEncodingMapString = gson.toJson(roundTripEncodingMap);
        //System.out.println("Original:  " + originalEncodingMapString);
        //System.out.println("RoundTrip: " + roundTripEnc odingMapString);

        // equality fails due to roundtripped encoding params addresses being different on read
        Assert.assertEquals(roundTripEncodingMap, originalEncodingMap);
        //Assert.assertEquals(roundTripEncodingMapString, originalEncodingMapString);
    }

    private void assertExpectedHTSJDKGeneratedEncodings(
            final CompressionHeaderEncodingMap encodingMap,
            final Set<DataSeries> expectedMissingDataSeries) {
        for (final DataSeries dataSeries : DataSeries.values()) {
            // skip test marks and data series that are unused when writing:
            if (expectedMissingDataSeries.contains(dataSeries)) {
                Assert.assertNull(encodingMap.getEncodingParamsForDataSeries(dataSeries),
                        "Unexpected encoding key found: " + dataSeries.name());
            } else {
                Assert.assertNotNull(encodingMap.getEncodingParamsForDataSeries(dataSeries), "Encoding key not found: " + dataSeries.name());
                Assert.assertFalse(encodingMap.getEncodingParamsForDataSeries(dataSeries).id == EncodingID.NULL);
            }
        }
    }

}
