/*
* Copyright (c) 2012 The Broad Institute
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
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author aaron
 *         <p/>
 *         Class VCFWriterUnitTest
 *         <p/>
 *         This class tests out the ability of the VCF writer to correctly write VCF files
 */
public class VCFWriterUnitTest extends VariantBaseTest {
    private Set<VCFHeaderLine> metaData;
    private Set<String> additionalColumns;
    private File tempDir;

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectory("VCFWriter", "StaleIndex");
        tempDir.deleteOnExit();
    }


    /** test, using the writer and reader, that we can output and input a VCF file without problems */
    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void testBasicWriteAndRead(final String extension) throws IOException {
        final File fakeVCFFile = File.createTempFile("testBasicWriteAndRead.", extension, tempDir);
        fakeVCFFile.deleteOnExit();
        if (FileExtensions.COMPRESSED_VCF.equals(extension)) {
            new File(fakeVCFFile.getAbsolutePath() + FileExtensions.VCF_INDEX);
        } else {
            Tribble.indexFile(fakeVCFFile).deleteOnExit();
        }
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile)
                .setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build();
        writer.writeHeader(header);
        writer.add(createVC(header));
        writer.add(createVC(header));
        writer.close();
        final VCFCodec codec = new VCFCodec();
        final FeatureReader<VariantContext> reader = AbstractFeatureReader.getFeatureReader(fakeVCFFile.getAbsolutePath(), codec, false);
        final VCFHeader headerFromFile = (VCFHeader)reader.getHeader();

        int counter = 0;

        // validate what we're reading in
        validateHeader(headerFromFile, sequenceDict);

        try {
            final Iterator<VariantContext> it = reader.iterator();
            while(it.hasNext()) {
                it.next();
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
        catch (final IOException e ) {
            throw new RuntimeException(e.getMessage());
        }

    }

    /** test, using the writer and reader, that we can output and input a VCF body without problems */
    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void testWriteAndReadVCFHeaderless(final String extension) throws IOException {
        final File fakeVCFFile = File.createTempFile("testWriteAndReadVCFHeaderless.", extension, tempDir);
        fakeVCFFile.deleteOnExit();
        if (FileExtensions.COMPRESSED_VCF.equals(extension)) {
            new File(fakeVCFFile.getAbsolutePath() + ".tbi");
        } else {
            Tribble.indexFile(fakeVCFFile).deleteOnExit();
        }
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile).setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.setHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        final VCFCodec codec = new VCFCodec();
        codec.setVCFHeader(header, VCFHeaderVersion.VCF4_2);

        try (BlockCompressedInputStream bcis = new BlockCompressedInputStream(fakeVCFFile);
                FileInputStream fis = new FileInputStream(fakeVCFFile)) {
            AsciiLineReaderIterator iterator =
                    new AsciiLineReaderIterator(new AsciiLineReader(".vcf.gz".equals(extension) ? bcis : fis));
            int counter = 0;
            while (iterator.hasNext()) {
                VariantContext context = codec.decode(iterator.next());
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }

    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testWriteHeaderTwice() {
        final File fakeVCFFile = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFFile.deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent writing header twice
        try (final VariantContextWriter writer1 = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer1.writeHeader(header);
            writer1.writeHeader(header);
        }
    }


    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingHeader() {
        final File fakeVCFFile = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFFile.deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent changing header if it's already written
        try (final VariantContextWriter writer2 = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer2.writeHeader(header);
            writer2.setHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingBody() {
        final File fakeVCFFile = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFFile.deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent changing header if part of body is already written
        try (final VariantContextWriter writer3 = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer3.setHeader(header);
            writer3.add(createVC(header));
            writer3.setHeader(header);
        }
    }

    /**
     * create a fake header of known quantity
     * @param metaData           the header lines
     * @param additionalColumns  the additional column names
     * @return a fake VCF header
     */
    private static VCFHeader createFakeHeader(final Set<VCFHeaderLine> metaData, final Set<String> additionalColumns,
                                             final SAMSequenceDictionary sequenceDict) {
        metaData.add(new VCFHeaderLine(VCFHeaderVersion.VCF4_0.getFormatString(), VCFHeaderVersion.VCF4_0.getVersionString()));
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

       return createVCGeneral(header,"1",1);
    }

    private VariantContext createVCGeneral(final VCFHeader header, final String chrom, final int position) {
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
        return new VariantContextBuilder("RANDOM", chrom, position, position, alleles)
                .genotypes(genotypes).attributes(attributes).make();
    }


    /**
     * validate a VCF header
     * @param header the header to validate
     */
    private void validateHeader(final VCFHeader header, final SAMSequenceDictionary sequenceDictionary) {
        // check the fields
        int index = 0;
        for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields()) {
            Assert.assertEquals(VCFHeader.HEADER_FIELDS.values()[index], field);
            index++;
        }
        Assert.assertEquals(header.getMetaDataInSortedOrder().size(), metaData.size() + sequenceDictionary.size());
        index = 0;
        for (final String key : header.getGenotypeSamples()) {
            Assert.assertTrue(additionalColumns.contains(key));
            index++;
        }
        Assert.assertEquals(index, additionalColumns.size());
    }

    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void TestWritingLargeVCF(final String extension) throws FileNotFoundException, InterruptedException {

        final Set<VCFHeaderLine> metaData = new HashSet<VCFHeaderLine>();
        final Set<String> Columns = new HashSet<String>();
        for (int i = 0; i < 123; i++) {

            Columns.add(String.format("SAMPLE_%d", i));
        }

        final SAMSequenceDictionary dict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData,Columns, dict);

        final File vcf = new File(tempDir, "test" + extension);
        final String indexExtension;
        if (extension.equals(FileExtensions.COMPRESSED_VCF)) {
            indexExtension = FileExtensions.TABIX_INDEX;
        } else {
            indexExtension = FileExtensions.TRIBBLE_INDEX;
        }
        final File vcfIndex = new File(vcf.getAbsolutePath() + indexExtension);
        vcfIndex.deleteOnExit();

        for(int count=1;count<2; count++){
            final VariantContextWriter writer =  new VariantContextWriterBuilder()
                    .setOutputFile(vcf)
                    .setReferenceDictionary(dict)
                    .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                    .build();
            writer.writeHeader(header);

            for (int i = 1; i < 17 ; i++) { // write 17 chromosomes
                for (int j = 1; j < 10; j++) { //10 records each
                    writer.add(createVCGeneral(header, String.format("%d", i), j * 100));
                }
            }
            writer.close();

            Assert.assertTrue(vcf.lastModified() <= vcfIndex.lastModified());
        }
    }

    @DataProvider(name = "vcfExtensionsDataProvider")
    public Object[][]vcfExtensionsDataProvider() {
        return new Object[][] {
                // TODO: BCF doesn't work because header is not properly constructed.
                // {".bcf"},
                {FileExtensions.VCF},
                {FileExtensions.COMPRESSED_VCF}
        };
    }


    /**
     * A test to ensure that if we add a line to a VCFHeader it will persist through
     * a round-trip write/read cycle via VariantContextWriter/VCFFileReader
     */
    @Test
    public void testModifyHeader() {
        final File originalVCF = new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(originalVCF, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();

        header.addMetaDataLine(new VCFHeaderLine("FOOBAR", "foovalue"));

        final File outputVCF = createTempFile("testModifyHeader", FileExtensions.VCF);
        final VariantContextWriter writer = new VariantContextWriterBuilder().setOutputFile(outputVCF).setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER)).build();
        writer.writeHeader(header);
        writer.close();

        final VCFFileReader roundtripReader = new VCFFileReader(outputVCF, false);
        final VCFHeader roundtripHeader = roundtripReader.getFileHeader();
        roundtripReader.close();

        Assert.assertNotNull(roundtripHeader.getOtherHeaderLine("FOOBAR"), "Could not find FOOBAR header line after a write/read cycle");
        Assert.assertEquals(roundtripHeader.getOtherHeaderLine("FOOBAR").getValue(), "foovalue", "Wrong value for FOOBAR header line after a write/read cycle");
    }


    /**
     *
     * A test to check that we can't write VCF with missing header.
     */
    @Test(dataProvider = "vcfExtensionsDataProvider", expectedExceptions = IllegalStateException.class)
    public void testWriteWithEmptyHeader(final String extension) throws IOException {
        final File fakeVCFFile = File.createTempFile("testWriteAndReadVCFHeaderless.", extension, tempDir);
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile(fakeVCFFile).setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.add(createVC(header));
        }
    }
}

