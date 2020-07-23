package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.htsget.HtsgetClass;
import htsjdk.samtools.util.htsget.HtsgetFormat;
import htsjdk.samtools.util.htsget.HtsgetResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;

public class HtsgetResponseUnitTest extends HtsjdkTest {
    @Test
    public void testDeserialization() {
        final String respJson = "{\n   \"htsget\" : {\n      \"format\" : \"BAM\",\n      \"urls\" : [\n         {\n            \"url\" : \"data:application/vnd.ga4gh.bam;base64,QkFNAQ==\",\n            \"class\" : \"header\"\n         },\n         {\n            \"url\" : \"https://htsget.blocksrv.example/sample1234/header\",\n            \"class\" : \"header\"\n         },\n         {\n            \"url\" : \"https://htsget.blocksrv.example/sample1234/run1.bam\",\n            \"headers\" : {\n               \"Authorization\" : \"Bearer xxxx\",\n               \"Range\" : \"bytes=65536-1003750\"\n             },\n            \"class\" : \"body\"\n         },\n         {\n            \"url\" : \"https://htsget.blocksrv.example/sample1234/run1.bam\",\n            \"headers\" : {\n               \"Authorization\" : \"Bearer xxxx\",\n               \"Range\" : \"bytes=2744831-9375732\"\n            },\n            \"class\" : \"body\"\n         }\n      ]\n   }\n}";

        final HtsgetResponse resp = HtsgetResponse.parse(respJson);

        Assert.assertEquals(resp.getFormat(), HtsgetFormat.BAM);

        Assert.assertEquals(resp.getBlocks().get(0).getUri(), URI.create("data:application/vnd.ga4gh.bam;base64,QkFNAQ=="));
        Assert.assertEquals(resp.getBlocks().get(0).getDataClass(), HtsgetClass.header);

        Assert.assertEquals(resp.getBlocks().get(2).getHeaders().get("Authorization"), "Bearer xxxx");
        Assert.assertEquals(resp.getBlocks().get(2).getHeaders().get("Range"), "bytes=65536-1003750");
    }
}
