/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.variant.utils;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.File;

/**
 * @author farjoun on 4/9/14.
 */
public class SAMSequenceDictionaryExtractorTest extends HtsjdkTest {
    String path = "src/test/resources/htsjdk/variant/utils/SamSequenceDictionaryExtractor/";

    @DataProvider(name = "testExtractDictionaries")
    public Object[][] dictionaries() {
        return new Object[][]{
                new Object[]{"test1_comp.interval_list", "test1.dict"},
                new Object[]{"test1.vcf", "test1.dict"},
                new Object[]{"test1.dict", "test1.dict"},
                new Object[]{"empty.interval_list", "test1.dict"},
                new Object[]{"Homo_sapiens_assembly18.trimmed.fasta", "Homo_sapiens_assembly18.trimmed.dict"},
                new Object[]{"test2_comp.interval_list", "Homo_sapiens_assembly18.trimmed.dict"},
                new Object[]{"ScreenSamReads.100.input.sam", "test3_comp.interval_list"},
        };
    }

    @Test(dataProvider = "testExtractDictionaries")
    public void testExtractDictionary(final String dictSource, final String dictExpected) throws Exception {
        final File dictSourceFile = new File(path, dictSource);
        final File dictExpectedFile = new File(path, dictExpected);
        final SAMSequenceDictionary dict1 = SAMSequenceDictionaryExtractor.extractDictionary(dictSourceFile);
        final SAMSequenceDictionary dict2 = SAMSequenceDictionaryExtractor.extractDictionary(dictExpectedFile);

        Assert.assertTrue(SequenceUtil.areSequenceDictionariesEqual(dict1,
                dict2));
        Assert.assertTrue(dict1.md5().equals(dict2.md5()));
    }
}
