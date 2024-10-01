package htsjdk.io;


import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SafeGZIPInputStreamTest {

    final String lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris pulvinar pulvinar eros, eu " +
            "convallis nulla sollicitudin non. Nunc in bibendum risus. Maecenas condimentum auctor libero a iaculis. " +
            "Nullam vitae ipsum arcu. Nunc risus mauris, venenatis vitae sem vel, laoreet tristique nisi. Phasellus " +
            "quis ipsum vel lacus molestie venenatis. Quisque at aliquet nisi. Phasellus porttitor ullamcorper ipsum," +
            " ac luctus quam tempor eu. Sed nibh purus, pellentesque eu metus sed, molestie dapibus lacus. Class aptent " +
            "taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Praesent eget tellus nec" +
            " turpis lobortis posuere. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac " +
            "turpis egestas. Pellentesque hendrerit pharetra pellentesque. Morbi congue placerat orci non accumsan. " +
            "Etiam vel finibus odio.";

//    @DataProvider
//    public static Object[][] getCases() {
//        return new Object[][]{
//                return new;
//        };
//    }


//    @Test(dataProvider = "getCases")
//    public void testGzipRoundTrip() throws IOException {
//        String original = lorem;
//        // Compress the original string
//        byte[] compressed = compress(original);
//
//        // Decompress the compressed data
//        String decompressed = decompress(compressed);
//
//        // Assert that the original string matches the decompressed string
//        Assert.assertEquals(decompressed, original, "The decompressed string should match the original string.");
//    }

    private byte[] compress(String data) throws IOException {
        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(baout)) {
            out.write(data.getBytes());
        }
        return baout.toByteArray();
    }

    private String decompress(byte[] compressedData, Function<InputStream, GZIPInputStream> newGzipInputStream) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(compressedData);
        try (GZIPInputStream gzipIn = newGzipInputStream.apply(bain)) {
            return new String(gzipIn.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}