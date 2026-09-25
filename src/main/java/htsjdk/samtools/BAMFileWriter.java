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
package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.zip.DeflaterFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Concrete implementation of SAMFileWriter for writing gzipped BAM files.
 */
public class BAMFileWriter extends SAMFileWriterImpl {

    private final BinaryCodec outputBinaryCodec;
    private BAMRecordCodec bamRecordCodec = null;
    private final BlockCompressedOutputStream blockCompressedOutputStream;
    private BAMIndexer bamIndexer = null;
    private Path bamIndexPath;
    private SAMException indexingFailure;

    protected BAMFileWriter(final Path path) {
        blockCompressedOutputStream = new BlockCompressedOutputStream(path);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(path.toAbsolutePath().toString());
    }

    protected BAMFileWriter(final Path path, final int compressionLevel) {
        blockCompressedOutputStream = new BlockCompressedOutputStream(path, compressionLevel);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(path.toAbsolutePath().toString());
    }

    protected BAMFileWriter(final OutputStream os, final Path path) {
        blockCompressedOutputStream = new BlockCompressedOutputStream(os, path);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(getPathString(path));
    }

    protected BAMFileWriter(final OutputStream os, final Path path, final int compressionLevel) {
        blockCompressedOutputStream = new BlockCompressedOutputStream(os, path, compressionLevel);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(getPathString(path));
    }

    protected BAMFileWriter(
            final OutputStream os, final Path path, final int compressionLevel, final DeflaterFactory deflaterFactory) {
        blockCompressedOutputStream = new BlockCompressedOutputStream(os, path, compressionLevel, deflaterFactory);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(getPathString(path));
    }

    protected BAMFileWriter(
            final OutputStream os,
            final String absoluteFilename,
            final int compressionLevel,
            final DeflaterFactory deflaterFactory) {
        blockCompressedOutputStream =
                new BlockCompressedOutputStream(os, (Path) null, compressionLevel, deflaterFactory);
        outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        outputBinaryCodec.setOutputFileName(absoluteFilename);
    }

    private void prepareToWriteAlignments() {
        if (bamRecordCodec == null) {
            bamRecordCodec = new BAMRecordCodec(getFileHeader());
            bamRecordCodec.setOutputStream(outputBinaryCodec.getOutputStream(), getFilename());
        }
    }

    /** @return absolute path, or null if arg is null.  */
    private String getPathString(final Path path) {
        return (path != null) ? path.toAbsolutePath().toString() : null;
    }

    // Allow enabling the bam index construction
    // only enabled by factory method before anything is written
    void enableBamIndexConstruction() {
        enableBamIndexConstruction(BamIndexType.BAI, BAMIndexer.DEFAULT_CSI_MIN_SHIFT);
    }

    /**
     * @param indexType the kind of index to write beside the BAM: a BAI is named {@code x.bai} and a CSI
     *     {@code x.bam.csi}, as samtools names it
     * @param csiMinShift for a CSI, log2 of the span of its smallest bins
     */
    void enableBamIndexConstruction(final BamIndexType indexType, final int csiMinShift) {
        if (!getSortOrder().equals(SAMFileHeader.SortOrder.coordinate)) {
            throw new SAMException("Not creating BAM index since not sorted by coordinates: " + getSortOrder());
        }
        if (getFilename() == null) {
            throw new SAMException("Not creating BAM index since we don't have an output file name");
        }
        bamIndexer =
                createBamIndex(getFilename(), indexType.resolve(getFileHeader().getSequenceDictionary()), csiMinShift);
    }

    private BAMIndexer createBamIndex(final String pathURI, final BamIndexType indexType, final int csiMinShift) {
        try {
            final Path indexPath;
            if (indexType == BamIndexType.CSI) {
                indexPath = IOUtil.getPath(pathURI + FileExtensions.CSI);
            } else {
                final String indexFileBase = SamReader.Type.BAM_TYPE.hasValidFileExtension(pathURI)
                        ? pathURI.substring(0, pathURI.lastIndexOf('.'))
                        : pathURI;
                indexPath = IOUtil.getPath(indexFileBase + FileExtensions.BAI_INDEX);
            }
            if (Files.exists(indexPath)) {
                if (!Files.isWritable(indexPath)) {
                    throw new SAMException(
                            "Not creating BAM index since unable to write index file " + indexPath.toUri());
                }
            }
            this.bamIndexPath = indexPath;
            return new BAMIndexer(indexPath, getFileHeader(), indexType, csiMinShift);
        } catch (Exception e) {
            throw new SAMException("Not creating BAM index", e);
        }
    }

    @Override
    protected void writeAlignment(final SAMRecord alignment) {
        if (indexingFailure != null) {
            throw new SAMException(
                    "Cannot write further alignments after a BAM indexing failure on " + bamIndexPath, indexingFailure);
        }
        prepareToWriteAlignments();

        if (bamIndexer != null) {
            try {
                final long startOffset = blockCompressedOutputStream.getFilePointer();
                bamRecordCodec.encode(alignment);
                final long stopOffset = blockCompressedOutputStream.getFilePointer();
                // set the alignment's SourceInfo and then prepare its index information
                alignment.setFileSource(new SAMFileSource(null, new BAMFileSpan(new Chunk(startOffset, stopOffset))));
                bamIndexer.processAlignment(alignment);
            } catch (Exception e) {
                bamIndexer.abandon();
                deleteIndexQuietly(bamIndexPath);
                indexingFailure = new SAMException("Exception when processing alignment for BAM index " + alignment, e);
                throw indexingFailure;
            }
        } else {
            bamRecordCodec.encode(alignment);
        }
    }

    @Override
    protected void writeHeader(final String textHeader) {
        writeHeader(outputBinaryCodec, getFileHeader(), textHeader);
    }

    @Override
    protected void finish() {
        final long endOfRecords = bamIndexer != null && indexingFailure == null ? endOfRecordsPointer() : 0;
        outputBinaryCodec.close();
        if (indexingFailure != null) {
            // A distinct exception so that try-with-resources can add it as suppressed to the original
            // without triggering IllegalArgumentException from self-suppression.
            throw new SAMException("BAM indexing failed on " + bamIndexPath, indexingFailure);
        }
        try {
            if (bamIndexer != null) {
                bamIndexer.finish(endOfRecords);
            }
        } catch (Exception e) {
            deleteIndexQuietly(bamIndexPath);
            throw new SAMException("Exception writing BAM index file", e);
        }
    }

    private static void deleteIndexQuietly(final Path indexPath) {
        if (indexPath != null) {
            try {
                Files.deleteIfExists(indexPath);
            } catch (final IOException ignored) {
                // The original exception is more important.
            }
        }
    }

    /**
     * Flushes the BGZF stream and returns the file pointer. samtools ends the file's last chunk at
     * the pointer taken after the final flush, which names the start of the next block; taken before
     * it, the same place is named as the end of this one, and the index would differ from one made
     * by reading the file.
     */
    private long endOfRecordsPointer() {
        try {
            blockCompressedOutputStream.flush();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return blockCompressedOutputStream.getFilePointer();
    }

    /** @return absolute path in URI format, or null if this writer does not correspond to a file.
     * To get a Path from this, use: IOUtil.getPath(getFilename()) */
    @Override
    protected String getFilename() {
        return outputBinaryCodec.getOutputFileName();
    }

    /**
     * Writes a header to a BAM file. samFileHeader and headerText are redundant - one can be used to regenerate the other but in
     * some instances we already have both so this allows us to save some cycles
     */
    protected static void writeHeader(
            final BinaryCodec outputBinaryCodec, final SAMFileHeader samFileHeader, final String headerText) {
        outputBinaryCodec.writeBytes(BAMFileConstants.BAM_MAGIC);

        // The header text is UTF-8, which the spec allows in @CO and in DS and CL values.
        final byte[] headerBytes = headerText.getBytes(StandardCharsets.UTF_8);
        outputBinaryCodec.writeInt(headerBytes.length);
        outputBinaryCodec.writeBytes(headerBytes);

        // write the sequences binarily.  This is redundant with the text header
        outputBinaryCodec.writeInt(samFileHeader.getSequenceDictionary().size());
        for (final SAMSequenceRecord sequenceRecord :
                samFileHeader.getSequenceDictionary().getSequences()) {
            outputBinaryCodec.writeString(sequenceRecord.getSequenceName(), true, true);
            outputBinaryCodec.writeInt(sequenceRecord.getSequenceLength());
        }
    }

    /**
     * Writes a header to a BAM file.
     */
    protected static void writeHeader(final BinaryCodec outputBinaryCodec, final SAMFileHeader samFileHeader) {
        final Writer stringWriter = new StringWriter();
        new SAMTextHeaderCodec().encode(stringWriter, samFileHeader, true);
        final String headerString = stringWriter.toString();
        writeHeader(outputBinaryCodec, samFileHeader, headerString);
    }

    /**
     * Write a BAM file header to an output stream in block compressed BAM format.
     * @param outputStream the stream to write the BAM header to
     * @param samFileHeader the header to write
     */
    public static void writeHeader(final OutputStream outputStream, final SAMFileHeader samFileHeader) {
        final BlockCompressedOutputStream blockCompressedOutputStream =
                new BlockCompressedOutputStream(outputStream, (Path) null);
        final BinaryCodec outputBinaryCodec = new BinaryCodec(blockCompressedOutputStream);
        writeHeader(outputBinaryCodec, samFileHeader);
        try {
            blockCompressedOutputStream.flush();
        } catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }
}
