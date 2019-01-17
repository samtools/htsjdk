package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Created by vadim on 11/01/2016.
 */
public class ContainerParserTest extends HtsjdkTest {

    @Test
    public void testEOF() {
        ContainerParser parser = new ContainerParser(new SAMFileHeader());
        ByteArrayOutputStream v2_baos = new ByteArrayOutputStream();
        Version version = CramVersions.CRAM_v2_1;
        CramIO.issueEOF(version, v2_baos);
        Container container = ContainerIO.readContainer(version, new ByteArrayInputStream(v2_baos.toByteArray()));
        Assert.assertTrue(container.isEOF());
        Assert.assertTrue(parser.getRecords(null, container, ValidationStringency.STRICT).isEmpty());

        ByteArrayOutputStream v3_baos = new ByteArrayOutputStream();
        version = CramVersions.CRAM_v3;
        CramIO.issueEOF(version, v3_baos);
        container = ContainerIO.readContainer(version, new ByteArrayInputStream(v3_baos.toByteArray()));
        Assert.assertTrue(container.isEOF());
        Assert.assertTrue(parser.getRecords(null, container, ValidationStringency.STRICT).isEmpty());
    }

}
