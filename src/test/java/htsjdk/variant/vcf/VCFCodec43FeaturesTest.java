package htsjdk.variant.vcf;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.TestUtil;
import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 **************************************************************************************************
 * IMPORTANT NOTE: this class contains string constants that contain embedded non-ASCII characters
 * used for testing VCF UTF-8 support. Edit with care.
 **************************************************************************************************
 */
public class VCFCodec43FeaturesTest extends VariantBaseTest {
    private static final Path TEST_PATH = Paths.get("src/test/resources/htsjdk/variant/vcf43/");
    private static final Path TEST_43_FILE = TEST_PATH.resolve("all43Features.vcf");
    private static final Path TEST_43_GZ_FILE = TEST_PATH.resolve("all43FeaturesCompressed.vcf.gz");
    // NOTE: these test file contain UTF8-encoded characters, and must be edited using a
    // UTF8-encoding aware text editor
    private static final Path TEST_43_UTF8_FILE = TEST_PATH.resolve("all43Features.utf8.vcf");
    private static final Path TEST_43_UTF8_GZ_FILE = TEST_PATH.resolve("all43FeaturesCompressed.utf8.vcf.gz");

    @DataProvider(name="all43Files")
    private Object[][] allVCF43Files() {
        return new Object[][] {
                // a .vcf, .vcf.gz, .vcf with UTF8 chars, and .vcf.gz with UTF8 chars
                { TEST_43_FILE },
                { TEST_43_UTF8_FILE },
                { TEST_43_GZ_FILE },
                { TEST_43_UTF8_GZ_FILE }
        };
    }

    @Test(dataProvider="all43Files")
    public void testReadAllVCF43Features(final Path testFile) {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(testFile);

        Assert.assertEquals(entireVCF.a.getMetaDataInInputOrder().size(), 70);
        Assert.assertEquals(entireVCF.b.size(), 25);
    }

    @Test(dataProvider="all43Files")
    public void testVCF43SampleLine(final Path testFile) {
        // ##SAMPLE=<ID=NA19238,Assay=WholeGenome,Ethnicity=AFR,Disease=None,Description="Test NA19238 SAMPLE header line",
        // DOI=http://someurl,ExtraSampleField="extra sample">
        final VCFSampleHeaderLine sampleLine = getHeaderLineFromTestFile(
                testFile,
                "SAMPLE",
                "NA19238",
                hl -> (VCFSampleHeaderLine) hl);

        Assert.assertEquals(sampleLine.getGenericFieldValue("Assay"), "WholeGenome");
        Assert.assertEquals(sampleLine.getGenericFieldValue("Ethnicity"), "AFR");
        Assert.assertEquals(sampleLine.getGenericFieldValue("Disease"), "None");
        Assert.assertEquals(sampleLine.getGenericFieldValue("Description"), "Test NA19238 SAMPLE header line");
        Assert.assertEquals(sampleLine.getGenericFieldValue("DOI"), "http://someurl");
        Assert.assertEquals(sampleLine.getGenericFieldValue("ExtraSampleField"), "extra sample");
    }

    @Test(dataProvider="all43Files")
    public void testVCF43AltLine(final Path testFile) {
        // ##ALT=<ID=DEL,Description="Deletion",ExtraAltField="extra alt">
        final VCFAltHeaderLine altLine = getHeaderLineFromTestFile(
                testFile,
                "ALT",
                "DEL",
                hl -> (VCFAltHeaderLine) hl);

        Assert.assertEquals(altLine.getGenericFieldValue("Description"), "Deletion");
        Assert.assertEquals(altLine.getGenericFieldValue("ExtraAltField"), "extra alt");
    }

    @Test(dataProvider="all43Files")
    public void testVCF43PedigreeLine(final Path testFile) {
        // ##PEDIGREE=<ID=ChildID,Father=FatherID,Mother=MotherID,ExtraPedigreeField="extra pedigree">
        final VCFPedigreeHeaderLine pedigreeLine = getHeaderLineFromTestFile(
                testFile,
                "PEDIGREE",
                "ChildID",
                hl -> (VCFPedigreeHeaderLine) hl);

        Assert.assertEquals(pedigreeLine.getGenericFieldValue("Father"), "FatherID");
        Assert.assertEquals(pedigreeLine.getGenericFieldValue("Mother"), "MotherID");
        Assert.assertEquals(pedigreeLine.getGenericFieldValue("ExtraPedigreeField"), "extra pedigree");
    }

    @Test(dataProvider="all43Files")
    public void testVCF43MetaLine(final Path testFile) {
        // ##META=<ID=Assay,Type=String,Number=.,Values=[WholeGenome or Exome],ExtraMetaField="extra meta">
        final VCFMetaHeaderLine metaLine = getHeaderLineFromTestFile(
                testFile,
                "META",
                "Assay",
                hl -> (VCFMetaHeaderLine) hl);

        Assert.assertEquals(metaLine.getGenericFieldValue("Type"), "String");
        Assert.assertEquals(metaLine.getGenericFieldValue("ExtraMetaField"), "extra meta");
    }

    @Test(dataProvider="all43Files")
    public void testVCF43PercentEncoding(final Path testFile) {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(testFile);

        // 1       327     .       T       <*>     666.18  GATK_STANDARD;HARD_TO_VALIDATE
        // AB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0.00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=fil%3AteredInBoth
        final VariantContext vc = entireVCF.b.get(0);
        Assert.assertEquals(vc.getContig(), "1");
        Assert.assertEquals(vc.getStart(), 327);
        // set=fil%3AteredInBoth
        Assert.assertEquals(vc.getCommonInfo().getAttribute("set"), "fil:teredInBoth");
    }

    @Test(dataProvider="all43Files")
    public void testSymbolicAlternateAllele(final Path testFile) {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(testFile);

        // 1       327     .       T       <*>     666.18  GATK_STANDARD;HARD_TO_VALIDATE
        // AB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0.00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=fil%3AteredInBoth
        final VariantContext vc = entireVCF.b.get(0);
        Assert.assertEquals(vc.getContig(), "1");
        Assert.assertEquals(vc.getStart(), 327);

        final Allele symbolicAlternateAllele = vc.getAlternateAllele(0);
        Assert.assertTrue(symbolicAlternateAllele.isSymbolic());
        Assert.assertTrue(symbolicAlternateAllele.isNonRefAllele());
        Assert.assertTrue(symbolicAlternateAllele.isNonReference());
        Assert.assertEquals(symbolicAlternateAllele, Allele.create(Allele.UNSPECIFIED_ALTERNATE_ALLELE_STRING));
    }

    @DataProvider(name="all43IndexableFiles")
    private Object[][] allVCF43IndexableFiles() {
        return new Object[][] {
                { TEST_43_GZ_FILE },
                { TEST_43_UTF8_GZ_FILE }
        };
    }

    @Test(dataProvider="all43IndexableFiles")
    public void testVCF43IndexRoundTripQuery(final Path testFile) throws IOException {
        final File tempDir = TestUtil.getTempDirectory("VCF43Codec", "indextest");
        tempDir.deleteOnExit();

        // copy our input vcf to a temp location, and create a tabix index
        Files.copy(testFile, (new File (tempDir, testFile.toFile().getName())).toPath());
        final File vcfFileCopy = new File(tempDir, testFile.toFile().getName());
        final Index index = IndexFactory.createIndex(vcfFileCopy, new VCFCodec(), IndexFactory.IndexType.TABIX);
        final File indexFile = new File(tempDir, vcfFileCopy.getName() + FileExtensions.TABIX_INDEX);
        index.write(indexFile);
        Assert.assertTrue(indexFile.exists());

        // query for a variant located after any variants containing percent encoded or UTF8 chars
        // 22	327	.	T	C	666.18	GATK_STANDARD;HARD_TO_VALIDATE	AB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0
        // .00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=filteredInBoth	GT:DP:GQ	1/0:10:62	1/0:37:99
        // 1/0:53:99
        try (final VCFFileReader vcfReader = new VCFFileReader(vcfFileCopy, true);
             final CloseableIterator<VariantContext> vcIt = vcfReader.query(new Interval("22", 327, 327 ))) {
            final List<VariantContext> vcs = new ArrayList(1);
            while (vcIt.hasNext()) {
                vcs.add(vcIt.next());
            }
            Assert.assertEquals(vcs.size(), 1);
            Assert.assertEquals(vcs.get(0).getContig(), "22");
            Assert.assertEquals(vcs.get(0).getStart(), 327);
        }
    }

    //
    // UTF8-specific tests
    //

    @Test
    public void testVCF43ReadUTF8Attributes() {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(TEST_43_UTF8_FILE);
        final List<VCFHeaderLine> headerLines = getIDHeaderLinesWithKey(entireVCF.a, "COMMENT");

        //##COMMENT=<This file has 6 embedded UTF8 chars - 1 right here (ä), 3 in the second ALT line, and 2 in the second vc's set attribute value.>
        Assert.assertEquals(headerLines.get(0).getValue(),
                "This file has 6 embedded UTF8 chars - 1 right here (ä), 3 in the second ALT line, and 2 in the second vc's set attribute value.");
    }

    @Test
    public void testVCF43AltLineWithUTF8Chars() {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(TEST_43_UTF8_FILE);
        final List<VCFHeaderLine> headerLines = getIDHeaderLinesWithKey(entireVCF.a,"ALT");

        //##ALT=<ID=DUP,Description="Duplication", ExtraAltField="äääa">
        final VCFAltHeaderLine altLine = headerLines
                .stream()
                .map(hl -> ((VCFAltHeaderLine) hl))
                .filter(hl -> hl.getID().equals("DUP"))
                .findFirst()
                .get();
        Assert.assertEquals(altLine.getGenericFieldValue("Description"), "Duplication");
        Assert.assertEquals(altLine.getGenericFieldValue("ExtraAltField"), "äääa");
    }

    @Test
    public void testVCF43PercentEncodingWithUTF8() {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(TEST_43_UTF8_FILE);

        //2	327	.	T	C	666.18	GATK_STANDARD;HARD_TO_VALIDATE
        // AB=0.74;AC=3;AF=0.50;AN=6;DB=0;DP=936;Dels=0.00;HRun=3;MQ=34.66;MQ0=728;QD=0.71;SB=-268.74;set=ääa
        // GT:DP:GQ	1/0:10:62.65	1/0:37:99.00	1/0:53:99.00
        final VariantContext vc = entireVCF.b.get(1);
        Assert.assertEquals(vc.getContig(), "2");
        Assert.assertEquals(vc.getStart(), 327);

        Assert.assertEquals(vc.getCommonInfo().getAttribute("set"), "ääa");
    }

    // given a vcf file, extract a header line with the given key and ID, cast to the target
    // header line type (T) via the transformer function
    private static <T extends VCFIDHeaderLine> T getHeaderLineFromTestFile(
            final Path testVCFFile,
            final String key,
            final String ID,
            Function<VCFHeaderLine, T> headerLineCastTransformer)
    {
        final Tuple<VCFHeader, List<VariantContext>> entireVCF = readEntireVCFIntoMemory(testVCFFile);
        final List<VCFHeaderLine> headerLines = getIDHeaderLinesWithKey(entireVCF.a, key);
        return headerLines
                .stream()
                .map(headerLineCastTransformer)
                .filter(hl -> hl.getID().equals(ID))
                .findFirst()
                .get();
    }

    private static List<VCFHeaderLine> getIDHeaderLinesWithKey(final VCFHeader header, final String key) {
        final List<VCFHeaderLine> headerLines =
                header.getMetaDataInInputOrder()
                        .stream()
                        .filter(hl -> hl.getKey().equals(key))
                        .collect(Collectors.toList());
        return headerLines;
    }

}
