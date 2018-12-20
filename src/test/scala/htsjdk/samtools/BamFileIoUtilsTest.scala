package htsjdk.samtools

import java.io.File
import htsjdk.UnitSpec

class BamFileIoUtilsTest extends UnitSpec {

  "BamFileIoUtils.isBamFile" should "identify bam files" in {
    Seq(
      ("test.bam", true),
      ("test.sam.bam", true),
      (".bam", true),
      ("./test.bam", true),
      ("./test..bam", true),
      ("c:\\path\\to\test.bam", true),
      ("test.Bam", false),
      ("test.BAM", false),
      ("test.sam", false),
      ("test.bam.sam", false),
      ("testbam", false)).foreach {
      case (file, isBam) =>
        BamFileIoUtils.isBamFile(new File(file)) shouldEqual isBam
    }
  }
}

