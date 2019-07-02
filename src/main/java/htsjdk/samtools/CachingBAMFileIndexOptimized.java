package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;

/**
 * A subclass of CachingBAMFileIndex that is optimized for merging, by returning
 * null BAMIndexContent objects if all bins are empty.
 */
public class CachingBAMFileIndexOptimized extends CachingBAMFileIndex {
  public CachingBAMFileIndexOptimized(SeekableStream stream, SAMSequenceDictionary dictionary) {
    super(stream, dictionary);
  }

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

  private void skipToSequence(final int sequenceIndex) {
    //Use sequence position cache if available
    if(sequenceIndexes[sequenceIndex] != -1){
      seek(sequenceIndexes[sequenceIndex]);
      return;
    }

    // Use previous sequence position if in cache
    final int startSequenceIndex;
    if (sequenceIndex > 0 && sequenceIndexes[sequenceIndex - 1] != -1) {
      seek(sequenceIndexes[sequenceIndex - 1]);
      startSequenceIndex = sequenceIndex - 1;
    } else {
      startSequenceIndex = 0;
    }

    for (int i = startSequenceIndex; i < sequenceIndex; i++) {
      // System.out.println("# Sequence TID: " + i);
      final int nBins = readInteger();
      // System.out.println("# nBins: " + nBins);
      for (int j = 0; j < nBins; j++) {
        readInteger(); // bin
        final int nChunks = readInteger();
        // System.out.println("# bin[" + j + "] = " + bin + ", nChunks = " + nChunks);
        skipBytes(16 * nChunks);
      }
      final int nLinearBins = readInteger();
      // System.out.println("# nLinearBins: " + nLinearBins);
      skipBytes(8 * nLinearBins);
    }

    //Update sequence position cache
    sequenceIndexes[sequenceIndex] = position();
  }

}
