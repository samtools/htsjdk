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
}
