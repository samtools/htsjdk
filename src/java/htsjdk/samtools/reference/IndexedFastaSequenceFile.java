/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

package htsjdk.samtools.reference;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.IOUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * A fasta file driven by an index for fast, concurrent lookups.  Supports two interfaces:
 * the ReferenceSequenceFile for old-style, stateful lookups and a direct getter.
 */
public class IndexedFastaSequenceFile extends AbstractFastaSequenceFile implements Closeable {
    /**
     * The interface facilitating direct access to the fasta.
     */
    private final SeekableByteChannel channel;

    /**
     * A representation of the sequence index, stored alongside the fasta in a .fasta.fai file.
     */
    private final FastaSequenceIndex index;

    /**
     * An iterator into the fasta index, for traversing iteratively across the fasta.
     */
    private Iterator<FastaSequenceIndexEntry> indexIterator;

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param file The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file, final FastaSequenceIndex index) {
        this(file == null ? null : file.toPath(), index);
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param file The file to open.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file) throws FileNotFoundException {
        this(file, new FastaSequenceIndex((findRequiredFastaIndexFile(file))));
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param path The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     */
    public IndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index) {
        super(path);
        if (index == null) throw new IllegalArgumentException("Null index for fasta " + path);
        this.index = index;
        IOUtil.assertFileIsReadable(path);
        try {
            this.channel = Files.newByteChannel(path);
        } catch (IOException e) {
            throw new SAMException("Fasta file should be readable but is not: " + path, e);
        }
        reset();

        if(getSequenceDictionary() != null)
            sanityCheckDictionaryAgainstIndex(path.toAbsolutePath().toString(),sequenceDictionary,index);
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param path The file to open.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final Path path) throws FileNotFoundException {
        this(path, new FastaSequenceIndex((findRequiredFastaIndexFile(path))));
    }

    public boolean isIndexed() {return true;}

    private static File findFastaIndex(File fastaFile) {
        File indexFile = getFastaIndexFileName(fastaFile);
        if (!indexFile.exists()) return null;
        return indexFile;
    }

    private static File getFastaIndexFileName(File fastaFile) {
        return new File(fastaFile.getAbsolutePath() + ".fai");
    }

    private static File findRequiredFastaIndexFile(File fastaFile) throws FileNotFoundException {
        File ret = findFastaIndex(fastaFile);
        if (ret == null) throw new FileNotFoundException(getFastaIndexFileName(fastaFile) + " not found.");
        return ret;
    }

    public static boolean canCreateIndexedFastaReader(final File fastaFile) {
        return (fastaFile.exists() &&
                findFastaIndex(fastaFile) != null);
    }

    private static Path findFastaIndex(Path fastaFile) {
        Path indexFile = getFastaIndexFileName(fastaFile);
        if (!Files.exists(indexFile)) return null;
        return indexFile;
    }

    private static Path getFastaIndexFileName(Path fastaFile) {
        return fastaFile.resolveSibling(fastaFile.getFileName() + ".fai");
    }

    private static Path findRequiredFastaIndexFile(Path fastaFile) throws FileNotFoundException {
        Path ret = findFastaIndex(fastaFile);
        if (ret == null) throw new FileNotFoundException(getFastaIndexFileName(fastaFile) + " not found.");
        return ret;
    }

    public static boolean canCreateIndexedFastaReader(final Path fastaFile) {
        return (Files.exists(fastaFile) &&
            findFastaIndex(fastaFile) != null);
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

    /**
     * Retrieves the sequence dictionary for the fasta file.
     * @return sequence dictionary of the fasta.
     */
    public SAMSequenceDictionary getSequenceDictionary() {
        return sequenceDictionary;
    }

    /**
     * Retrieves the complete sequence described by this contig.
     * @param contig contig whose data should be returned.
     * @return The full sequence associated with this contig.
     */
    public ReferenceSequence getSequence( String contig ) {
        return getSubsequenceAt( contig, 1, (int)index.getIndexEntry(contig).getSize() );
    }

    /**
     * Gets the subsequence of the contig in the range [start,stop]
     * @param contig Contig whose subsequence to retrieve.
     * @param start inclusive, 1-based start of region.
     * @param stop inclusive, 1-based stop of region.
     * @return The partial reference sequence associated with this range.
     */
    public ReferenceSequence getSubsequenceAt( String contig, long start, long stop ) {
        if(start > stop + 1)
            throw new SAMException(String.format("Malformed query; start point %d lies after end point %d",start,stop));

        FastaSequenceIndexEntry indexEntry = index.getIndexEntry(contig);

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
                startOffset += readFromPosition(channel, channelBuffer, indexEntry.getLocation()+startOffset);
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
     * Reads a sequence of bytes from this channel into the given buffer,
     * starting at the given file position.
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position to start reading at
     * @return the number of bytes read
     * @throws IOException if an I/O error occurs while reading
     */
    private static int readFromPosition(final SeekableByteChannel channel, final ByteBuffer buffer, long position) throws IOException {
        if (channel instanceof FileChannel) { // special case to take advantage of native code path
            return ((FileChannel) channel).read(buffer,position);
        } else {
            long oldPos = channel.position();
            try {
                channel.position(position);
                return channel.read(buffer);
            } finally {
                channel.position(oldPos);
            }
        }
    }

    /**
     * Gets the next sequence if available, or null if not present.
     * @return next sequence if available, or null if not present.
     */
    public ReferenceSequence nextSequence() {
        if( !indexIterator.hasNext() )
            return null;
        return getSequence( indexIterator.next().getContig() );
    }

    /**
     * Reset the iterator over the index.
     */
    public void reset() {
        indexIterator = index.iterator();
    }

    /**
     * A simple toString implementation for debugging.
     * @return String representation of the file.
     */
    public String toString() {
        return getAbsolutePath();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
