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

package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * User: ebanks
 * Date: Apr 2, 2014
 */
public class VCFCompoundHeaderLineUnitTest extends VariantBaseTest {

    @Test
    public void supportsVersionFields() {
        final String line = "<ID=FOO,Number=1,Type=Float,Description=\"foo\",Version=3>";
        new VCFInfoHeaderLine(line, VCFHeaderVersion.VCF4_2);
        // if we don't support version fields then we should fail before we ever get here
        Assert.assertTrue(true);
    }

    @Test
    public void unknownAttributesSurviveARoundTrip() {
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine(
                "<ID=FOO,Number=1,Type=Float,Description=\"foo\",IDX=7,Custom=\"a b\">", VCFHeaderVersion.VCF4_2);
        Assert.assertEquals(line.getGenericFieldValue("IDX"), "7");
        Assert.assertEquals(line.getGenericFieldValue("Custom"), "a b");
        Assert.assertEquals(line.getGenericFieldValue("ID"), "FOO");
        Assert.assertEquals(line.getGenericFieldValue("Number"), "1");
        Assert.assertNull(line.getGenericFieldValue("Missing"));
        Assert.assertEquals(
                line.toString(), "INFO=<ID=FOO,Number=1,Type=Float,Description=\"foo\",IDX=7,Custom=\"a b\">");
        Assert.assertEquals(new VCFInfoHeaderLine(line.toString().substring(5), VCFHeaderVersion.VCF4_2), line);
    }

    @Test
    public void attributesMayComeInAnyOrder() {
        final VCFFormatHeaderLine line =
                new VCFFormatHeaderLine("<Description=\"depth\",Type=Integer,ID=DP,Number=1>", VCFHeaderVersion.VCF4_4);
        Assert.assertEquals(line.getID(), "DP");
        Assert.assertEquals(line.getType(), VCFHeaderLineType.Integer);
        Assert.assertEquals(line.getCount(), 1);
        Assert.assertEquals(line.getDescription(), "depth");
        // written in the conventional order regardless
        Assert.assertEquals(line.toString(), "FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"depth\">");
    }

    @Test
    public void sourceAndVersionAreReadForEveryVersion() {
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine(
                "<ID=FOO,Number=1,Type=Float,Description=\"foo\",Source=\"dbsnp\",Version=\"138\">",
                VCFHeaderVersion.VCF4_1);
        Assert.assertEquals(line.getSource(), "dbsnp");
        Assert.assertEquals(line.getVersion(), "138");
    }

    @Test
    public void linesDifferingOnlyInUnknownAttributesAreNotEqual() {
        final VCFInfoHeaderLine a = new VCFInfoHeaderLine(
                "<ID=FOO,Number=1,Type=Float,Description=\"foo\",IDX=7>", VCFHeaderVersion.VCF4_2);
        final VCFInfoHeaderLine b = new VCFInfoHeaderLine(
                "<ID=FOO,Number=1,Type=Float,Description=\"foo\",IDX=8>", VCFHeaderVersion.VCF4_2);
        final VCFInfoHeaderLine c = new VCFInfoHeaderLine(
                "<ID=FOO,Number=1,Type=Float,Description=\"foo\",IDX=7>", VCFHeaderVersion.VCF4_2);
        Assert.assertNotEquals(a, b);
        Assert.assertEquals(a, c);
        Assert.assertEquals(a.hashCode(), c.hashCode());
    }

    @Test
    public void programmaticLinesHaveNoUnknownAttributes() {
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine("FOO", 1, VCFHeaderLineType.Float, "foo");
        Assert.assertEquals(
                line.getGenericFields().keySet().toArray(), new String[] {"ID", "Number", "Type", "Description"});
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void missingNumberIsRejected() {
        new VCFInfoHeaderLine("<ID=FOO,Type=Float,Description=\"foo\">", VCFHeaderVersion.VCF4_2);
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void missingTypeIsRejected() {
        new VCFFormatHeaderLine("<ID=FOO,Number=1,Description=\"foo\">", VCFHeaderVersion.VCF4_2);
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void missingIdIsRejected() {
        new VCFInfoHeaderLine("<Number=1,Type=Float,Description=\"foo\">", VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void missingDescriptionIsTolerated() {
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine("<ID=FOO,Number=1,Type=Float>", VCFHeaderVersion.VCF4_2);
        Assert.assertEquals(line.getID(), "FOO");
        Assert.assertNotNull(line.getDescription());
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void anEqualsSignInAnIdReadFromAFileIsAnInvalidHeader() {
        new VCFInfoHeaderLine("<ID=a=b,Number=1,Type=String,Description=\"x\">", VCFHeaderVersion.VCF4_2);
    }

    // F10
    @Test
    public void linesDifferingOnlyInSourceOrVersionAreNotEqual() {
        final String line = "<ID=FOO,Number=1,Type=Float,Description=\"foo\"";
        final VCFInfoHeaderLine plain = new VCFInfoHeaderLine(line + ">", VCFHeaderVersion.VCF4_2);
        final VCFInfoHeaderLine dbsnp138 =
                new VCFInfoHeaderLine(line + ",Source=\"dbsnp\",Version=\"138\">", VCFHeaderVersion.VCF4_2);
        final VCFInfoHeaderLine dbsnp151 =
                new VCFInfoHeaderLine(line + ",Source=\"dbsnp\",Version=\"151\">", VCFHeaderVersion.VCF4_2);
        final VCFInfoHeaderLine dbsnp151Again =
                new VCFInfoHeaderLine(line + ",Source=\"dbsnp\",Version=\"151\">", VCFHeaderVersion.VCF4_2);
        Assert.assertNotEquals(plain, dbsnp138);
        Assert.assertNotEquals(dbsnp138, dbsnp151);
        Assert.assertEquals(dbsnp151, dbsnp151Again);
        Assert.assertEquals(dbsnp151.hashCode(), dbsnp151Again.hashCode());
    }

    // Number= codes

    private static VCFFormatHeaderLine formatLineWithNumber(final String number) {
        return new VCFFormatHeaderLine(
                "<ID=XX,Number=" + number + ",Type=Integer,Description=\"x\">", VCFHeaderVersion.VCF4_5);
    }

    @Test
    public void numberPParsesAndIsWrittenBack() {
        final VCFFormatHeaderLine line = formatLineWithNumber("P");
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.P);
        Assert.assertEquals(line.toString(), "FORMAT=<ID=XX,Number=P,Type=Integer,Description=\"x\">");
    }

    @Test
    public void numberLAParsesAndIsWrittenBack() {
        final VCFFormatHeaderLine line = formatLineWithNumber("LA");
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.LA);
        Assert.assertEquals(line.toString(), "FORMAT=<ID=XX,Number=LA,Type=Integer,Description=\"x\">");
    }

    @Test
    public void numberLRParsesAndIsWrittenBack() {
        final VCFFormatHeaderLine line = formatLineWithNumber("LR");
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.LR);
        Assert.assertEquals(line.toString(), "FORMAT=<ID=XX,Number=LR,Type=Integer,Description=\"x\">");
    }

    @Test
    public void numberLGParsesAndIsWrittenBack() {
        final VCFFormatHeaderLine line = formatLineWithNumber("LG");
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.LG);
        Assert.assertEquals(line.toString(), "FORMAT=<ID=XX,Number=LG,Type=Integer,Description=\"x\">");
    }

    @Test
    public void numberMParsesAndIsWrittenBack() {
        final VCFFormatHeaderLine line = formatLineWithNumber("M");
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.M);
        Assert.assertEquals(line.toString(), "FORMAT=<ID=XX,Number=M,Type=Integer,Description=\"x\">");
    }

    @Test
    public void aLineBuiltFromACountWritesItsCode() {
        final VCFFormatHeaderLine line =
                new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LR, VCFHeaderLineType.Integer, "local depths");
        Assert.assertEquals(line.toString(), "FORMAT=<ID=LAD,Number=LR,Type=Integer,Description=\"local depths\">");
    }

    @Test
    public void numberCodesAreReadWhateverTheHeaderVersion() {
        final VCFFormatHeaderLine line =
                new VCFFormatHeaderLine("<ID=XX,Number=LR,Type=Integer,Description=\"x\">", VCFHeaderVersion.VCF4_1);
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.LR);
    }

    @Test
    public void aSampleDependentCodeOnAnInfoLineIsKeptAsDeclared() {
        final VCFInfoHeaderLine line =
                new VCFInfoHeaderLine("<ID=XX,Number=P,Type=Integer,Description=\"x\">", VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.P);
        Assert.assertEquals(line.toString(), "INFO=<ID=XX,Number=P,Type=Integer,Description=\"x\">");
    }

    @Test
    public void aSampleDependentCountCannotBeWorkedOutFromTheRecord() {
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"), Allele.create("G"));
        final VariantContext vc = new VariantContextBuilder("test", "1", 100, 100, alleles).make();
        Assert.assertEquals(formatLineWithNumber("P").getCount(vc), -1);
        Assert.assertEquals(formatLineWithNumber("LA").getCount(vc), -1);
        Assert.assertEquals(formatLineWithNumber("LR").getCount(vc), -1);
        Assert.assertEquals(formatLineWithNumber("LG").getCount(vc), -1);
        Assert.assertEquals(formatLineWithNumber("M").getCount(vc), -1);
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void aNumberThatIsNeitherACodeNorAnIntegerIsAnInvalidHeader() {
        formatLineWithNumber("Q");
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void numberCodesAreCaseSensitive() {
        formatLineWithNumber("lr");
    }

    @Test
    public void minusOneMeansUnboundedBeforeVcf4() {
        // header lines before VCF 4.0 give their attributes by position, without tags
        final VCFFormatHeaderLine line = new VCFFormatHeaderLine("XX,-1,Integer,\"x\"", VCFHeaderVersion.VCF3_3);
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.UNBOUNDED);
    }

    @Test
    public void aDotAlsoMeansUnboundedBeforeVcf4() {
        final VCFFormatHeaderLine line = new VCFFormatHeaderLine("XX,.,Integer,\"x\"", VCFHeaderVersion.VCF3_3);
        Assert.assertEquals(line.getCountType(), VCFHeaderLineCount.UNBOUNDED);
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void minusOneIsRejectedFromVcf4On() {
        new VCFFormatHeaderLine("<ID=XX,Number=-1,Type=Integer,Description=\"x\">", VCFHeaderVersion.VCF4_0);
    }

    @Test
    public void aLinesMinimumVersionIsItsCountsMinimumVersion() {
        Assert.assertEquals(formatLineWithNumber("1").minimumVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(formatLineWithNumber("A").minimumVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(formatLineWithNumber("R").minimumVersion(), VCFHeaderVersion.VCF4_2);
        Assert.assertEquals(formatLineWithNumber("P").minimumVersion(), VCFHeaderVersion.VCF4_4);
        Assert.assertEquals(formatLineWithNumber("LA").minimumVersion(), VCFHeaderVersion.VCF4_5);
        final VCFInfoHeaderLine info =
                new VCFInfoHeaderLine("XX", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "x");
        Assert.assertEquals(info.minimumVersion(), VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void aLineKnowsWhetherItIsInfoOrFormat() {
        Assert.assertEquals(
                formatLineWithNumber("1").getLineType(), VCFCompoundHeaderLine.SupportedHeaderLineType.FORMAT);
        final VCFInfoHeaderLine info = new VCFInfoHeaderLine("XX", 1, VCFHeaderLineType.Integer, "x");
        Assert.assertEquals(info.getLineType(), VCFCompoundHeaderLine.SupportedHeaderLineType.INFO);
    }
}
