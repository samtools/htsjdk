package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;

import java.io.File;
import java.io.FileInputStream;

import org.testng.Assert;
import org.testng.annotations.Test;

public class BamFileIoUtilsTest {
	private final File testFile = new File("testdata/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta");
	@Test
	public void testNioAndSeekableStream() throws Exception {
		long posFile = 12800;
		
		long posSeek = posFile;
		FileInputStream fileInputStream = new FileInputStream(testFile);
		SeekableStream seekableStream = SeekableStreamFactory.getInstance().getStreamFor(testFile);
		while (posFile > 0) {
			posFile -= fileInputStream.skip(posFile);
			posSeek -= seekableStream.skip(posSeek);
			Assert.assertEquals(posFile, posSeek);
        }
		long currentPosFile = fileInputStream.getChannel().position();
		long currentPosSeek = seekableStream.position();
		Assert.assertEquals(currentPosFile, currentPosSeek);
		fileInputStream.close();
		seekableStream.close();
    }
		
	
}
