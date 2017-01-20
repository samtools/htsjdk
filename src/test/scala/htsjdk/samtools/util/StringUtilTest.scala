/*
 * The MIT License
 *
 * Copyright (c) 2017 The Broad Institute
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
package htsjdk.samtools.util

import htsjdk.UnitSpec

class StringUtilTest extends UnitSpec {
  "StringUtil.split" should "behave like String.split(char)" in {
    Seq("A:BB:C", "A:BB", "A:BB:", "A:BB:C:DDD", "A:", "A", "A:BB:C").foreach { s =>
      val arr = new Array[String](10)
      val count = StringUtil.split(s, arr, ':')
      arr.take(count) shouldBe s.split(':')
    }
  }

  "StringUtil.splitConcatenateExcessTokens" should "behave like String.split(regex, limit)" in {
    Seq("A:BB:C", "A:BB", "A:BB:", "A:BB:C:DDD", "A:", "A", "A:BB:C:").foreach { s =>
      val arr = new Array[String](3)
      val count = StringUtil.splitConcatenateExcessTokens(s, arr, ':')
      arr.take(count) shouldBe s.split(":", 3).filter(_.nonEmpty)
    }
  }

  "StringUtil.join" should "join tokens with a separator" in {
    StringUtil.join(",", 1, "hello", 'T') shouldBe "1,hello,T"
    StringUtil.join(",") shouldBe ""
  }

  "StringUtil.hammingDistance" should "return zero for two empty sequences" in {
      StringUtil.hammingDistance("", "") shouldBe 0
  }

  Seq(("ATAC", "GCAT", 3), ("ATAGC", "ATAGC", 0)).foreach { case (s1, s2, distance) =>
      it should s"return distance $distance between $s1 and $s2" in {
        StringUtil.hammingDistance(s1, s2) shouldBe distance
      }
  }

  it should "be case sensitive" in {
    StringUtil.hammingDistance("ATAC", "atac") shouldBe 4
  }

  it should "count Ns as matching when computing distance" in {
    StringUtil.hammingDistance("nAGTN", "nAGTN") shouldBe 0
  }

  it should "throw an exception if two strings of different lengths are provided" in {
    an[Exception] shouldBe thrownBy { StringUtil.hammingDistance("", "ABC")}
    an[Exception] shouldBe thrownBy { StringUtil.hammingDistance("Abc", "wxyz")}
  }

  "StringUtil.isWithinHammingDistance" should "agree with StringUtil.hammingDistance" in {
    Seq(("ATAC", "GCAT", 3), ("ATAC", "GCAT", 2), ("ATAC", "GCAT", 1), ("ATAC", "GCAT", 0)).foreach { case (s1, s2, within) =>
        StringUtil.isWithinHammingDistance(s1, s2, within) shouldBe (StringUtil.hammingDistance(s1, s2) <= within)
    }
  }

  it should "throw an exception if the two strings are of different lengths" in {
    an[Exception] shouldBe thrownBy { StringUtil.isWithinHammingDistance("", "ABC", 2)}
    an[Exception] shouldBe thrownBy { StringUtil.isWithinHammingDistance("Abc", "wxyz", 2)}
  }

  "StringUtil.toLowerCase(byte)" should "work just like Character.toLowerCase" in {
    0 to 127 foreach {i => StringUtil.toLowerCase(i.toByte) shouldBe i.toChar.toLower.toByte }
  }

  "StringUtil.toUpperCase(byte)" should "work just like Character.toUpperCase" in {
    0 to 127 foreach {i => StringUtil.toUpperCase(i.toByte) shouldBe i.toChar.toUpper.toByte }
  }

  "StringUtil.toUpperCase(byte[])" should "do upper case characters" in {
    val seq = "atACgtaCGTgatcCAtATATgATtatgacNryuAN"
    val bytes = seq.getBytes
    StringUtil.toUpperCase(bytes)
    bytes shouldBe seq.toUpperCase.getBytes
  }

  "StringUtil.assertCharactersNotInString" should "catch illegal characters" in {
    an[Exception] shouldBe thrownBy {
      StringUtil.assertCharactersNotInString("Hello World!", ' ', '!', '_')
    }
  }

  it should "not fail when there are no illegal characters present" in {
    StringUtil.assertCharactersNotInString("HelloWorld", ' ', '!', '_')
  }

  val textForWrapping =
    """This is a little bit
      |of text with nice short
      |lines.
    """.stripMargin.trim

  "StringUtil.wordWrap" should "not wrap when lines are shorter than the given length" in {
    StringUtil.wordWrap(textForWrapping, 50) shouldBe textForWrapping
  }

  it should "wrap text when lines are longer than length give" in {
    val result = StringUtil.wordWrap(textForWrapping, 15)
    result.lines.size shouldBe 5
    result.lines.foreach(line => line.length should be <= 15)
  }

  "StringUtil.intValuesToString(int[])" should "generate a CSV string of ints" in {
    val ints = Array[Int](1, 2, 3, 11, 22, 33, Int.MinValue, 0, Int.MaxValue)
    StringUtil.intValuesToString(ints) shouldBe ints.mkString(", ")
  }

  "StringUtil.intValuesToString(short[])" should "generate a CSV string of ints" in {
    val ints = Array[Short](1, 2, 3, 11, 22, 33, Short.MinValue, 0, Short.MaxValue)
    StringUtil.intValuesToString(ints) shouldBe ints.mkString(", ")
  }
}
