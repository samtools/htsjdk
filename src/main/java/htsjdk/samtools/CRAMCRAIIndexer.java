package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Indexer for creating/reading/writing a CRAIIndex for a CRAM file/stream. There
 * are three ways to obtain an index:
 * </p><ul>
 * <li>create an index for an entire CRAM stream and write it to an output stream</li>
 * <li>create an index on-the-fly by processing one container at a time</li>
 * <li>read an existing index from an input stream</li>
 * </ul><p>
 */
public class CRAMCRAIIndexer implements CRAMIndexer {

    final private CRAIIndex craiIndex = new CRAIIndex();
    final private GZIPOutputStream os;

    /**
     * Create a CRAMCRAIIndexer that writes to the given output stream.
     *
     * @param os output stream to which the index will be written
     * @param samHeader SAMFileHeader - user to verify sort order
     */
    public CRAMCRAIIndexer(final OutputStream os, final SAMFileHeader samHeader) {
        if (samHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException("CRAM file must be coordinate-sorted for indexing.");
        }
        try {
            this.os = new GZIPOutputStream(new BufferedOutputStream(os));
        }
        catch (IOException e) {
            throw new RuntimeIOException("Error opening CRAI index output stream");
        }
    }

    /**
     * Create a CRAMCRAIIndexer that writes to the given output stream,
     * initialized with a Collection of {@link CRAIEntry} objects.
     *
     * @param os output stream to which the index will be written
     * @param samHeader SAMFileHeader - user to verify sort order
     * @param entries the CRAI entries to index
     */
    public CRAMCRAIIndexer(final OutputStream os,
                           final SAMFileHeader samHeader,
                           final Collection<CRAIEntry> entries) {
        this(os, samHeader);
        craiIndex.addEntries(entries);
    }

    /**
     * Create index entries for a single container.
     * @param container the container to index
     */
    public void processContainer(final Container container) {
        craiIndex.processContainer(container);
    }

    @Override
    public void processContainer(final Container container, final ValidationStringency validationStringency) {
        processContainer(container);
    }

    /**
     * Finish creating the index by writing the accumulated entries out to the stream.
     */
    @Override
    public void finish() {
        try {
            craiIndex.writeIndex(os);
            os.flush();
            os.close();
        }
        catch (IOException e) {
            throw new RuntimeIOException("Error writing CRAI index to output stream");
        }
    }

    /**
     * Generate and write a CRAI index to an output stream from a CRAM input stream
     *
     * @param cramStream CRAM stream to index; must be coordinate sorted
     * @param craiStream stream for output index
     */
    public static void writeIndex(final SeekableStream cramStream, OutputStream craiStream) {
        final CramHeader cramHeader = CramIO.readCramHeader(cramStream);
        final CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(craiStream, cramHeader.getSamFileHeader());
        final Version cramVersion = cramHeader.getVersion();

        // get the first container
        Container container = ContainerIO.readContainer(cramVersion, cramStream);

        while (container != null && !container.isEOF()) {
            indexer.processContainer(container);
            container = ContainerIO.readContainer(cramVersion, cramStream);
        }

        indexer.finish();
    }

    /**
     * Read an input stream containing a .crai index and return a CRAIIndex object.
     * @param is Input stream to read
     * @return A CRAIIndex object representing the index.
     */
    public static CRAIIndex readIndex(final InputStream is) {
        CRAIIndex craiIndex = new CRAIIndex();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new GZIPInputStream(is));
            while (scanner.hasNextLine()) {
                final String line = scanner.nextLine();
                craiIndex.addEntry(new CRAIEntry(line));
            }
        }
        catch (IOException e) {
            throw new RuntimeIOException("Error reading CRAI index from output stream");
        }
        finally {
            if (null != scanner) {
                scanner.close();
            }
        }

        return craiIndex;
    }

}