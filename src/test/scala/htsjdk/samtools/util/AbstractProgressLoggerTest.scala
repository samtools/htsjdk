package htsjdk.samtools.util

import htsjdk.UnitSpec

class AbstractProgressLoggerTest extends UnitSpec {

  "AbstractProgressLoggerTest.pad" should "pad the right amount of spaces in " in {
    Seq(
      ("hello", 10, "     hello"),
      ("hello", 6, " hello"),
      ("hello", 5, "hello"),
      ("hello", 4, "hello"),
      ("hello", -1, "hello")).foreach { case (in, len, expected) =>
      AbstractProgressLogger.pad(in, len) shouldEqual expected
    }
  }
}
