package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;

/**
 * A subclass of CachingBAMFileIndex that is optimized for merging, by returning
 * null BAMIndexContent objects if all bins are empty.
 */
class CachingBamFileIndexOptimizedForMerging extends CachingBAMFileIndex {
  CachingBamFileIndexOptimizedForMerging(SeekableStream stream, SAMSequenceDictionary dictionary) {
    super(stream, dictionary);
  }

  @Override
  protected BAMIndexContent query(final int referenceSequence, final int startPos, final int endPos) {
    seek(4);

    final int sequenceCount = readInteger();

    if (referenceSequence >= sequenceCount) {
      return null;
    }

    skipToSequence(referenceSequence);

    final int binCount = readInteger();

    if (binCount == 0) {
      return null;
    }

    return super.query(referenceSequence, startPos, endPos);
  }
}
