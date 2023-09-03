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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for VCFCompoundHeaderLine.
 *
 * NOTE: This class uses VCFInfoHeaderLine instances to test shared VCFCompoundHeaderLine functionality since
 * VCFCompoundHeaderLine abstract.
 */
public class VCFCompoundHeaderLineUnitTest extends VariantBaseTest {

    @DataProvider (name = "badOrMissingAttributes")
    public Object[][] getMissingAttributes() {
        return new Object[][] {
                {"<ID=FOO,Number=A,Description=\"foo\">"},                  // no Type
                {"<ID=FOO,Number=A,Description=\"foo\">"},                  // no Type
                {"<ID=FOO,Type=Float,Description=\"foo\",Version=3>"},      // no Number
                {"<ID=FOO,Number=unknown,Type=Float,Description=\"foo\">"}, // bogus Type
                {"<ID=FOO,Number=A,Type=unknown,Description=\"foo\">"},     // bogus Number
        };
    }

    @Test(dataProvider= "badOrMissingAttributes", expectedExceptions=TribbleException.class)
    public void testBadOrMissingAttributes(final String lineString) {
        new VCFInfoHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION);
    }

    @DataProvider (name = "acceptedAttributes")
    public Object[][] getAcceptedAttributes() {
        return new Object[][] {
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\">", "Description", "foo"},
                //next two cases from https://github.com/samtools/htsjdk/issues/517
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\",Version=3>", "Version", "3"},
                {"<ID=FOO,Number=R,Type=Float,Description=\"foo\",Source=\"mySource\">", "Source", "mySource"},
        };
    }

    @Test(dataProvider= "acceptedAttributes")
    public void testAcceptedAttributes(final String lineString, final String attribute, final String expectedValue) {
        final VCFCompoundHeaderLine headerline = new VCFInfoHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(headerline.getGenericFieldValue(attribute), expectedValue);
    }

    @DataProvider (name = "invalidIDs")
    public Object[][] getInvalidLines() {
        return new Object[][] {
            // ID cannot start with number
            {"<ID=1A,Number=A,Type=Integer,Description=\"foo\">"},
            // ID cannot start with '.''
            {"<ID=.A,Number=A,Type=Integer,Description=\"foo\">"},
            // Test that IDs with the special thousand genomes key as a prefix are rejected
            // The thousand genomes key is only accepted for VCFInfoHeaderLine and is tested in VCFInfoHeaderLineUnitTest
            {"<ID=1000GA,Number=A,Type=Integer,Description=\"foo\">"},
            // Contains invalid character '&'
            {"<ID=A&,Number=A,Type=Integer,Description=\"foo\">"},
        };
    }

    @Test(dataProvider = "invalidIDs", expectedExceptions = TribbleException.class)
    public void testGetValidationError(final String lineString) {
        new VCFInfoHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION);
    }

    @DataProvider (name = "headerLineTypes")
    public Object[][] getHeaderLineTypes() {
        return new Object[][] {
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeaderLineType.Float},
                {"<ID=FOO,Number=A,Type=Integer,Description=\"foo\">", VCFHeaderLineType.Integer},
                {"<ID=FOO,Number=A,Type=String,Description=\"foo\">", VCFHeaderLineType.String},
                {"<ID=FOO,Number=A,Type=Character,Description=\"foo\">", VCFHeaderLineType.Character},
                // Number must be 0 for flag type
                {"<ID=FOO,Number=0,Type=Flag,Description=\"foo\">", VCFHeaderLineType.Flag},
        };
    }

    @Test(dataProvider = "headerLineTypes")
    public void testGetType(final String lineString, final VCFHeaderLineType expectedType) {
        final VCFCompoundHeaderLine headerline = new VCFInfoHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(headerline.getType(), expectedType);
    }

    @DataProvider (name = "headerLineCountTypes")
    public Object[][] getLineCountTypes() {
        return new Object[][] {
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeaderLineCount.A},
                {"<ID=FOO,Number=R,Type=Integer,Description=\"foo\">", VCFHeaderLineCount.R},
                {"<ID=FOO,Number=G,Type=String,Description=\"foo\">", VCFHeaderLineCount.G},
                {"<ID=FOO,Number=127,Type=Character,Description=\"foo\">", VCFHeaderLineCount.INTEGER},
                {"<ID=FOO,Number=.,Type=Integer,Description=\"foo\">", VCFHeaderLineCount.UNBOUNDED},
        };
    }

    @Test(dataProvider= "headerLineCountTypes")
    public void testGetLineCountType(final String lineString, final VCFHeaderLineCount expectedCountType) {
        final VCFCompoundHeaderLine headerline = new VCFInfoHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(headerline.getCountType(), expectedCountType);
        Assert.assertEquals(headerline.isFixedCount(), expectedCountType == VCFHeaderLineCount.INTEGER);
    }

    @Test(expectedExceptions=TribbleException.class)
    public void testRejectIntegerTypeWithNegativeCount() {
        new VCFInfoHeaderLine("<ID=FOO,Number=-1,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION);
    }

    @Test
    public void testRepairFlagTypeWithNegativeCount() {
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("<ID=FOO,Number=-1,Type=Flag,Description=\"foo\">",
                VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(infoLine.getCount(), 0);
    }

    @DataProvider (name = "equalsData")
    public Object[][] getEqualsData() {
        return new Object[][] {
                //pos
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\">",
                        "<ID=FOO,Number=A,Type=Float,Description=\"foo\">", true},
                {"<ID=FOO,Number=R,Type=Integer,Description=\"foo\">",
                        "<ID=FOO,Number=R,Type=Integer,Description=\"foo\">", true},
                {"<ID=FOO,Number=G,Type=String,Description=\"foo\">",
                        "<ID=FOO,Number=G,Type=String,Description=\"foo\">", true},
                {"<ID=FOO,Number=127,Type=Character,Description=\"foo\">",
                        "<ID=FOO,Number=127,Type=Character,Description=\"foo\">", true},
                {"<ID=FOO,Number=.,Type=Integer,Description=\"foo\",Source=source>",
                        "<ID=FOO,Number=.,Type=Integer,Description=\"foo\",Source=source>", true},

                //neg
                {"<ID=FOO1,Number=A,Type=Float,Description=\"foo\">",
                 "<ID=FOO2,Number=A,Type=Float,Description=\"foo\">", false},      // different ID
                {"<ID=FOO,Number=R,Type=Integer,Description=\"foo\">",
                "<ID=FOO,Number=R,Type=Float,Description=\"foo\">", false},        // different Type
                {"<ID=FOO,Number=A,Type=Float,Description=\"foo\">",
                 "<ID=FOO,Number=R,Type=Float,Description=\"foo\">", false},       // different Number
                {"<ID=FOO,Number=127,Type=Character,Description=\"foo\">",
                 "<ID=FOO,Number=119,Type=Character,Description=\"foo\">", false}, // different integer Number
                {"<ID=FOO,Number=G,Type=String,Description=\"foo\">",
                 "<ID=FOO,Number=G,Type=String,Description=\"foobar\">", false},   // different description
                {"<ID=FOO,Number=.,Type=Integer,Description=\"foo\",Source=source>",
                 "<ID=FOO,Number=.,Type=Integer,Description=\"foo\",", false},     // different extra attributes
        };
    }

    @Test(dataProvider= "equalsData")
    public void testEquals(final String line1, final String line2, final boolean expectedEquals) {
        final VCFCompoundHeaderLine headerLine1 = new VCFInfoHeaderLine(line1, VCFHeader.DEFAULT_VCF_VERSION);
        final VCFCompoundHeaderLine headerLine2 = new VCFInfoHeaderLine(line2, VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(headerLine1.equals(headerLine2), expectedEquals);
    }

    @DataProvider(name = "mergeCompatibleInfoLines")
    public Object[][] getMergeCompatibleInfoLines() {
        return new Object[][]{
                {
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),  // merged result, promote to float
                },
                {
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Float,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION)  // merged result, promote to float
                },
                {
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=G,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=.,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION)  // merged result, resolve as new unbounded
                },
        };
    }

    @Test(dataProvider = "mergeCompatibleInfoLines")
    public void testMergeIncompatibleInfoLines(final VCFInfoHeaderLine line1, final VCFInfoHeaderLine line2, final VCFInfoHeaderLine expectedLine) {
        VCFCompoundHeaderLine mergedLine = VCFCompoundHeaderLine.getMergedCompoundHeaderLine(
                line1,
                line2,
                new VCFHeaderMerger.HeaderMergeConflictWarnings(false),
                (l1, l2) -> new VCFInfoHeaderLine(
                        l1.getID(),
                        VCFHeaderLineCount.UNBOUNDED,
                        l1.getType(),
                        l1.getDescription())
        );
        Assert.assertEquals(mergedLine, expectedLine);
    }

    @DataProvider(name = "mergeIncompatibleInfoLines")
    public Object[][] getMergeIncompatibleInfoLines() {
        return new Object[][]{
                {
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=Integer,Description=\"foo\">",VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=0,Type=Flag,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                },
                {
                        new VCFInfoHeaderLine("<ID=FOO,Number=A,Type=String,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("<ID=FOO,Number=37,Type=Integer,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION),
                },
        };
    }

    @Test(dataProvider = "mergeIncompatibleInfoLines", expectedExceptions=TribbleException.class)
    public void testMergeIncompatibleInfoLines(final VCFInfoHeaderLine line1, final VCFInfoHeaderLine line2) {
        VCFCompoundHeaderLine.getMergedCompoundHeaderLine(
                line1,
                line2,
                new VCFHeaderMerger.HeaderMergeConflictWarnings(false),
                (l1, l2) -> { throw new IllegalArgumentException("lambda should never execute - this exception should never be thrown"); }
        );
    }

    @Test
    public void testEncodeWithUnescapedQuotes() {

        VCFFilterHeaderLine unescapedFilterLine = new VCFFilterHeaderLine(
                "aFilter",
                "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");

        final String encodedAttributes = unescapedFilterLine.toStringEncoding();
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "FILTER=<ID=aFilter,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

    @Test
    public void testEncodeWithEscapedQuotes() {

        VCFFilterHeaderLine escapedFilterLine = new VCFFilterHeaderLine("aFilter", "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");
        final String encodedAttributes = escapedFilterLine.toStringEncoding();
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "FILTER=<ID=aFilter,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

}
