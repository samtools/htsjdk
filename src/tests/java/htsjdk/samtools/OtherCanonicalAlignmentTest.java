package htsjdk.samtools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Pierre Lindenbaum @yokofakun
 *
 */
public class OtherCanonicalAlignmentTest
	{
	@Test
	public void testBasic() throws Exception
		{
		Assert.assertEquals("SA",ReservedTagConstants.SA);
		
		SamReader r=openSamReader();
		SAMRecordIterator iter=r.iterator();
		//get read 1
		Assert.assertTrue(iter.hasNext());
		SAMRecord rec=iter.next();
		Assert.assertFalse(rec.getOtherCanonicalAlignments().isEmpty());
		
		//get read 2
		Assert.assertTrue(iter.hasNext());
		rec=iter.next();
		Assert.assertTrue(rec.getOtherCanonicalAlignments().isEmpty());
		
		//get read 3
		Assert.assertTrue(iter.hasNext());
		rec=iter.next();
		List<OtherCanonicalAlignment> alns=rec.getOtherCanonicalAlignments();
		Assert.assertEquals(1,alns.size());
		OtherCanonicalAlignment aln=alns.get(0);
		Assert.assertEquals(rec.getReferenceName(),aln.getReferenceName());
		Assert.assertEquals(1,aln.getAlignmentStart());
		Assert.assertFalse(aln.getReadNegativeStrandFlag());
		Assert.assertEquals(2, aln.getCigarElements().size());
		CigarElement ce=aln.getCigarElements().get(0);
		Assert.assertEquals(84, ce.getLength());
		Assert.assertEquals(0,aln.getMappingQuality());
		Assert.assertEquals(0,aln.getNM());
		
		
		Assert.assertFalse(iter.hasNext());
		iter.close();
		r.close();
	 	}
	
	private String getSamFileText() {
		return  "@HD\tVN:1.4\tSO:coordinate\n"+
				"@SQ\tSN:SEGMENT1\tLN:4216\n"+
				"N00491:38:000000000-A5BCP:1:1101:13616:3205\t321\tSEGMENT1\t1\t0\t84H67M\t=\t1766\t1766\tCTGCGCGCTCGCTCGCTCACTGAGGCCGCCCGGGCAAAGCCCGGGCGTCGGGCGACCTTTGGTCGCC\tHGGHGGGGCG?CGHGGGHGHFFBFC?F>AFG@@CC-.</CHH--;A-;::-:AD-ADDFFE/;F-@F\tSA:Z:SEGMENT1,2083,+,69M82S,0,0;\tMD:Z:67\tNM:i:0\tAS:i:67\tXS:i:67\n"+
				"N00491:38:000000000-A5BCP:1:1101:13616:3205\t1153\tSEGMENT1\t1766\t60\t151M\t=\t2083\t318\tTAACAGGGTAATATAGGCGGCCGCTTCGAGCAGACATGATAAGATACATTGATGAGTTTGGACAAACCACAACTAGAATGCAGTGAAAAAAATGCTTTATTTGTGAAATTTGTGATGCTATTGCTTTATTTGTAACCATTATAAGCTGCAA\tBBBBBFBBFFFFGFGGGGGGGGGGGGGGHHGGGHHHHHHHHG3EGHHHHHB5FGGHHHHGGHHHGGHGHGHHGHHEFBGHHHHHGGGHHFGEFHHFHGBGHHHHH4?BGHHFDFGHHHHHHHHHHHHHHHGFHFHHHHHHHHGGFHHHGHB\tMD:Z:151\tNM:i:0\tAS:i:151\tXS:i:0\n"+
				"N00491:38:000000000-A5BCP:1:1101:13616:3205\t1089\tSEGMENT1\t2083\t0\t69M82S\t=\t1766\t-318\tTACAAGGAACCCCTAGTGATGGAGTTGGCCACTCCCTCTCTGCGCGCTCGCTCGCTCACTGAGGCCGGGTTGGCCACTCCCTCTCTGCGCGCTCGCTCGCTCACTGAGGCCGCCCGGGCAAAGCCCGGGCGTCGGGCGACCTTTGGTCGCC\tCABCBFCFFFCBGGGGGGGGGGHGHHHHHHHGHHHHGHGHHGHGGGGGGGGGHGGGHGHHHHHEFGEEGGGGGHHHGHHHHHHHHGGHGGGGCG?CGHGGGHGHFFBFC?F>AFG@@CC-.</CHH--;A-;::-:AD-ADDFFE/;F-@F\tSA:Z:SEGMENT1,1,+,84S67M,0,0;\tMD:Z:69\tNM:i:0\tAS:i:69\tXS:i:69\n"
				;
		}
	private SamReader openSamReader() {
		InputStream inputStream=new ByteArrayInputStream(getSamFileText().getBytes());
		return SamReaderFactory.make().open(SamInputResource.of(inputStream));
		}
	}
