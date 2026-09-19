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

    // Indexing state: bgzfStream is set before the header is written and the indexer is created after.
    private BAMIndexer bamIndexer;
    private BlockCompressedOutputStream bgzfStream;
    private Path indexPath;
    private BamIndexType indexType;
    private int indexCsiMinShift;
    private long nextRecordStart;

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
     * @param resolvedType BAI or CSI (never AUTO)
     * @param csiMinShift for a CSI, log2 of the span of its smallest bins
     */
    void enableIndexConstruction(
            final BlockCompressedOutputStream stream,
            final Path indexPath,
            final BamIndexType resolvedType,
            final int csiMinShift) {
        this.bgzfStream = stream;
        this.indexPath = indexPath;
        this.indexType = resolvedType;
        this.indexCsiMinShift = csiMinShift;
    }

    /**
     * Returns the Writer used by this instance.  Useful for flushing the output.
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
     */
    @Override
    public void writeAlignment(final SAMRecord alignment) {
        if (bamIndexer != null) {
            try {
                final long startOffset = nextRecordStart;
                writeAlignmentNoNewline(alignment);
                out.write("\n");
                ((AsciiWriter) out).writeBufferedBytes();
                final long stopOffset = bgzfStream.getFilePointer();
                nextRecordStart = stopOffset;
                alignment.setFileSource(new SAMFileSource(null, new BAMFileSpan(new Chunk(startOffset, stopOffset))));
                bamIndexer.processAlignment(alignment);
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            } catch (final Exception e) {
                bamIndexer = null;
                throw new SAMException("Exception when processing alignment for SAM index " + alignment, e);
            }
        } else {
            writeAlignmentNoNewline(alignment);
            try {
                out.write("\n");
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
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
     * @param textHeader String containing the text to write.
     */
    @Override
    public void writeHeader(final String textHeader) {
        try {
            out.write(textHeader);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    protected void writeHeader(final SAMFileHeader header) {
        new SAMTextHeaderCodec().encode(out, header);
        if (bgzfStream != null && indexPath != null) {
            try {
                ((AsciiWriter) out).writeBufferedBytes();
                nextRecordStart = bgzfStream.getFilePointer();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
            if (!header.getSortOrder().equals(SAMFileHeader.SortOrder.coordinate)) {
                throw new SAMException(
                        "Not creating SAM index since not sorted by coordinates: " + header.getSortOrder());
            }
            bamIndexer = new BAMIndexer(indexPath, header, indexType, indexCsiMinShift).namingSequencesInCsi();
        }
    }

    /**
     * Do any required flushing here.
     */
    @Override
    public void finish() {
        final long endOfRecords;
        if (bamIndexer != null) {
            try {
                ((AsciiWriter) out).writeBufferedBytes();
                bgzfStream.flush();
                endOfRecords = bgzfStream.getFilePointer();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        } else {
            endOfRecords = 0;
        }
        try {
            out.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        try {
            if (bamIndexer != null) {
                bamIndexer.finish(endOfRecords);
            }
        } catch (final Exception e) {
            throw new SAMException("Exception writing SAM index file", e);
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
