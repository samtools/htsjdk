/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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

/**
 * A {@link TabixIndexCreator} that can write to an output stream.
 */
public class StreamBasedTabixIndexCreator extends AllRefsTabixIndexCreator {

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
    final Index index = super.finalizeIndex(finalFilePosition);
    final TabixIndex tabixIndex = (TabixIndex) index;
    return new StreamBasedTabixIndex(
        tabixIndex.getFormatSpec(),
        tabixIndex.getSequenceNames(),
        tabixIndex.getIndices(),
        out);
  }
}
