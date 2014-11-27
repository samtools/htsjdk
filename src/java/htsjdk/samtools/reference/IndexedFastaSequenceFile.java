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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A fasta file driven by an index for fast, concurrent lookups.  Supports two interfaces:
 * the ReferenceSequenceFile for old-style, stateful lookups and a direct getter.
 */
public class IndexedFastaSequenceFile extends AbstractFastaSequenceFile implements Closeable {
    /**
     * The interface facilitating direct access to the fasta.
     */
    private final FileChannel channel;
    /**
     * File input stream 
     */
    private final FileInputStream stream;
    /**
     * A representation of the sequence index, stored alongside the fasta in a .fasta.fai file.
     */
    private final FastaSequenceIndex index;
    /**
     * Memory mapping of fasta in Integer.MAX_VALUE chunks
     */
    private final List<MappedByteBuffer> mmap;
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
        this(file, index, Defaults.MEMORY_MAP_REFERENCES);
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
     * @param file The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     * @param memoryMapped Should the reference file be memory mapped
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file, final FastaSequenceIndex index, boolean memoryMapped) {
        super(file);
        if (index == null) throw new IllegalArgumentException("Null index for fasta " + file);
        this.index = index;
        IOUtil.assertFileIsReadable(file);
        try {
        	stream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new SAMException("Fasta file should be readable but is not: " + file, e);
        }
        channel = stream.getChannel();
        reset();

        if(getSequenceDictionary() != null)
            sanityCheckDictionaryAgainstIndex(file.getAbsolutePath(),sequenceDictionary,index);
        
        if (memoryMapped) {
        	try {
        		mmap = new ArrayList<MappedByteBuffer>();
        		for (long i = 0; i < file.length(); i++) {
        			long mapLength = Math.min(file.length() - i, Integer.MAX_VALUE);
        			mmap.add(channel.map(MapMode.READ_ONLY, i, mapLength));
        			i += mapLength;
        		}
            } catch (IOException e) {
                throw new SAMException("Fastq file should be readable but unable to memory map file: " + file, e);
            }
        } else {
        	mmap = null;
        }
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param file The file to open.
     * @param memoryMapped Should the reference file be memory mapped 
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file, boolean memoryMapped) throws FileNotFoundException {
        this(file, new FastaSequenceIndex((findRequiredFastaIndexFile(file))), memoryMapped);
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

        final int basesPerLine = indexEntry.getBasesPerLine();
        final int bytesPerLine = indexEntry.getBytesPerLine();
        final int terminatorLength = bytesPerLine - basesPerLine;

        long startOffset = ((start-1)/basesPerLine)*bytesPerLine + (start-1)%basesPerLine;

        if (mmap == null) {
	        // Allocate a buffer for reading in sequence data.
	        ByteBuffer channelBuffer = ByteBuffer.allocate(Math.min(Defaults.NON_ZERO_BUFFER_SIZE, (length % basesPerLine + 2) * bytesPerLine));
	        ByteBuffer targetBuffer = ByteBuffer.wrap(target);
	        while(targetBuffer.position() < length) {
	            // If the bufferOffset is currently within the eol characters in the string, push the bufferOffset forward to the next printable character.
	            startOffset += Math.max((int)(startOffset%bytesPerLine - basesPerLine + 1),0);
	
	            try {
	                 startOffset += channel.read(channelBuffer,indexEntry.getLocation()+startOffset);
	            }
	            catch(IOException ex) {
	                throw new SAMException("Unable to load " + contig + "(" + start + ", " + stop + ") from " + file);
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
        } else {
        	int transfered = 0;
        	while (transfered < length) {
        		long transferStartSequenceOffset = start - 1 + transfered;
        		long lineNumber = transferStartSequenceOffset / basesPerLine;
        		long lineOffset = transferStartSequenceOffset % basesPerLine;
        		long transferStartFilePosition = indexEntry.getLocation() + lineNumber * bytesPerLine + lineOffset;
        		long transferLength = Math.min(length - transfered, basesPerLine - lineOffset);
        		if (mmapCopy(target, transfered, transferStartFilePosition, transferLength) != transferLength) {
        			throw new SAMException("Unable to load " + contig + "(" + start + ", " + stop + ") from " + file);
        		}
        		transfered += transferLength;
        	}
        }
        return new ReferenceSequence( contig, indexEntry.getSequenceIndex(), target );
    }
    private long mmapCopy(byte[] dst, int offset, long pos, long length) {
    	int bufferIndex = (int)(pos / Integer.MAX_VALUE);
    	int bufferOffset =  (int)(pos % Integer.MAX_VALUE);
    	long copied = 0;
    	while (copied < length && bufferIndex < mmap.size()) {
    		ByteBuffer bb = mmap.get((int)bufferIndex).duplicate();
    		bb.position(bufferOffset);
    		int copyLength = Math.min((int)Math.min(length - copied, Integer.MAX_VALUE), bb.capacity() - bufferOffset);
    		copied += copyLength;
    		bb.get(dst, offset, copyLength);
    		bufferIndex++;
    		bufferOffset = 0;
    	}
    	return copied;
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
        return this.file.getAbsolutePath();
    }

    @Override
    public void close() throws IOException {
    	if (mmap != null) {
    		mmap.clear();
    	}
        channel.close();
        stream.close();
    }
}