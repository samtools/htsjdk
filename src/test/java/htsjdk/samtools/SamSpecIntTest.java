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

package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloserUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SamSpecIntTest extends HtsjdkTest {
  private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

  @DataProvider(name = "testSamIntegersTestCases")
  public Object[][] testSamIntegersTestCases() {
    return new Object[][] {{"inttest.sam"}, {"inttest_large_coordinates.sam"}};
  }

  @DataProvider(name = "testBamIntegersTestCases")
  public Object[][] testBamIntegersTestCases() {
    return new Object[][] {{"inttest.bam"}, {"inttest_large_coordinates.bam"}};
  }

  @Test(dataProvider = "testSamIntegersTestCases")
  public void testSamIntegers(final String inputFile) throws IOException {
    final File input = new File(TEST_DATA_DIR, inputFile);
    final SamReader samReader = SamReaderFactory.makeDefault().open(input);

    tryToWriteToSamAndBam(samReader);
  }

  @Test(dataProvider = "testBamIntegersTestCases")
  public void testBamIntegers(final String inputFile) throws IOException {
    final File input = new File(TEST_DATA_DIR, inputFile);
    final SamReader bamReader = SamReaderFactory.makeDefault().open(input);

    tryToWriteToSamAndBam(bamReader);
  }

  private void tryToWriteToSamAndBam(final SamReader reader) throws IOException {
    final File bamOutput = File.createTempFile("test", ".bam");
    final File samOutput = File.createTempFile("test", ".sam");
    final SAMFileWriter samWriter =
        new SAMFileWriterFactory().makeWriter(reader.getFileHeader(), true, samOutput, null);
    final SAMFileWriter bamWriter =
        new SAMFileWriterFactory().makeWriter(reader.getFileHeader(), true, bamOutput, null);

    final List<String> errorMessages = new ArrayList<>();
    for (SAMRecord rec : reader) {
      try {
        samWriter.addAlignment(rec);
        bamWriter.addAlignment(rec);
      } catch (final Throwable e) {
        System.out.println(e.getMessage());
        errorMessages.add(e.getMessage());
      }
    }

    CloserUtil.close(reader);
    samWriter.close();
    bamWriter.close();
    Assert.assertEquals(errorMessages.size(), 0);
    bamOutput.deleteOnExit();
    samOutput.deleteOnExit();
  }
}
