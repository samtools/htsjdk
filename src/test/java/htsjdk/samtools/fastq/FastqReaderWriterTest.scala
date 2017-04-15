package htsjdk.samtools.fastq

import java.io.{BufferedReader, File, StringReader}
import java.nio.file.{Path, Paths}

import htsjdk.UnitSpec
import htsjdk.samtools.SAMUtils
import htsjdk.samtools.util.IOUtil

import scala.util.Random
import scala.collection.JavaConverters.asScalaIterator

class FastqReaderWriterTest extends UnitSpec {
  private val rng = new Random()
  private val Bases = Array('A', 'C', 'G', 'T')

  /** Generates a random string of bases of the desired length. */
  def bases(length: Int): String = {
    val chs = new Array[Char](length)
    chs.indices.foreach(i => chs(i) = Bases(rng.nextInt(Bases.length)))
    new String(chs)
  }

  /** Generates a FastqRecord with random bases at a given length. */
  def fq(name: String, length: Int, qual: Int = 30): FastqRecord = {
    new FastqRecord(name, bases(length), "", SAMUtils.phredToFastq(qual).toString * length)
  }

  "FastqWriter" should "write four lines per record to file" in {
    val path = makeTempFile("test.", ".fastq")
    val out = new FastqWriterFactory().newWriter(path.toFile)
    val recs = Seq(fq("q1", 50), fq("q2", 48), fq("q3", 55))
    val Seq(q1, q2, q3) = recs

    recs.foreach(rec => out.write(rec))
    out.close()

    val lines = IOUtil.slurpLines(path.toFile)
    lines should have size 12

    lines.get(0) shouldBe "@q1"
    lines.get(1) shouldBe q1.getReadString
    lines.get(4) shouldBe "@q2"
    lines.get(5) shouldBe q2.getReadString
    lines.get(8) shouldBe "@q3"
    lines.get(9) shouldBe q3.getReadString
  }

  it should "write a record with only a single base" in {
    val path = makeTempFile("test.", ".fastq")
    val out = new FastqWriterFactory().newWriter(path.toFile)
    out.write(fq("q1", 1))
    out.close()
    IOUtil.slurpLines(path.toFile).get(1) should have length 1
  }

  it should "write a record with zero-length bases and quals" in {
    val path = makeTempFile("test.", ".fastq")
    val out = new FastqWriterFactory().newWriter(path.toFile)
    out.write(fq("q1", 0))
    out.close()
    IOUtil.slurpLines(path.toFile).get(1) should have length 0
  }


  "FastqReader" should "read back a fastq file written by FastqWriter" in {
    val path = makeTempFile("test.", ".fastq")
    val out = new FastqWriterFactory().newWriter(path.toFile)
    val recs = Seq(fq("q1", 50), fq("q2", 100), fq("q3", 150))
    recs.foreach(rec => out.write(rec))
    out.close()

    val in = new FastqReader(path.toFile)
    val recs2 = in.iterator().toList
    in.close()
    recs2 should contain theSameElementsInOrderAs recs
  }

  it should "throw an exception if the input fastq is garbled" in {
    val fastq =
      """
        |@q1
        |AACCGGTT
        |+
        |########
        |@q2
        |ACGT
        |####
      """.stripMargin.trim

    val in = new FastqReader(null, new BufferedReader(new StringReader(fastq)))
    an[Exception] shouldBe thrownBy { in.next() }
  }

  it should "throw an exception if the input file doesn't exist" in {
    an[Exception] shouldBe thrownBy { new FastqReader(new File("/some/path/that/shouldnt/exist.fq"))}
  }

  it should "read an empty file just fine" in {
    val path = makeTempFile("empty.", ".fastq")
    val in = new FastqReader(path.toFile)
    while (in.hasNext) in.next()
    an[Exception] shouldBe thrownBy { in.next() }
    in.close()
  }

  it should "fail on a truncated file" in {
    val fastq =
      """
        |@q1
        |AACCGGTT
        |+
        |########
      """.stripMargin.trim

    Range.inclusive(1, 3).foreach { n =>
      val text   = fastq.lines.take(n).mkString("\n")
      val reader = new BufferedReader(new StringReader(text))
      an[Exception] shouldBe thrownBy { new FastqReader(null, reader).iterator().toSeq }
    }
  }

  it should "fail if the seq and qual lines are different lengths" in {
    val fastq =
      """
        |@q1
        |AACC
        |+
        |########
      """.stripMargin.trim

    val reader = new BufferedReader(new StringReader(fastq))
    an[Exception] shouldBe thrownBy { new FastqReader(null, reader).iterator().toSeq }
  }

  it should "fail if either header line is empty" in {
    val fastq =
      """
        |@q1
        |AACC
        |+q1
        |########
      """.stripMargin.trim

    val noSeqHeader  = new BufferedReader(new StringReader(fastq.replace("@q1", "")))
    val noQualHeader = new BufferedReader(new StringReader(fastq.replace("+q1", "")))
    an[Exception] shouldBe thrownBy { new FastqReader(noSeqHeader).iterator().toSeq }
    an[Exception] shouldBe thrownBy { new FastqReader(noQualHeader).iterator().toSeq }
  }

}
