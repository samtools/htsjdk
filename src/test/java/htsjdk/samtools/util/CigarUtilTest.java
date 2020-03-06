/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Basic positive tests for testing cigar string clipping
 *
 * @author Martha Borkan  mborkan@broadinstitute.org
 */
public class CigarUtilTest extends HtsjdkTest {


    @Test(dataProvider = "clipData")
    public void basicTest(final String testName, final int start, final String inputCigar, final boolean negativeStrand,
                          final int clipPosition,
                          final String expectedCigar, final int expectedAdjustedStart, final CigarOperator clippingOperator) throws IOException {
        List<CigarElement> cigar = TextCigarCodec.decode(inputCigar).getCigarElements();
        if (negativeStrand) {
            List<CigarElement> copiedList = new ArrayList<CigarElement>(cigar);
            Collections.reverse(copiedList);
            cigar = copiedList;
        }
        List<CigarElement> result = CigarUtil.clipEndOfRead(clipPosition, cigar, clippingOperator);
        Cigar newCigar = new Cigar(result);
        Cigar oldCigar = new Cigar(cigar);
        if (negativeStrand) {
            Collections.reverse(result);
            newCigar = new Cigar(result);
            int oldLength = oldCigar.getReferenceLength();
            int newLength = newCigar.getReferenceLength();
            int sizeChange = oldLength - newLength;
            //Assert.assertEquals(sizeChange, numClippedBases + adjustment, testName + " sizeChange == numClippedBases");
            Assert.assertEquals(start + sizeChange, expectedAdjustedStart, sizeChange + " " + testName);
            Assert.assertTrue(sizeChange >= 0, "sizeChange >= 0. " + sizeChange);
        }
        Assert.assertEquals(TextCigarCodec.encode(newCigar), expectedCigar, testName);
        if (clippingOperator == CigarOperator.SOFT_CLIP) {
            Assert.assertEquals(newCigar.getReadLength(), oldCigar.getReadLength());
        }
        Assert.assertNull(newCigar.isValid(testName, -1));
    }

    @DataProvider(name = "clipData")
    private Object[][] getCigarClippingTestData() {
        // numClippedBases = (readLength - clipPosition) +1
        return new Object[][]{
                {"Test 1:simple + strand", 100, "50M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 1s:+ strand already clipped", 100, "42M8S", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 2:simple - strand", 100, "50M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},
                {"Test 3:boundary + strand", 100, "42M3D8M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 3s:boundary + strand", 100, "42M3D8S", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 3x:stutter + strand", 100, "42M2D1D8M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 3y:stutter + strand", 100, "42M1D2D8M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 3a:boundary + strand", 100, "42M1D8M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 4:boundary - strand", 98, "10M2D40M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},
                {"Test 5:deletion + strand", 100, "44M1D6M", false, 43, "42M8S", 110, CigarOperator.SOFT_CLIP},
                {"Test 6:deletion - strand", 98, "6M2D44M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},

                {"Test 7:insertion + strand", 100, "42M3I5M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 8:insertion - strand", 102, "8M2I40M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},
                {"Test 9:insertion within + strand", 100, "44M2I4M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 9x:insertion stutter within + strand", 100, "44M2I2I2M", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 10:insertion within - strand", 100, "3M3I44M", true, 41, "10S40M", 107, CigarOperator.SOFT_CLIP},
                {"Test 11:insertion straddling + strand", 100, "40M4I6M", false, 43, "40M10S", 100, CigarOperator.SOFT_CLIP},
                {"Test 11s:insertion straddling + strand", 100, "40M4I6S", false, 43, "40M10S", 100, CigarOperator.SOFT_CLIP},
                {"Test 11a:insertion straddling + strand", 100, "40M2I8M", false, 43, "40M10S", 100, CigarOperator.SOFT_CLIP},
                {"Test 12:insertion straddling - strand", 104, "4M4I42M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},
                {"Test 12a:insertion straddling - strand", 102, "8M2I40M", true, 41, "10S40M", 110, CigarOperator.SOFT_CLIP},

                {"Test 13:deletion before clip + strand", 100, "10M5D35M", false, 38, "10M5D27M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 14:deletion before clip - strand", 100, "35M5D10M", true, 36, "10S25M5D10M", 110, CigarOperator.SOFT_CLIP},
                {"Test 15:insertion before clip + strand", 100, "10M5I35M", false, 43, "10M5I27M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 16:insertion before clip - strand", 100, "16M5I29M", true, 41, "10S6M5I29M", 110, CigarOperator.SOFT_CLIP},

                {"Test 17:second, earlier clip", 100, "48M2S", false, 43, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 17s:second, earlier clip", 100, "2S48M", true, 43, "8S42M", 106, CigarOperator.SOFT_CLIP},
                {"Test 18:second, later clip", 100, "42M8S", false, 48, "42M8S", 100, CigarOperator.SOFT_CLIP},
                {"Test 18s:second, later clip", 100, "8S42M", true, 48, "8S42M", 100, CigarOperator.SOFT_CLIP},

                {"Test 19: read already ends in hard clip", 100, "48M2H", false,43,"42M6S2H", 100, CigarOperator.SOFT_CLIP},
                {"Test 19s: read already ends in hard clip", 102, "2H48M", true,43,"2H6S42M", 108, CigarOperator.SOFT_CLIP},

                {"Test hard-clipping 1:simple + strand", 100, "50M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 1s:+ strand already clipped", 100, "42M8S", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 2:simple - strand", 100, "50M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 3:boundary + strand", 100, "42M3D8M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 3s:boundary + strand", 100, "42M3D8S", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 3x:stutter + strand", 100, "42M2D1D8M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 3y:stutter + strand", 100, "42M1D2D8M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 3a:boundary + strand", 100, "42M1D8M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 4:boundary - strand", 98, "10M2D40M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 5:deletion + strand", 100, "44M1D6M", false, 43, "42M8H", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 6:deletion - strand", 98, "6M2D44M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},

                {"Test hard-clipping 7:insertion + strand", 100, "42M3I5M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 8:insertion - strand", 102, "8M2I40M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 9:insertion within + strand", 100, "44M2I4M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 9x:insertion stutter within + strand", 100, "44M2I2I2M", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 10:insertion within - strand", 100, "3M3I44M", true, 41, "10H40M", 107, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 11:insertion straddling + strand", 100, "40M4I6M", false, 43, "40M2I8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 11s:insertion straddling + strand", 100, "40M4I6S", false, 43, "40M2I8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 11a:insertion straddling + strand", 100, "40M2I8M", false, 43, "40M2I8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 12:insertion straddling - strand", 104, "4M4I42M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 12a:insertion straddling - strand", 102, "8M2I40M", true, 41, "10H40M", 110, CigarOperator.HARD_CLIP},

                {"Test hard-clipping 13:deletion before clip + strand", 100, "10M5D35M", false, 38, "10M5D27M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 14:deletion before clip - strand", 100, "35M5D10M", true, 36, "10H25M5D10M", 110, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 15:insertion before clip + strand", 100, "10M5I35M", false, 43, "10M5I27M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 16:insertion before clip - strand", 100, "16M5I29M", true, 41, "10H6M5I29M", 110, CigarOperator.HARD_CLIP},

                {"Test hard-clipping 17:second, earlier clip", 100, "48M2S", false, 43, "42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 17s:second, earlier clip", 100, "2S48M", true, 43, "8H42M", 106, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 18:second, later clip", 100, "42M8S", false, 48, "42M5S3H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 18s:second, later clip", 100, "8S42M", true, 48, "3H5S42M", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 17:second, earlier clip", 100, "42M8S", false, 49, "42M6S2H", 100, CigarOperator.HARD_CLIP},

                {"Test hard-clipping 19: read already ends in hard clip", 100, "48M2H", false,43,"42M8H", 100, CigarOperator.HARD_CLIP},
                {"Test hard-clipping 19s: read already ends in hard clip", 102, "2H48M", true,43,"8H42M", 108, CigarOperator.HARD_CLIP},

        };
    }

    @Test(dataProvider = "addData")
    public void addingSoftClippedBasesTest(final String testName, final String cigar, final boolean negativeStrand,
                                           final int threePrimeEnd, final int fivePrimeEnd, final String expectedCigar) throws IOException {

        Assert.assertEquals(CigarUtil.addSoftClippedBasesToEndsOfCigar(TextCigarCodec.decode(cigar), negativeStrand,
                threePrimeEnd, fivePrimeEnd).toString(), expectedCigar, testName);
    }

    @DataProvider(name = "addData")
    private Object[][] getCigarAddingTestData() {
        // numClippedBases = (readLength - clipPosition) +1
        return new Object[][]{
                {"Add to 5' end only, +", "36M", false, 0, 5, "5S36M"},
                {"Add to 5' end only, -", "30M1I5M", true, 0, 5, "30M1I5M5S"},
                {"Add to 3' end only, +", "26M", false, 3, 0, "26M3S"},
                {"Add to 3' end only, -", "19M3D7M", true, 3, 0, "3S19M3D7M"},
                {"Add to 5' end already soft-clipped, +", "6S20M", false, 0, 5, "11S20M"},
                {"Add to 5' end already soft-clipped, -", "28M4S", true, 0, 5, "28M9S"},
                {"Add to 3' end already soft-clipped, +", "15M5I10M2S", false, 7, 0, "15M5I10M9S"},
                {"Add to 3' end already soft-clipped, -", "2S34M", true, 6, 0, "8S34M"},
                {"Add to 5' and 3' ends, no merging, +", "36M", false, 15, 30, "30S36M15S"},
                {"Add to 5' and 3' ends, no merging, -", "36M", true, 15, 30, "15S36M30S"},
                {"Add to 5' and 3' ends, merging 5' end, +", "5S31M", false, 15, 30, "35S31M15S"},
                {"Add to 5' and 3' ends, merging 5' end, -", "31M5S", true, 15, 30, "15S31M35S"},
                {"Add to 5' and 3' ends, merging 3' end, +", "20M6S", false, 10, 12, "12S20M16S"},
                {"Add to 5' and 3' ends, merging 3' end, -", "6S25M", true, 10, 12, "16S25M12S"},
                {"Add to 5' and 3' ends, merging both ends, +", "3S31M2S", false, 10, 15, "18S31M12S"},
                {"Add to 5' and 3' ends, merging both ends, -", "2S26M8S", true, 10, 12, "12S26M20S"},

        };
    }

    public SAMRecord createTestSamRec(final String readString, final byte[] baseQualities, final String cigarString, final int startPos, final boolean negativeStrand) {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadString(readString);
        rec.setCigarString(cigarString);
        rec.setReadNegativeStrandFlag(negativeStrand);

        rec.setBaseQualities(baseQualities);
        rec.setAlignmentStart(startPos);

        return (rec);
    }

    @DataProvider(name = "hardClippingData")
    private Object[][] getHardClippingTestData() {

        final byte[] baseQualities1To8 = new byte[]{(byte)31, (byte)32, (byte)33, (byte)34, (byte)35, (byte)36, (byte)37, (byte)38};
        final byte[] baseQualities1To6 = Arrays.copyOf(baseQualities1To8, 6);
        final byte[] baseQualities3To8 = Arrays.copyOfRange(baseQualities1To8, 2, 8);
        final byte[] baseQualities1To4 = Arrays.copyOf(baseQualities1To8, 4);
        final byte[] baseQualities5To8 = Arrays.copyOfRange(baseQualities1To8, 4, 8);


        return new Object[][]{
                {"Hard clip 2 bases , +", "ATGCAGAG", baseQualities1To8, "8M", 100, false, 7, "ATGCAG", baseQualities1To6, "6M2H", 100, 105},
                {"Hard clip 2 bases , -", "ATGCAGAG", baseQualities1To8, "8M", 100, true, 7, "GCAGAG", baseQualities3To8,  "2H6M", 102, 107},

                {"Hard clip 2 bases existing Hard Clip, +", "ATGCAG", baseQualities1To6, "6M2H", 100, false, 5, "ATGC", baseQualities1To4, "4M4H", 100, 103},
                {"Hard clip 2 bases existing Hard Clip, -", "ATGCAG", baseQualities3To8, "2H6M", 102, true, 5, "GCAG", baseQualities5To8, "4H4M", 104, 107}

        };
    }

    @Test(dataProvider = "hardClippingData")
    public void hardClippingTest(final String testName, final String initialReadString, final byte[] initialBaseQualities, final String initialCigar, final int initialStartPosition, final boolean negativeStrand,
                                 final int clipFrom, final String expectedReadString, final byte[] expectedBaseQualities, final String expectedCigar, final int expectedStartPosition, final int expectedEndPosition) throws IOException {

        final SAMRecord rec = createTestSamRec(initialReadString, initialBaseQualities, initialCigar, initialStartPosition, negativeStrand);

        CigarUtil.clip3PrimeEndOfRead(rec, clipFrom, CigarOperator.HARD_CLIP);

        Assert.assertEquals(rec.getCigarString(), expectedCigar, testName);
        Assert.assertEquals(rec.getReadString(), expectedReadString, testName);

        Assert.assertEquals(rec.getBaseQualities(), expectedBaseQualities);

        Assert.assertEquals(rec.getAlignmentStart(), expectedStartPosition);
        Assert.assertEquals(rec.getAlignmentEnd(), expectedEndPosition);

    }

    @DataProvider(name = "tagInvalidationData")
    private Object[][] getTagInvalidationData() {
        return new Object[][] {
                {"8M", false, 7, CigarOperator.HARD_CLIP, true},
                {"8M", true, 7, CigarOperator.HARD_CLIP, true},
                {"8M", false, 7, CigarOperator.SOFT_CLIP, true},
                {"8M", true, 7, CigarOperator.SOFT_CLIP, true},

                {"6M2S", false, 7, CigarOperator.HARD_CLIP, false},
                {"6M2S", true, 7, CigarOperator.HARD_CLIP, true},
                {"2S6M", false, 7, CigarOperator.HARD_CLIP, true},
                {"2S6M", true, 7, CigarOperator.HARD_CLIP, false},

                {"6M2S", false, 7, CigarOperator.SOFT_CLIP, false},
                {"6M2S", true, 7, CigarOperator.SOFT_CLIP, true},
                {"2S6M", false, 7, CigarOperator.SOFT_CLIP, true},
                {"2S6M", true, 7, CigarOperator.SOFT_CLIP, false},

                {"6M2S", false, 6, CigarOperator.HARD_CLIP, true},
                {"6M2S", true, 6, CigarOperator.HARD_CLIP, true},
                {"2S6M", false, 6, CigarOperator.HARD_CLIP, true},
                {"2S6M", true, 6, CigarOperator.HARD_CLIP, true},

                {"6M2S", false, 6, CigarOperator.SOFT_CLIP, true},
                {"6M2S", true, 6, CigarOperator.SOFT_CLIP, true},
                {"2S6M", false, 6, CigarOperator.SOFT_CLIP, true},
                {"2S6M", true, 6, CigarOperator.SOFT_CLIP, true},

                {"8M2H", false, 7, CigarOperator.HARD_CLIP, true},
                {"2H8M", true, 7, CigarOperator.HARD_CLIP, true},
                {"8M2H", false, 7, CigarOperator.SOFT_CLIP, true},
                {"2H8M", true, 7, CigarOperator.SOFT_CLIP, true}
        };
    }

    @Test(dataProvider = "tagInvalidationData")
    public void testTagsInvalidation(final String initialCigarString, final boolean negativeStrand, final int clipFrom, final CigarOperator clippingOperator, final boolean expectInvalidated) {
        final byte[] baseQualities1To8 = new byte[]{(byte)31, (byte)32, (byte)33, (byte)34, (byte)35, (byte)36, (byte)37, (byte)38};
        final SAMRecord rec = createTestSamRec("ACTGACTG", baseQualities1To8, initialCigarString, 100, negativeStrand);
        rec.setAttribute(SAMTag.NM.name(), 0);
        rec.setAttribute(SAMTag.MD.name(), 0);
        rec.setAttribute(SAMTag.UQ.name(), 0);

        CigarUtil.clip3PrimeEndOfRead(rec, clipFrom, clippingOperator);

        if (expectInvalidated) {
            Assert.assertNull(rec.getAttribute(SAMTag.NM.name()));
            Assert.assertNull(rec.getAttribute(SAMTag.MD.name()));
            Assert.assertNull(rec.getAttribute(SAMTag.UQ.name()));
        } else {
            Assert.assertEquals(rec.getAttribute(SAMTag.NM.name()), 0);
            Assert.assertEquals(rec.getAttribute(SAMTag.MD.name()), 0);
            Assert.assertEquals(rec.getAttribute(SAMTag.UQ.name()), 0);
        }

    }
}
