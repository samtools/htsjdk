/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble.index;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

/**
 * User: jacob
 * Date: 2012-Aug-23
 */
public class IndexFactoryTest {

    final File sortedBedFile = new File(TestUtils.DATA_DIR + "bed/Unigene.sample.bed");
    final File unsortedBedFile = new File(TestUtils.DATA_DIR + "bed/unsorted.bed");
    final File discontinuousFile = new File(TestUtils.DATA_DIR + "bed/disconcontigs.bed");
    final BEDCodec bedCodec = new BEDCodec();

    @Test
    public void testCreateLinearIndex() throws Exception {
        Index index = IndexFactory.createLinearIndex(sortedBedFile, bedCodec);
        String chr = "chr2";

        Assert.assertTrue(index.getSequenceNames().contains(chr));
        Assert.assertTrue(index.containsChromosome(chr));
        Assert.assertEquals(1, index.getSequenceNames().size());
        List<Block> blocks = index.getBlocks(chr, 1, 50);
        Assert.assertEquals(1, blocks.size());

        Block block = blocks.get(0);
        Assert.assertEquals(78, block.getSize());
    }

    @Test(expectedExceptions = TribbleException.MalformedFeatureFile.class, dataProvider = "indexFactoryProvider")
    public void testCreateIndexUnsorted(IndexFactory.IndexType type) throws Exception{
        Index index = IndexFactory.createIndex(unsortedBedFile, bedCodec, type);
    }

    @Test(expectedExceptions = TribbleException.MalformedFeatureFile.class, dataProvider = "indexFactoryProvider")
    public void testCreateIndexDiscontinuousContigs(IndexFactory.IndexType type) throws Exception{
        Index index = IndexFactory.createIndex(discontinuousFile, bedCodec, type);
    }

    @DataProvider(name = "indexFactoryProvider")
    public Object[][] getIndexFactoryTypes(){
        return new Object[][] {
                new Object[] { IndexFactory.IndexType.LINEAR },
                new Object[] { IndexFactory.IndexType.INTERVAL_TREE }
        };
    }

    @Test
    public void testCreateTabixIndexOnBlockCompressed() {
        // index a VCF
        final File inputFileVcf = new File("src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf");
        final VCFFileReader readerVcf = new VCFFileReader(inputFileVcf, false);
        final SAMSequenceDictionary vcfDict = readerVcf.getFileHeader().getSequenceDictionary();
        final TabixIndex tabixIndexVcf =
                IndexFactory.createTabixIndex(inputFileVcf, new VCFCodec(), TabixFormat.VCF,
                vcfDict);

        // index the same bgzipped VCF
        final File inputFileVcfGz = new File("src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz");
        final VCFFileReader readerVcfGz = new VCFFileReader(inputFileVcfGz, false);
        final TabixIndex tabixIndexVcfGz =
                IndexFactory.createTabixIndex(inputFileVcfGz, new VCFCodec(), TabixFormat.VCF,
                        readerVcfGz.getFileHeader().getSequenceDictionary());

        // assert that each sequence in the header that represents some VCF row ended up in the index
        // for both the VCF and bgzipped VCF
        for (SAMSequenceRecord samSequenceRecord : vcfDict.getSequences()) {
            Assert.assertTrue(
                    tabixIndexVcf.containsChromosome(samSequenceRecord.getSequenceName()),
                    "Tabix indexed VCF does not contain sequence: " + samSequenceRecord.getSequenceName());

            Assert.assertTrue(
                    tabixIndexVcfGz.containsChromosome(samSequenceRecord.getSequenceName()),
                    "Tabix indexed (bgzipped) VCF does not contain sequence: " + samSequenceRecord.getSequenceName());
        }
    }
}
