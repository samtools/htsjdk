package htsjdk.tribble.index.tabix;

import htsjdk.samtools.BinningIndexContent;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.util.LittleEndianOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

public class StreamBasedTabixIndexCreator extends TabixIndexCreator {

  static class StreamBasedTabixIndex extends TabixIndex {
    private final OutputStream out;

    StreamBasedTabixIndex(
        TabixFormat formatSpec,
        List<String> sequenceNames,
        BinningIndexContent[] indices,
        OutputStream out) {
      super(formatSpec, sequenceNames, indices);
      this.out = out;
    }

    @Override
    public void writeBasedOnFeaturePath(final Path featurePath) throws IOException {
      try (final LittleEndianOutputStream los =
          new LittleEndianOutputStream(new BlockCompressedOutputStream(out, (Path) null))) {
        write(los);
      }
    }
  }

  private final OutputStream out;

  public StreamBasedTabixIndexCreator(
          SAMSequenceDictionary sequenceDictionary, TabixFormat formatSpec, OutputStream out) {
    super(sequenceDictionary, formatSpec);
    this.out = out;
  }

  @Override
  public Index finalizeIndex(long finalFilePosition) {
    Index index = super.finalizeIndex(finalFilePosition);
    TabixIndex tabixIndex = (TabixIndex) index;
    return new StreamBasedTabixIndex(
        tabixIndex.getFormatSpec(),
        tabixIndex.getSequenceNames(),
        tabixIndex.getIndices(),
        out);
  }
}
