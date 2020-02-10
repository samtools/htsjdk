/*
* Copyright (c) 2017 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author amila
 *         <p/>
 *         Class AsyncVariantContextWriterUnitTest
 *         <p/>
 *         This class tests out the ability of the VCF writer to correctly write VCF files with Asynchronous IO
 */
public class AsyncVariantContextWriterUnitTest extends VariantBaseTest {

    @BeforeClass
    private void createTemporaryDirectory() {
        File tempDir = TestUtil.getTempDirectory("VCFWriter", "StaleIndex");
        tempDir.deleteOnExit();
    }

    /** test, using the writer and reader, that we can output and input a VCF body without problems */
    @Test
    public void testWriteAndReadAsyncVCFHeaderless() throws IOException {
        final File fakeVCFFile = VariantBaseTest.createTempFile("testWriteAndReadAsyncVCFHeaderless.", FileExtensions.VCF);
        fakeVCFFile.deleteOnExit();

        Tribble.indexFile(fakeVCFFile).deleteOnExit();
        final Set<VCFHeaderLine> metaData = new HashSet<>();
        final Set<String> additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile).setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY, Options.USE_ASYNC_IO))
                .build()) {
            writer.setHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        final VCFCodec codec = new VCFCodec();
        codec.setVCFHeader(header, VCFHeaderVersion.VCF4_2);

        try (final FileInputStream fis = new FileInputStream(fakeVCFFile)) {
            final AsciiLineReaderIterator iterator = new AsciiLineReaderIterator(new AsciiLineReader(fis));
            int counter = 0;
            while (iterator.hasNext()) {
                VariantContext context = codec.decode(iterator.next());
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
    }

    /**
     * create a fake header of known quantity
     * @param metaData           the header lines
     * @param additionalColumns  the additional column names
     * @return a fake VCF header
     */
    public static VCFHeader createFakeHeader(final Set<VCFHeaderLine> metaData, final Set<String> additionalColumns,
                                             final SAMSequenceDictionary sequenceDict) {
        metaData.add(new VCFHeaderLine("two", "2"));
        additionalColumns.add("extra1");
        additionalColumns.add("extra2");
        final VCFHeader ret = new VCFHeader(metaData, additionalColumns);
        ret.setSequenceDictionary(sequenceDict);
        return ret;
    }

    /**
     * create a fake VCF record
     * @param header the VCF header
     * @return a VCFRecord
     */
    private VariantContext createVC(final VCFHeader header) {

        final List<Allele> alleles = new ArrayList<Allele>();
        final Map<String, Object> attributes = new HashMap<String,Object>();
        final GenotypesContext genotypes = GenotypesContext.create(header.getGenotypeSamples().size());

        alleles.add(Allele.create("A",true));
        alleles.add(Allele.create("ACC",false));

        attributes.put("DP","50");
        for (final String name : header.getGenotypeSamples()) {
            final Genotype gt = new GenotypeBuilder(name,alleles.subList(1,2)).GQ(0).attribute("BB", "1").phased(true).make();
            genotypes.add(gt);
        }
        return new VariantContextBuilder("RANDOM", "1", 1, 1, alleles)
                .genotypes(genotypes).attributes(attributes).make();
    }
}

