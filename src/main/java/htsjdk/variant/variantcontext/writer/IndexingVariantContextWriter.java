/*
 * Copyright (c) 2012 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.LocationAware;
import htsjdk.samtools.util.PositionalOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.index.DynamicIndexCreator;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * this class writes VCF files
 */
abstract class IndexingVariantContextWriter implements VariantContextWriter {
    private final String name;
    private final Path location;
    private final SAMSequenceDictionary refDict;

    private OutputStream outputStream;
    private LocationAware locationSource = null;
    private IndexCreator indexer = null;

    // The contig and start of the last record indexed, and the contigs whose records are finished
    private String lastContig = null;
    private int lastStart;
    private final Set<String> finishedContigs = new HashSet<>();

    private boolean closed;

    private IndexingVariantContextWriter(
            final String name, final Path location, final OutputStream output, final SAMSequenceDictionary refDict) {
        this.name = name;
        this.location = location;
        this.outputStream = output;
        this.refDict = refDict;
    }

    static String DEFAULT_READER_NAME = "Reader Name";

    /**
     * Create a VariantContextWriter with an associated index using the default index creator
     *
     * @param name  the name of this writer (i.e. the file name or stream)
     * @param location  the path to the output file
     * @param output    the output stream to write to
     * @param refDict   the reference dictionary
     * @param enableOnTheFlyIndexing    is OTF indexing enabled?
     */
    protected IndexingVariantContextWriter(
            final String name,
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing) {
        this(name, location, output, refDict);

        if (enableOnTheFlyIndexing) {
            initIndexingWriter(new DynamicIndexCreator(location, IndexFactory.IndexBalanceApproach.FOR_SEEK_TIME));
        }
    }

    /**
     * Create a VariantContextWriter with an associated index using a custom index creator
     *
     * @param name  the name of this writer (i.e. the file name or stream)
     * @param location  the path to the output file
     * @param output    the output stream to write to
     * @param refDict   the reference dictionary
     * @param enableOnTheFlyIndexing    is OTF indexing enabled?
     * @param idxCreator    the custom index creator.  NOTE: must be initialized
     */
    protected IndexingVariantContextWriter(
            final String name,
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing,
            final IndexCreator idxCreator) {
        this(name, location, output, refDict);

        if (enableOnTheFlyIndexing) {
            // TODO: Handle non-Tribble IndexCreators
            initIndexingWriter(idxCreator);
        }
    }

    private void initIndexingWriter(final IndexCreator idxCreator) {
        indexer = idxCreator;
        if (outputStream instanceof LocationAware) {
            locationSource = (LocationAware) outputStream;
        } else {
            final PositionalOutputStream positionalOutputStream = new PositionalOutputStream(outputStream);
            locationSource = positionalOutputStream;
            outputStream = positionalOutputStream;
        }
    }

    /** return true is the underlying stream is a PrintStream and
     * its checkError returned true. Used to stop linux pipelines
     */
    @Override
    public boolean checkError() {
        return (getOutputStream() instanceof PrintStream)
                && PrintStream.class.cast(getOutputStream()).checkError();
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public String getStreamName() {
        return name;
    }

    @Override
    public abstract void writeHeader(VCFHeader header);

    /**
     * attempt to close the VCF file; a second call does nothing
     */
    @Override
    public void close() {
        // Set before the work, so that a close that fails part way is not retried
        if (closed) return;
        closed = true;
        try {
            // close the underlying output stream
            outputStream.close();

            // close the index stream (keep it separate to help debugging efforts)
            if (indexer != null) {
                indexer.setIndexSequenceDictionary(refDict);
                final Index index = indexer.finalizeIndex(locationSource.getPosition());
                index.writeBasedOnFeaturePath(location);
            }

        } catch (final IOException e) {
            throw new RuntimeIOException("Unable to close index for " + getStreamName(), e);
        }
    }

    /**
     * @return whether {@link #close()} has run, so that a subclass's own closing work is done only once
     */
    protected final boolean isClosed() {
        return closed;
    }

    /**
     * @return the reference sequence dictionary used for the variant contexts being written
     */
    public SAMSequenceDictionary getRefDict() {
        return refDict;
    }

    /**
     * add a record to the file
     *
     * <p>With on-the-fly indexing, records must be sorted by start within each contig and each contig's records must
     * be contiguous. The contigs may otherwise come in any order, except that a BGZF BCF writer, which indexes with a
     * CSI, also needs them in reference-dictionary order and refuses a record out of it (see {@link BCF2Writer#add}).
     *
     * @param vc      the Variant Context object
     * @throws IllegalArgumentException with on-the-fly indexing, if the record starts before the last one on its
     *     contig, or another contig's records have come since its contig's; the record is neither indexed nor
     *     written, so the caller may carry on with a correctly ordered one
     */
    @Override
    public void add(final VariantContext vc) {
        // if we are doing on the fly indexing, add the record ***before*** we write any bytes
        if (indexer != null) {
            final String contig = vc.getContig();
            final int start = vc.getStart();
            final boolean sameContig = contig.equals(lastContig);
            // Checked before any indexer, so that all refuse alike: the Tribble linear index would otherwise start a
            // new per-contig index on each return to a contig and, binning only forwards, miss a record that goes back
            if (sameContig && start < lastStart) {
                throw new IllegalArgumentException("Records are not coordinate-sorted: " + contig + ":" + start
                        + " follows " + contig + ":" + lastStart
                        + "; sort them, or build the writer without Options.INDEX_ON_THE_FLY");
            }
            if (!sameContig && finishedContigs.contains(contig)) {
                throw new IllegalArgumentException("Records are not coordinate-sorted: " + contig + ":" + start
                        + " follows " + lastContig + ":" + lastStart + ", but " + contig
                        + " came earlier and each contig's records must be contiguous"
                        + "; sort them, or build the writer without Options.INDEX_ON_THE_FLY");
            }
            indexer.addFeature(vc, locationSource.getPosition());
            if (!sameContig && lastContig != null) finishedContigs.add(lastContig);
            lastContig = contig;
            lastStart = start;
        }
    }

    /**
     * Returns a reasonable "name" for this writer, to display to the user if something goes wrong
     *
     * @param location
     * @param stream
     * @return
     */
    protected static final String writerName(final Path location, final OutputStream stream) {
        return location == null
                ? stream == null ? DEFAULT_READER_NAME : stream.toString()
                : location.toAbsolutePath().toUri().toString();
    }
}
