/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.reference;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.IOUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
abstract class AbstractIndexedFastaSequenceFile extends AbstractFastaSequenceFile {

    /**
     * A representation of the sequence index, stored alongside the fasta in a .fasta.fai file.
     */
    private final FastaSequenceIndex index;

    /**
     * An iterator into the fasta index, for traversing iteratively across the fasta.
     */
    private Iterator<FastaSequenceIndexEntry> indexIterator;

    protected AbstractIndexedFastaSequenceFile(final Path path) throws FileNotFoundException {
        this(path, new FastaSequenceIndex((findRequiredFastaIndexFile(path))));
    }

    protected AbstractIndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index) {
        super(path);
        if (index == null) throw new IllegalArgumentException("Null index for fasta " + path);
        this.index = index;
        IOUtil.assertFileIsReadable(path);
        reset();
        if(getSequenceDictionary() != null)
            sanityCheckDictionaryAgainstIndex(path.toAbsolutePath().toString(),sequenceDictionary,index);
    }

    protected static Path findRequiredFastaIndexFile(Path fastaFile) throws FileNotFoundException {
        Path ret = findFastaIndex(fastaFile);
        if (ret == null) throw new FileNotFoundException(ReferenceSequenceFileFactory.getFastaIndexFileName(fastaFile) + " not found.");
        return ret;
    }

    protected static Path findFastaIndex(Path fastaFile) {
        Path indexFile = ReferenceSequenceFileFactory.getFastaIndexFileName(fastaFile);
        if (!Files.exists(indexFile)) return null;
        return indexFile;
    }

    /**
     * Do some basic checking to make sure the dictionary and the index match.
     * @param fastaFile Used for error reporting only.
     * @param sequenceDictionary sequence dictionary to check against the index.
     * @param index index file to check against the dictionary.
     */
    protected static void sanityCheckDictionaryAgainstIndex(final String fastaFile,
            final SAMSequenceDictionary sequenceDictionary,
            final FastaSequenceIndex index) {
        // Make sure dictionary and index are the same size.
        if( sequenceDictionary.getSequences().size() != index.size() )
            throw new SAMException("Sequence dictionary and index contain different numbers of contigs");

        Iterator<SAMSequenceRecord> sequenceIterator = sequenceDictionary.getSequences().iterator();
        Iterator<FastaSequenceIndexEntry> indexIterator = index.iterator();

        while(sequenceIterator.hasNext() && indexIterator.hasNext()) {
            SAMSequenceRecord sequenceEntry = sequenceIterator.next();
            FastaSequenceIndexEntry indexEntry = indexIterator.next();

            if(!sequenceEntry.getSequenceName().equals(indexEntry.getContig())) {
                throw new SAMException(String.format("Mismatch between sequence dictionary fasta index for %s, sequence '%s' != '%s'.",
                        fastaFile, sequenceEntry.getSequenceName(),indexEntry.getContig()));
            }

            // Make sure sequence length matches index length.
            if( sequenceEntry.getSequenceLength() != indexEntry.getSize())
                throw new SAMException("Index length does not match dictionary length for contig: " + sequenceEntry.getSequenceName() );
        }
    }

    public FastaSequenceIndex getIndex() {
        return index;
    }

    /**
     * Gets the next sequence if available, or null if not present.
     * @return next sequence if available, or null if not present.
     */
    @Override
    public final ReferenceSequence nextSequence() {
        if( !indexIterator.hasNext() )
            return null;
        return getSequence( indexIterator.next().getContig() );
    }

    /**
     * Reset the iterator over the index.
     */
    @Override
    public final void reset() {
        indexIterator = index.iterator();
    }

    @Override
    public final boolean isIndexed() {
        return true;
    }

    /**
     * Retrieves the complete sequence described by this contig.
     * @param contig contig whose data should be returned.
     * @return The full sequence associated with this contig.
     */
    @Override
    public final ReferenceSequence getSequence( String contig ) {
        return getSubsequenceAt( contig, 1, (int)index.getIndexEntry(contig).getSize() );
    }

    /**
     * Gets the subsequence of the contig in the range [start,stop]
     * @param contig Contig whose subsequence to retrieve.
     * @param start inclusive, 1-based start of region.
     * @param stop inclusive, 1-based stop of region.
     * @return The partial reference sequence associated with this range.
     */
    @Override
    public final ReferenceSequence getSubsequenceAt( String contig, long start, long stop ) {
        if(start > stop + 1)
            throw new SAMException(String.format("Malformed query; start point %d lies after end point %d",start,stop));

        FastaSequenceIndexEntry indexEntry = getIndex().getIndexEntry(contig);

        if(stop > indexEntry.getSize())
            throw new SAMException("Query asks for data past end of contig");

        int length = (int)(stop - start + 1);

        byte[] target = new byte[length];
        ByteBuffer targetBuffer = ByteBuffer.wrap(target);

        final int basesPerLine = indexEntry.getBasesPerLine();
        final int bytesPerLine = indexEntry.getBytesPerLine();
        final int terminatorLength = bytesPerLine - basesPerLine;

        long startOffset = ((start-1)/basesPerLine)*bytesPerLine + (start-1)%basesPerLine;
        // Cast to long so the second argument cannot overflow a signed integer.
        final long minBufferSize = Math.min((long) Defaults.NON_ZERO_BUFFER_SIZE, (long)(length / basesPerLine + 2) * (long)bytesPerLine);
        if (minBufferSize > Integer.MAX_VALUE) throw new SAMException("Buffer is too large: " +  minBufferSize);

        // Allocate a buffer for reading in sequence data.
        final ByteBuffer channelBuffer = ByteBuffer.allocate((int)minBufferSize);

        while(targetBuffer.position() < length) {
            // If the bufferOffset is currently within the eol characters in the string, push the bufferOffset forward to the next printable character.
            startOffset += Math.max((int)(startOffset%bytesPerLine - basesPerLine + 1),0);

            try {
                startOffset += readFromPosition(channelBuffer, indexEntry.getLocation()+startOffset);
            }
            catch(IOException ex) {
                throw new SAMException("Unable to load " + contig + "(" + start + ", " + stop + ") from " + getAbsolutePath(), ex);
            }

            // Reset the buffer for outbound transfers.
            channelBuffer.flip();

            // Calculate the size of the next run of bases based on the contents we've already retrieved.
            final int positionInContig = (int)start-1+targetBuffer.position();
            final int nextBaseSpan = Math.min(basesPerLine-positionInContig%basesPerLine,length-targetBuffer.position());
            // Cap the bytes to transfer by limiting the nextBaseSpan to the size of the channel buffer.
            int bytesToTransfer = Math.min(nextBaseSpan,channelBuffer.capacity());

            channelBuffer.limit(channelBuffer.position()+bytesToTransfer);

            while(channelBuffer.hasRemaining()) {
                targetBuffer.put(channelBuffer);

                bytesToTransfer = Math.min(basesPerLine,length-targetBuffer.position());
                channelBuffer.limit(Math.min(channelBuffer.position()+bytesToTransfer+terminatorLength,channelBuffer.capacity()));
                channelBuffer.position(Math.min(channelBuffer.position()+terminatorLength,channelBuffer.capacity()));
            }

            // Reset the buffer for inbound transfers.
            channelBuffer.flip();
        }

        return new ReferenceSequence( contig, indexEntry.getSequenceIndex(), target );
    }

    /**
     * Reads a sequence of bytes from this sequence file into the given buffer,
     * starting at the given file position.
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position to start reading at
     *
     * @return the number of bytes read
     * @throws IOException if an I/O error occurs while reading
     */
    protected abstract int readFromPosition(final ByteBuffer buffer, long position) throws IOException;
}
