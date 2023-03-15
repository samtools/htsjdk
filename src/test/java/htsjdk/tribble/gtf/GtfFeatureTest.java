/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.tribble.gtf;

import java.io.UnsupportedEncodingException;

import org.testng.Assert;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.annotation.Strand;

public class GtfFeatureTest extends HtsjdkTest {

	private GtfFeature decode(String...tokens) {
		return new GtfCodec().decode(String.join("\t", tokens));
		}
	

    @Test
    public void testGene() throws UnsupportedEncodingException {
    	final GtfFeature f1 = decode(
    			"chr9",
    			"HAVANA",
    			"gene",
    			"119312474",
    			"119408082",
    			".",
    			"-",
    			".",
    			"gene_id \"ENSMUSG00000032511.18\"; gene_type \"protein_coding\"; gene_name \"Scn5a\"; level 2; mgi_id \"MGI:98251\"; havana_gene \"OTTMUSG00000031832.3\";"
    			);
    	Assert.assertEquals(f1.getContig(), "chr9");
    	Assert.assertEquals(f1.getSource(), "HAVANA");
    	Assert.assertEquals(f1.getType(), "gene");
    	Assert.assertEquals(f1.getStart(), 119312474);
    	Assert.assertEquals(f1.getEnd(), 119408082);
    	Assert.assertFalse(f1.getScore().isPresent());
    	Assert.assertEquals(f1.getStrand(),Strand.NEGATIVE);
    	Assert.assertFalse(f1.getPhase().isPresent());
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.GENE_ID));
    	Assert.assertEquals(f1.getAttributes(GtfConstants.GENE_ID).size(),1);
    	Assert.assertTrue(f1.isGene());
    	Assert.assertFalse(f1.isTranscript());
    	Assert.assertEquals(f1.getGeneId(),"ENSMUSG00000032511.18");
    	Assert.assertEquals(f1.getGeneName(),"Scn5a");
    	Assert.assertNull(f1.getTranscriptId());
    }

    @Test
    public void testTranscript() throws UnsupportedEncodingException {
    	final GtfFeature f1 = decode(
    			"chr9",
    			"ENSEMBL",
    			"transcript",
    			"119312474",
    			"119391744",
    			".",
    			"-",
    			".",
    			"gene_id \"ENSMUSG00000032511.18\"; transcript_id \"ENSMUST00000065196.13\"; gene_type \"protein_coding\"; gene_name \"Scn5a\"; transcript_type \"protein_coding\"; transcript_name \"Scn5a-201\"; level 3; protein_id \"ENSMUSP00000066228.7\"; transcript_support_level \"5\"; mgi_id \"MGI:98251\"; tag \"basic\"; tag \"appris_principal_4\"; tag \"CCDS\"; ccdsid \"CCDS57715.1\"; havana_gene \"OTTMUSG00000031832.3\";"
    			);
    	Assert.assertEquals(f1.getContig(), "chr9");
    	Assert.assertEquals(f1.getSource(), "ENSEMBL");
    	Assert.assertEquals(f1.getType(), "transcript");
    	Assert.assertEquals(f1.getStart(), 119312474);
    	Assert.assertEquals(f1.getEnd(), 119391744);
    	Assert.assertFalse(f1.getScore().isPresent());
    	Assert.assertEquals(f1.getStrand(),Strand.NEGATIVE);
    	Assert.assertFalse(f1.getPhase().isPresent());
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.GENE_ID));
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.TRANSCRIPT_ID));
    	Assert.assertEquals(f1.getAttributes(GtfConstants.GENE_ID).size(),1);
    	Assert.assertEquals(f1.getAttributes(GtfConstants.TRANSCRIPT_ID).size(),1);
    	Assert.assertFalse(f1.isGene());
    	Assert.assertTrue(f1.isTranscript());
    	Assert.assertFalse(f1.isExon());
    	Assert.assertFalse(f1.isCDS());
    	Assert.assertEquals(f1.getGeneId(),"ENSMUSG00000032511.18");
    	Assert.assertEquals(f1.getTranscriptId(),"ENSMUST00000065196.13");

    }

    @Test
    public void testExon() throws UnsupportedEncodingException {
    	final GtfFeature f1 = decode(
    			"chr9",
    			"ENSEMBL",
    			"exon",
    			"119312474",
    			"119315884",
    			".",
    			"-",
    			".",
    			"gene_id \"ENSMUSG00000032511.18\"; transcript_id \"ENSMUST00000065196.13\"; gene_type \"protein_coding\"; gene_name \"Scn5a\"; transcript_type \"protein_coding\"; transcript_name \"Scn5a-201\"; exon_number 27; exon_id \"ENSMUSE00000505623.5\"; level 3; protein_id \"ENSMUSP00000066228.7\"; transcript_support_level \"5\"; mgi_id \"MGI:98251\"; tag \"basic\"; tag \"appris_principal_4\"; tag \"CCDS\"; ccdsid \"CCDS57715.1\"; havana_gene \"OTTMUSG00000031832.3\";"
    			);
    	Assert.assertEquals(f1.getContig(), "chr9");
    	Assert.assertEquals(f1.getSource(), "ENSEMBL");
    	Assert.assertEquals(f1.getType(), "exon");
    	Assert.assertEquals(f1.getStart(), 119312474);
    	Assert.assertEquals(f1.getEnd(), 119315884);
    	Assert.assertFalse(f1.getScore().isPresent());
    	Assert.assertEquals(f1.getStrand(),Strand.NEGATIVE);
    	Assert.assertFalse(f1.getPhase().isPresent());
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.GENE_ID));
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.TRANSCRIPT_ID));
    	Assert.assertEquals(f1.getAttributes(GtfConstants.GENE_ID).size(),1);
    	Assert.assertEquals(f1.getAttributes(GtfConstants.TRANSCRIPT_ID).size(),1);
    	Assert.assertFalse(f1.isGene());
    	Assert.assertFalse(f1.isTranscript());
    	Assert.assertTrue(f1.isExon());
    	Assert.assertFalse(f1.isCDS());
    	Assert.assertEquals(f1.getGeneId(),"ENSMUSG00000032511.18");
    	Assert.assertEquals(f1.getTranscriptId(),"ENSMUST00000065196.13");

    }

    @Test
    public void testCDS() throws UnsupportedEncodingException {
    	final GtfFeature f1 = decode(
    			"chr3",
    			"HAVANA",
    			"CDS",
    			"38550324",
    			"38551558",
    			".",
    			"-",
    			"2",
    			"gene_id \"ENSG00000183873.18\"; transcript_id \"ENST00000449557.6\"; gene_type \"protein_coding\"; gene_name \"SCN5A\"; transcript_type \"protein_coding\"; transcript_name \"SCN5A-206\"; exon_number 26; exon_id \"ENSE00001672933.1\"; level 2; protein_id \"ENSP00000413996.2\"; transcript_support_level \"5\"; hgnc_id \"HGNC:10593\"; tag \"basic\"; havana_gene \"OTTHUMG00000156166.6\"; havana_transcript \"OTTHUMT00000343227.3\";"
    			);
    	Assert.assertEquals(f1.getContig(), "chr3");
    	Assert.assertEquals(f1.getSource(), "HAVANA");
    	Assert.assertEquals(f1.getType(), "CDS");
    	Assert.assertEquals(f1.getStart(), 38550324);
    	Assert.assertEquals(f1.getEnd(), 38551558);
    	Assert.assertFalse(f1.getScore().isPresent());
    	Assert.assertEquals(f1.getStrand(),Strand.NEGATIVE);
    	Assert.assertTrue(f1.getPhase().isPresent());
    	Assert.assertEquals(f1.getPhase().orElse(-1),2);
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.GENE_ID));
    	Assert.assertTrue(f1.hasAttribute(GtfConstants.TRANSCRIPT_ID));
    	Assert.assertEquals(f1.getAttributes(GtfConstants.GENE_ID).size(),1);
    	Assert.assertEquals(f1.getAttributes(GtfConstants.TRANSCRIPT_ID).size(),1);
    	Assert.assertFalse(f1.isGene());
    	Assert.assertFalse(f1.isTranscript());
    	Assert.assertFalse(f1.isExon());
    	Assert.assertTrue(f1.isCDS());
    	Assert.assertEquals(f1.getGeneId(),"ENSG00000183873.18");
    	Assert.assertEquals(f1.getTranscriptId(),"ENST00000449557.6");
    }


}