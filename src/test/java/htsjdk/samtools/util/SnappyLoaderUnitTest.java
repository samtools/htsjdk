package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;

public class SnappyLoaderUnitTest extends HtsjdkTest{

    @Test
    public void testCanLoadAndRoundTripWithSnappy() throws IOException {
        final SnappyLoader snappyLoader = new SnappyLoader(false);
        Assert.assertTrue(snappyLoader.isSnappyAvailable());
        final File tempFile = File.createTempFile("snappyOutput", ".txt");
        tempFile.deleteOnExit();

        final String toWrite = "Hello Filesystem";
        try(Writer out = new OutputStreamWriter(snappyLoader.wrapOutputStream(new FileOutputStream(tempFile)))) {
            out.write(toWrite);
        }
        
        try(LineReader in = new BufferedLineReader(snappyLoader.wrapInputStream(new FileInputStream(tempFile)))){
            final String recoveredString = in.readLine();
            Assert.assertEquals(recoveredString, toWrite);
        }
    }

    @Test
    public void testCanDisableSnappy(){
        final SnappyLoader snappyLoader = new SnappyLoader(true);
        Assert.assertFalse(snappyLoader.isSnappyAvailable());
    }

    @Test(expectedExceptions = SAMException.class)
    public void disabledSnappyCantCreateInputWrappers(){
        final SnappyLoader snappyLoader = new SnappyLoader(true);
        Assert.assertFalse(snappyLoader.isSnappyAvailable());
        snappyLoader.wrapInputStream(new ByteArrayInputStream(new byte[]{ 0,0,0}));
    }

    @Test(expectedExceptions = SAMException.class)
    public void disabledSnappyCantCreateOutputWrappers(){
        final SnappyLoader snappyLoader = new SnappyLoader(true);
        Assert.assertFalse(snappyLoader.isSnappyAvailable());
        snappyLoader.wrapOutputStream(new ByteArrayOutputStream(10));
    }
}