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

import htsjdk.samtools.util.AsciiWriter;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writer for text-format SAM files.
 */
public class SAMTextWriter extends SAMFileWriterImpl {
    private static final String FIELD_SEPARATOR = "\t";

    private final Writer out;
    // For error reporting only.
    private final Path path;
    private final TextTagCodec tagCodec = new TextTagCodec();

    private final SamFlagField samFlagFieldOutput;

    // Indexing state, null when indexing is not enabled.
    private BAMIndexer bamIndexer;
    private BlockCompressedOutputStream bgzfStream;
    // The same object as out: what is buffered there must reach the BGZF stream before a file pointer means anything.
    private AsciiWriter asciiWriter;
    // A record's chunk starts where the one before ended (htslib's convention, which is how
    // bgzipped SAM is read back). This tracks that boundary.
    private long nextRecordStart;
    private Path indexPath;
    private SAMException indexingFailure;

    /**
     * Constructs a SAMTextWriter that outputs to a Writer.
     * @param out Writer.
     */
    public SAMTextWriter(final Writer out) {
        this(out, SamFlagField.DECIMAL);
    }

    /**
     * Constructs a SAMTextWriter that writes to a Path.
     * @param path Where to write the output.
     */
    public SAMTextWriter(final Path path) {
        this(path, SamFlagField.DECIMAL);
    }

    /**
     * Enables on-the-fly index construction for block-compressed SAM output. Must be called before
     * the header is written (i.e. before {@code setHeader}), so that writing the header can capture
     * the BGZF file pointer marking the start of the first record.
     *
     * @param stream the BGZF stream the AsciiWriter writes to
     * @param indexPath where to write the index
     * @param header the SAM file header
     * @param resolvedType BAI or CSI (never AUTO)
     * @param csiMinShift for a CSI, log2 of the span of its smallest bins
     * @throws IllegalStateException if the writer's output is not an AsciiWriter
     * @throws SAMException if the header is not coordinate-sorted
     */
    void enableIndexConstruction(
            final BlockCompressedOutputStream stream,
            final Path indexPath,
            final SAMFileHeader header,
            final BamIndexType resolvedType,
            final int csiMinShift) {
        if (!(out instanceof AsciiWriter ascii)) {
            throw new IllegalStateException("On-the-fly indexing requires the output to be an AsciiWriter, not "
                    + out.getClass().getName());
        }
        if (!header.getSortOrder().equals(SAMFileHeader.SortOrder.coordinate)) {
            throw new SAMException("Not creating SAM index since not sorted by coordinates: " + header.getSortOrder());
        }
        this.bgzfStream = stream;
        this.asciiWriter = ascii;
        this.indexPath = indexPath;
        this.bamIndexer = new BAMIndexer(indexPath, header, resolvedType, csiMinShift).namingSequencesInCsi();
    }

    /**
     * Returns the Writer used by this instance. When the output is block-compressed, each
     * {@code flush()} ends a BGZF block, so flushing per record makes a block per record;
     * flush rarely or not at all.
     */
    public Writer getWriter() {
        return out;
    }

    /**
     * Constructs a SAMTextWriter that writes to an OutputStream.  The OutputStream
     * is wrapped in an AsciiWriter, which can be retrieved with getWriter().
     * @param stream Need not be buffered because this class provides buffering.
     */
    public SAMTextWriter(final OutputStream stream) {
        this(stream, SamFlagField.DECIMAL);
    }

    /**
     * Constructs a SAMTextWriter that outputs to a Writer.
     * @param out Writer.
     */
    public SAMTextWriter(final Writer out, final SamFlagField samFlagFieldOutput) {
        if (samFlagFieldOutput == null) throw new IllegalArgumentException("Sam flag field was null");
        this.out = out;
        this.path = null;
        this.samFlagFieldOutput = samFlagFieldOutput;
    }

    /**
     * Constructs a SAMTextWriter that writes to a Path.
     * @param path Where to write the output.
     */
    public SAMTextWriter(final Path path, final SamFlagField samFlagFieldOutput) {
        if (samFlagFieldOutput == null) throw new IllegalArgumentException("Sam flag field was null");
        try {
            this.path = path;
            this.out = new AsciiWriter(Files.newOutputStream(path));
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        this.samFlagFieldOutput = samFlagFieldOutput;
    }

    /**
     * Constructs a SAMTextWriter that writes to an OutputStream.  The OutputStream
     * is wrapped in an AsciiWriter, which can be retrieved with getWriter().
     * @param stream Need not be buffered because this class provides buffering.
     */
    public SAMTextWriter(final OutputStream stream, final SamFlagField samFlagFieldOutput) {
        if (samFlagFieldOutput == null) throw new IllegalArgumentException("Sam flag field was null");
        this.path = null;
        this.out = new AsciiWriter(stream);
        this.samFlagFieldOutput = samFlagFieldOutput;
    }

    /**
     * Write the record.
     *
     * @param alignment SAMRecord.
     * @throws IllegalArgumentException if the read name or a Z or A tag value contains a tab, line feed, carriage
     *     return, NUL or a char above 0xFF
     */
    @Override
    public void writeAlignment(final SAMRecord alignment) {
        if (indexingFailure != null) {
            throw new SAMException(
                    "Cannot write further alignments after a SAM indexing failure on " + indexPath, indexingFailure);
        }
        // Checked here, not in writeAlignmentNoNewline, which SAMRecord.toString() uses and which must not throw.
        WritableText.requireInRecord(alignment, WritableText.Destination.SAM_RECORD);
        writeAlignmentNoNewline(alignment);
        try {
            out.write("\n");
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        if (bamIndexer != null) {
            indexAlignment(alignment);
        }
    }

    /**
     * Pushes the record's bytes to the BGZF stream, takes the file pointer, sets the record's
     * source span and hands it to the indexer.
     */
    private void indexAlignment(final SAMRecord alignment) {
        try {
            final long startOffset = nextRecordStart;
            asciiWriter.writeBufferedBytes();
            final long stopOffset = bgzfStream.getFilePointer();
            nextRecordStart = stopOffset;
            alignment.setFileSource(new SAMFileSource(null, new BAMFileSpan(new Chunk(startOffset, stopOffset))));
            bamIndexer.processAlignment(alignment);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        } catch (final Exception e) {
            bamIndexer.abandon();
            deleteIndexQuietly(indexPath);
            indexingFailure = new SAMException("Exception when processing alignment for SAM index " + alignment, e);
            throw indexingFailure;
        }
    }

    /** Writes the alignment fields without a trailing newline. Used by {@link #getSAMString}. */
    private void writeAlignmentNoNewline(final SAMRecord alignment) {
        try {
            out.write(alignment.getReadName());
            out.write(FIELD_SEPARATOR);
            out.write(this.samFlagFieldOutput.format(alignment.getFlags()));
            out.write(FIELD_SEPARATOR);
            out.write(alignment.getReferenceName());
            out.write(FIELD_SEPARATOR);
            out.write(Integer.toString(alignment.getAlignmentStart()));
            out.write(FIELD_SEPARATOR);
            out.write(Integer.toString(alignment.getMappingQuality()));
            out.write(FIELD_SEPARATOR);
            out.write(alignment.getCigarString());
            out.write(FIELD_SEPARATOR);

            //  == is OK here because these strings are interned
            if (alignment.getReferenceName() == alignment.getMateReferenceName()
                    && SAMRecord.NO_ALIGNMENT_REFERENCE_NAME != alignment.getReferenceName()) {
                out.write("=");
            } else {
                out.write(alignment.getMateReferenceName());
            }
            out.write(FIELD_SEPARATOR);
            out.write(Integer.toString(alignment.getMateAlignmentStart()));
            out.write(FIELD_SEPARATOR);
            out.write(Integer.toString(alignment.getInferredInsertSize()));
            out.write(FIELD_SEPARATOR);
            out.write(alignment.getReadString());
            out.write(FIELD_SEPARATOR);
            out.write(alignment.getBaseQualityString());
            SAMBinaryTagAndValue attribute = alignment.getBinaryAttributes();
            while (attribute != null) {
                out.write(FIELD_SEPARATOR);
                final String encodedTag;
                if (attribute.isUnsignedArray()) {
                    encodedTag = tagCodec.encodeUnsignedArray(SAMTag.makeStringTag(attribute.tag), attribute.value);
                } else {
                    encodedTag = tagCodec.encode(SAMTag.makeStringTag(attribute.tag), attribute.value);
                }
                out.write(encodedTag);
                attribute = attribute.getNext();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /* This method is called by SAMRecord.getSAMString(). */
    static String getSAMString(final SAMRecord alignment) {
        final StringWriter stringWriter = new StringWriter();
        new SAMTextWriter(stringWriter).writeAlignmentNoNewline(alignment);
        return stringWriter.toString();
    }

    /**
     * Write the header text.  This method can also be used to write
     * an arbitrary String, not necessarily the header.
     *
     * @param textHeader String containing the text to write, encoded as UTF-8 unless this writer was given a
     *     {@link Writer}, which does its own encoding.
     */
    @Override
    public void writeHeader(final String textHeader) {
        try {
            if (out instanceof AsciiWriter ascii) {
                ascii.writeUtf8(textHeader);
            } else {
                out.write(textHeader);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    protected void writeHeader(final SAMFileHeader header) {
        final StringWriter headerText = new StringWriter();
        new SAMTextHeaderCodec().encode(headerText, header);
        writeHeader(headerText.toString());
        if (bamIndexer != null) {
            try {
                asciiWriter.writeBufferedBytes();
                nextRecordStart = bgzfStream.getFilePointer();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

    /**
     * Do any required flushing here.
     */
    @Override
    public void finish() {
        final long endOfRecords = bamIndexer != null && indexingFailure == null ? endOfRecordsPointer() : 0;
        try {
            out.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        if (indexingFailure != null) {
            // A distinct exception so that try-with-resources can add it as suppressed to the original
            // without triggering IllegalArgumentException from self-suppression.
            throw new SAMException("SAM indexing failed on " + indexPath, indexingFailure);
        }
        try {
            if (bamIndexer != null) {
                bamIndexer.finish(endOfRecords);
            }
        } catch (final Exception e) {
            deleteIndexQuietly(indexPath);
            throw new SAMException("Exception writing SAM index file", e);
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
     * Flushes the AsciiWriter and the BGZF stream, then returns the file pointer. samtools ends
     * the file's last chunk at the pointer taken after the final flush, which names the start of
     * the next block; taken before it, the same place is named as the end of this one, and the
     * index would differ from one made by reading the file.
     */
    private long endOfRecordsPointer() {
        try {
            asciiWriter.writeBufferedBytes();
            bgzfStream.flush();
            return bgzfStream.getFilePointer();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * For producing error messages.
     *
     * @return Output filename, or null if there isn't one.
     */
    @Override
    public String getFilename() {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().toString();
    }
}
