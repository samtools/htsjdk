package htsjdk.samtools.cram.structure.slice

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import htsjdk.UnitSpec
import htsjdk.samtools.cram.common.CramVersions
import htsjdk.samtools.{SAMBinaryTagAndValue, SAMTag}

class SliceHeaderTest extends UnitSpec {
  "SliceHeader" should "round-trip successfully" in {

    // arbitrary data
    val (sequenceId, alignmentStart, alignmentSpan, recordCount, globalRecordCounter) = (7, 33, 444, 1, 5435)
    val (dataBlockCount, embeddedRefBlockContentID) = (2, 5435)
    val contentIDs = Array(1, 2, 3)
    val refMD5 = "some text that we then reduce to the right length".getBytes.take(SliceHeader.MD5_LEN)

    Seq(CramVersions.CRAM_v2_1, CramVersions.CRAM_v3).foreach { version =>
      val tags = {
        if (version.compatibleWith(CramVersions.CRAM_v3)) {
          new SAMBinaryTagAndValue(SAMTag.makeBinaryTag("RG"), "Z")
        }
        else {
          null
        }
      }

      val sh = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount, globalRecordCounter, dataBlockCount,
        contentIDs, embeddedRefBlockContentID, refMD5, tags)

      val bytes = {
        val os = new ByteArrayOutputStream()
        sh.write(version.major, os)
        os.flush
        os.toByteArray
      }

      val sh2 = SliceHeader.read(version.major, new ByteArrayInputStream(bytes))
      sh2 shouldBe sh
    }
  }
}
