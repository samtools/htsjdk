package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.htsget.HtsgetClass;
import htsjdk.samtools.util.htsget.HtsgetErrorResponse;
import htsjdk.samtools.util.htsget.HtsgetFormat;
import htsjdk.samtools.util.htsget.HtsgetResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

public class HtsgetResponseUnitTest extends HtsjdkTest {
    @Test
    public void testDeserialization() {
        final String respJson = "{\"htsget\":{\"format\":\"BAM\",\"urls\":[{\"url\":\"data:application/vnd.ga4gh.bam;base64,QkFNAQ==\",\"class\":\"header\"},{\"url\":\"https://htsget.blocksrv.example/sample1234/header\",\"class\":\"header\"},{\"url\":\"https://htsget.blocksrv.example/sample1234/run1.bam\",\"headers\":{\"Authorization\":\"Bearer xxxx\",\"Range\":\"bytes=65536-1003750\"},\"class\":\"body\"},{\"url\":\"https://htsget.blocksrv.example/sample1234/run1.bam\",\"headers\":{\"Authorization\":\"Bearer xxxx\",\"Range\":\"bytes=2744831-9375732\"},\"class\":\"body\"}]}}";
        final HtsgetResponse resp = HtsgetResponse.parse(respJson);

        Assert.assertEquals(resp.getFormat(), HtsgetFormat.BAM);

        Assert.assertEquals(resp.getBlocks().get(0).getUri(), URI.create("data:application/vnd.ga4gh.bam;base64,QkFNAQ=="));
        Assert.assertEquals(resp.getBlocks().get(0).getDataClass(), HtsgetClass.header);

        Assert.assertEquals(resp.getBlocks().get(2).getHeaders().get("Authorization"), "Bearer xxxx");
        Assert.assertEquals(resp.getBlocks().get(2).getHeaders().get("Range"), "bytes=65536-1003750");
    }

    @Test
    public void testErrorDeserialization() {
        final String respJson = "{\"htsget\":{\"error\":\"NotFound\",\"message\":\"No such accession 'ENS16232164'\"}}";
        final HtsgetErrorResponse err = HtsgetErrorResponse.parse(respJson);
        Assert.assertEquals(err.getError(), "NotFound");
        Assert.assertEquals(err.getMessage(), "No such accession 'ENS16232164'");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    @SuppressWarnings("StatementWithEmptyBody")
    public void testNonBase64EncodedDataURI() throws IOException {
        final String respJson = "{\"htsget\":{\"format\":\"BAM\",\"urls\":[{\"url\":\"data:application/vnd.ga4gh.bam;base64,ZW5jb2RlZCA=\",\"class\":\"header\"},{\"url\":\"data:application/vnd.ga4gh.bam;base64,dGVzdCA=\",\"class\":\"body\"},{\"url\":\"data:application/vnd.ga4gh.bam;somethingelse,ZGF0YQ==\",\"class\":\"body\"}]}}";
        final HtsgetResponse resp = HtsgetResponse.parse(respJson);

        final InputStream data = resp.getDataStream();
        while (data.read() != -1) ;
    }

    @Test
    public void testBase64Decode() throws IOException {
        final String respJson = "{\"htsget\":{\"format\":\"BAM\",\"urls\":[{\"url\":\"data:application/vnd.ga4gh.bam;base64,ZW5jb2RlZCA=\",\"class\":\"header\"},{\"url\":\"data:application/vnd.ga4gh.bam;base64,dGVzdCA=\",\"class\":\"body\"},{\"url\":\"data:application/vnd.ga4gh.bam;base64,ZGF0YQ==\",\"class\":\"body\"}]}}";
        final HtsgetResponse resp = HtsgetResponse.parse(respJson);

        final BufferedReader reader = new BufferedReader(new InputStreamReader(resp.getDataStream()));
        final StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }
        final String actual = out.toString();
        Assert.assertEquals(actual, "encoded test data");
    }
}
