package htsjdk.samtools.cram.structure.slice

import htsjdk.UnitSpec
import htsjdk.samtools.cram.CRAIEntry

class SliceBAIMetadataTest extends UnitSpec {
  "SliceBAIMetadata" should "convert from CRAIEntry" in {

    // arbitrary data
    val (sequenceId, alignmentStart, alignmentSpan, containerStartOffset, sliceByteOffset, sliceByteSize) = (7, 33, 444, 1, 5435, 654)

    val craiEntry = new CRAIEntry(sequenceId, alignmentStart, alignmentSpan, containerStartOffset, sliceByteOffset, sliceByteSize)
    val sliceBAIMetadata = craiEntry.toBAIMetadata(-1, -2)

    sliceBAIMetadata.getSequenceId shouldBe craiEntry.getSequenceId
    sliceBAIMetadata.getAlignmentStart shouldBe craiEntry.getAlignmentStart
    sliceBAIMetadata.getAlignmentSpan shouldBe craiEntry.getAlignmentSpan
    sliceBAIMetadata.getContainerByteOffset shouldBe craiEntry.getContainerStartOffset
    sliceBAIMetadata.getByteOffset shouldBe craiEntry.getSliceByteOffset
    sliceBAIMetadata.getByteSize shouldBe craiEntry.getSliceByteSize
    sliceBAIMetadata.getRecordCount shouldBe -1
    sliceBAIMetadata.getIndex shouldBe -2
  }
}