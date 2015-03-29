package htsjdk.samtools.reference;

import htsjdk.samtools.util.IOUtil;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IndexedFastaSequenceFileHadoopTest {
	String filePath = "testdata/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta";
	@Test
	public void testGetSeq() throws IOException {
		IndexedFastaSequenceFileHadoop indexedFastaSequenceFileRaw =
				new IndexedFastaSequenceFileHadoop(IOUtil.getFile(filePath));
		IndexedFastaSequenceFile indexedFastaSequenceFile =
				new IndexedFastaSequenceFile(IOUtil.getFile(filePath));
		
		
		ReferenceSequence ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chrM", 14571, 16571);
		String seq1 = getSeq(ref.getBases());
		ReferenceSequence ref2 = indexedFastaSequenceFile.getSubsequenceAt("chrM", 14571, 16571);
		String seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		//==================
		ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chrM", 1, 1345);
		seq1 = getSeq(ref.getBases());
		ref2 = indexedFastaSequenceFile.getSubsequenceAt("chrM", 1, 1345);
		seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		//=======================
		ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chrM", 2345, 3456);
		seq1 = getSeq(ref.getBases());
		ref2 = indexedFastaSequenceFile.getSubsequenceAt("chrM", 2345, 3456);
		seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		//=======================
		ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chr20", 1, 15654);
		seq1 = getSeq(ref.getBases());
		ref2 = indexedFastaSequenceFile.getSubsequenceAt("chr20", 1, 15654);
		seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		//=======================
		ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chr20", 943230, 1000000);
		seq1 = getSeq(ref.getBases());
		ref2 = indexedFastaSequenceFile.getSubsequenceAt("chr20", 943230, 1000000);
		seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		//=======================
		ref = indexedFastaSequenceFileRaw.getSubsequenceAt("chr20", 23450, 34560);
		seq1 = getSeq(ref.getBases());
		ref2 = indexedFastaSequenceFile.getSubsequenceAt("chr20", 23450, 34560);
		seq2 = getSeq(ref2.getBases());
		
		Assert.assertEquals(seq1, seq2);
		
		indexedFastaSequenceFileRaw.close();
		indexedFastaSequenceFile.close();
	}
	
	private String getSeq(byte[] readInfo) {
		StringBuilder sequence = new StringBuilder();
		for (byte b : readInfo) {
			char seq = (char)b;
			sequence.append(seq);
		}
		return sequence.toString();
	}
}
