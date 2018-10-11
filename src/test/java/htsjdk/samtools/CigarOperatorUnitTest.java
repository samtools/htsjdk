/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** @author Daniel Gomez-Sanchez (magicDGS) */
public class CigarOperatorUnitTest extends HtsjdkTest {

  @DataProvider
  public Object[][] chars() {
    return new Object[][] {
      {'M', CigarOperator.M},
      {'I', CigarOperator.I},
      {'D', CigarOperator.D},
      {'N', CigarOperator.N},
      {'S', CigarOperator.S},
      {'H', CigarOperator.H},
      {'P', CigarOperator.P},
      {'=', CigarOperator.EQ},
      {'X', CigarOperator.X}
    };
  }

  @Test(dataProvider = "chars")
  public void testCharacterToEnum(final char c, final CigarOperator op) throws Exception {
    Assert.assertEquals(CigarOperator.characterToEnum(c), op);
  }

  @Test(dataProvider = "chars")
  public void testEnumToCharacter(final char c, final CigarOperator op) throws Exception {
    Assert.assertEquals(CigarOperator.enumToCharacter(op), c);
  }

  @DataProvider
  public Object[][] illegalChars() {
    return new Object[][] {{'A'}, {'E'}, {'O'}, {'U'}};
  }

  @Test(dataProvider = "illegalChars", expectedExceptions = IllegalArgumentException.class)
  public void testIllegalCharacterToEnum(final char c) throws Exception {
    CigarOperator.characterToEnum(c);
  }

  @DataProvider
  public Object[][] binary() {
    return new Object[][] {
      {0, CigarOperator.M},
      {1, CigarOperator.I},
      {2, CigarOperator.D},
      {3, CigarOperator.N},
      {4, CigarOperator.S},
      {5, CigarOperator.H},
      {6, CigarOperator.P},
      {7, CigarOperator.EQ},
      {8, CigarOperator.X}
    };
  }

  @Test(dataProvider = "binary")
  public void testBinaryToEnum(final int bin, final CigarOperator op) throws Exception {
    Assert.assertEquals(CigarOperator.binaryToEnum(bin), op);
  }

  @Test(dataProvider = "binary")
  public void testEnumToBinary(final int bin, final CigarOperator op) throws Exception {
    Assert.assertEquals(CigarOperator.enumToBinary(op), bin);
  }

  @DataProvider
  public Object[][] illegalBinary() {
    return new Object[][] {{-1}, {9}, {10}};
  }

  @Test(dataProvider = "illegalBinary", expectedExceptions = IllegalArgumentException.class)
  public void testIllegalBinaryToEnum(final int bin) throws Exception {
    CigarOperator.binaryToEnum(bin);
  }

  @DataProvider
  public Object[][] opStatus() {
    return new Object[][] {
      // op, isClipping, isIndel, isSkip, isAlignment, isPadding
      {CigarOperator.M, false, false, false, true, false},
      {CigarOperator.I, false, true, false, false, false},
      {CigarOperator.D, false, true, false, false, false},
      {CigarOperator.N, false, false, true, false, false},
      {CigarOperator.S, true, false, false, false, false},
      {CigarOperator.H, true, false, false, false, false},
      {CigarOperator.P, false, false, false, false, true},
      {CigarOperator.EQ, false, false, false, true, false},
      {CigarOperator.X, false, false, false, true, false}
    };
  }

  @Test(dataProvider = "opStatus")
  public void testIsSetOfOperations(
      final CigarOperator op,
      final boolean isClipping,
      final boolean isIndel,
      final boolean isSkip,
      final boolean isAlignment,
      final boolean isPadding)
      throws Exception {
    Assert.assertEquals(op.isClipping(), isClipping);
    Assert.assertEquals(op.isIndel(), isIndel);
    Assert.assertEquals(op.isIndelOrSkippedRegion(), isIndel || isSkip);
    Assert.assertEquals(op.isAlignment(), isAlignment);
    Assert.assertEquals(op.isPadding(), isPadding);
  }
}
