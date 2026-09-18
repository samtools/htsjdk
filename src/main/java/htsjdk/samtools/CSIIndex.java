package htsjdk.samtools;

import htsjdk.index.FileBackedBinningIndex;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A CSI index of a BAM file, opened by its caller rather than by a {@link SamReader}.
 * The CSI index extends the BAI index by allowing a more flexible
 * binning scheme, with variable depth (number of levels) and
 * bin sizes, thus allowing for genomic regions longer than 2^29-1.
 *
 * @deprecated ask a {@link SamReader} for its index, or to read an index on its own open it with
 *     {@link htsjdk.index.FileBackedBinningIndex}
 */
@Deprecated
public class CSIIndex extends AbstractBAMFileIndex implements BrowseableBAMIndex {

    public CSIIndex(final SeekableStream stream, final SAMSequenceDictionary dictionary) {
        super(stream, dictionary);
        requireCsi(stream.getSource());
    }

    public CSIIndex(final Path path, final SAMSequenceDictionary dictionary) throws IOException {
        super(path, dictionary);
        requireCsi(path.toString());
    }

    /**
     * @param enableMemoryMapping has no effect: an index is no longer memory-mapped
     */
    public CSIIndex(final Path path, boolean enableMemoryMapping, final SAMSequenceDictionary dictionary) {
        super(path, dictionary);
        requireCsi(path.toString());
    }

    private FileBackedBinningIndex source() {
        return (FileBackedBinningIndex) getDelegate().getSource();
    }

    private void requireCsi(final String sourceName) {
        if (!source().isCsi()) {
            close();
            throw new RuntimeIOException("Invalid file header in BAM CSI index " + sourceName);
        }
    }

    /**
     * Bin depth is the number of levels of the index. By default,
     * BAI has 6 levels. CSI makes this variable.
     */
    public int getBinDepth() {
        return source().getDepth() + 1;
    }

    /**
     * 2^(min shift) is the smallest width of a bin
     */
    public int getMinShift() {
        return source().getMinShift();
    }

    public int getMaxBins() {
        return ((1 << 3 * getBinDepth()) - 1) / 7;
    }

    public int getMaxSpan() {
        return 1 << (getMinShift() + 3 * (getBinDepth() - 1));
    }

    public byte[] getAuxData() {
        return source().getAux();
    }

    /**
     * Extends the functionality of {@link AbstractBAMFileIndex#getFirstBinInLevel(int)} ,
     * which cannot be overridden due to its static nature.
     */
    public int getFirstBinInLevelForCSI(final int levelNumber) {
        if (levelNumber >= getBinDepth()) {
            throw new SAMException(
                    "Level number (" + levelNumber + ") is greater than or equal to maximum (" + getBinDepth() + ").");
        }
        return ((1 << 3 * levelNumber) - 1) / 7;
    }

    @Override
    public BinList getBinsOverlapping(final int referenceIndex, final int startPos, final int endPos) {
        return getDelegate().getBinsOverlapping(referenceIndex, startPos, endPos);
    }

    @Override
    public BAMFileSpan getSpanOverlapping(final Bin bin) {
        return getDelegate().getSpanOverlapping(bin);
    }

    public int getParentBinNumber(int binNumber) {
        if (binNumber >= getMaxBins()) {
            throw new SAMException("Tried to get parent bin for invalid bin (" + binNumber + ").");
        }
        if (binNumber == 0) {
            return 0;
        }
        return (binNumber - 1) >> 3;
    }

    public int getParentBinNumber(Bin bin) {
        if (bin == null) {
            throw new SAMException("Tried to get parent bin for null bin.");
        }
        return getParentBinNumber(bin.getBinNumber());
    }
}
