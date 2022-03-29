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

import htsjdk.samtools.util.ProgressLoggerInterface;
import htsjdk.samtools.util.SortingCollection;

import java.io.File;
import java.io.StringWriter;
import static htsjdk.samtools.SAMFileHeader.SortOrder;

/**
 * Base class for implementing SAM writer with any underlying format.
 * Mostly this manages accumulation & sorting of SAMRecords when appropriate,
 * and produces the text version of the header, since that seems to be a popular item
 * in both text and binary file formats.
 */
public abstract class SAMFileWriterImpl implements SAMFileWriter
{
    private static int DEAFULT_MAX_RECORDS_IN_RAM = 500000;      
    private int maxRecordsInRam = DEAFULT_MAX_RECORDS_IN_RAM;
    private SAMFileHeader.SortOrder sortOrder;
    private SAMFileHeader header;
    private SortingCollection<SAMRecord> alignmentSorter;
    private File tmpDir = new File(System.getProperty("java.io.tmpdir"));
    private ProgressLoggerInterface progressLogger = null;
    private boolean isClosed = false;

    // If true, records passed to addAlignment are already in the order specified by sortOrder
    private boolean presorted;

    // For validating presorted records.
    private SAMSortOrderChecker sortOrderChecker;

    /**
     * When writing records that are not presorted, specify the number of records stored in RAM
     * before spilling to disk.  This method sets the default value for all SamFileWriterImpl
     * instances. Must be called before the constructor is called.
     * @param maxRecordsInRam
     */
    public static void setDefaultMaxRecordsInRam(final int maxRecordsInRam) {
        DEAFULT_MAX_RECORDS_IN_RAM = maxRecordsInRam;    
    }
    
    /**
     * When writing records that are not presorted, this number determines the 
     * number of records stored in RAM before spilling to disk.
     * @return DEAFULT_MAX_RECORDS_IN_RAM 
     */
    public static int getDefaultMaxRecordsInRam() {
        return DEAFULT_MAX_RECORDS_IN_RAM;    
    }

    /**
     * Sets the progress logger used by this implementation. Setting this lets this writer emit log
     * messages as SAM records in a SortingCollection are being written to disk.
     */
    @Override
    public void setProgressLogger(final ProgressLoggerInterface progress) {
        this.progressLogger = progress;
    }

    /**
     * Must be called before calling setHeader().  SortOrder value in the header passed
     * to setHeader() is ignored.  If setSortOrder is not called, default is SortOrder.unsorted.
     */
    public void setSortOrder(final SAMFileHeader.SortOrder sortOrder, final boolean presorted) {
        if (header != null) {
            throw new IllegalStateException("Cannot call SAMFileWriterImpl.setSortOrder after setHeader for " +
                    getFilename());
        }
        this.sortOrder = sortOrder;
        this.presorted = presorted;
        setSortOrderChecking(this.sortOrderChecker != null);
    }

    @Override
    public void setSortOrderChecking(boolean check) {
        final boolean doCheck = check &&
                this.presorted &&
                this.sortOrder != SAMFileHeader.SortOrder.unsorted &&
                this.sortOrder != SortOrder.unknown;

        if (doCheck) {
            this.sortOrderChecker = new SAMSortOrderChecker(this.sortOrder);
        }
        else {
            this.sortOrderChecker = null;
        }
    }

    /**
     * Must be called after calling setHeader().
     */
    protected SAMFileHeader.SortOrder getSortOrder() {
        return this.sortOrder;
    }

    /**
     * When writing records that are not presorted, specify the number of records stored in RAM
     * before spilling to disk.  Must be called before setHeader().
     * @param maxRecordsInRam
     */
    protected void setMaxRecordsInRam(final int maxRecordsInRam) {
        if (this.header != null) {
            throw new IllegalStateException("setMaxRecordsInRam must be called before setHeader()");
        }
        this.maxRecordsInRam = maxRecordsInRam;
    }

    protected int getMaxRecordsInRam() {
        return maxRecordsInRam;
    }

    /**
     * When writing records that are not presorted, specify the path of the temporary directory 
     * for spilling to disk.  Must be called before setHeader().
     * @param tmpDir path to the temporary directory
     */
    protected void setTempDirectory(final File tmpDir) {
        if (tmpDir!=null) {
            this.tmpDir = tmpDir;
        }
    }

    protected File getTempDirectory() {
        return tmpDir;
    }

    /**
     * Must be called before addAlignment. Header cannot be null.
     */
    public void setHeader(final SAMFileHeader header)
    {
        if (null == header) {
            throw new IllegalArgumentException("A non-null SAMFileHeader is required for a writer");
        }
        this.header = header;
        if (this.sortOrder == null) {
             this.sortOrder = SAMFileHeader.SortOrder.unsorted;
        }
        header.setSortOrder(this.sortOrder);

        writeHeader(header);

        if (this.presorted) {
            if (this.sortOrder.equals(SAMFileHeader.SortOrder.unsorted)) {
                this.presorted = false;
            }
            setSortOrderChecking(true);
        } else if (!sortOrder.equals(SAMFileHeader.SortOrder.unsorted)) {
            alignmentSorter = SortingCollection.newInstance(SAMRecord.class,
                    new BAMRecordCodec(header), sortOrder.getComparatorInstance(), maxRecordsInRam, tmpDir);
        }
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return header;
    }

    /**
     * Add an alignment record to be emitted by the writer.
     *
     * @param alignment Must not be null. The record will be updated to use the header used by this writer, which will
     *                  in turn cause any unresolved reference and mate reference indices to be resolved against the
     *                  header's sequence dictionary.
     * @throws IllegalArgumentException if the record's reference or mate reference indices cannot be
     * resolved against the writer's header using the current reference and mate reference names
     */
    @Override
    public void addAlignment(final SAMRecord alignment)
    {
        alignment.setHeaderStrict(header); // re-establish the record header and resolve reference indices
        if (sortOrder.equals(SAMFileHeader.SortOrder.unsorted)) {
            writeAlignment(alignment);
        } else if (presorted) {
            assertPresorted(alignment);
            writeAlignment(alignment);
        } else {
            alignmentSorter.add(alignment);
        }
    }

    private void assertPresorted(final SAMRecord alignment) {
        if (this.sortOrderChecker != null && !sortOrderChecker.isSorted(alignment)) {
            final SAMRecord prev = sortOrderChecker.getPreviousRecord();
            throw new IllegalArgumentException("Alignments added out of order in SAMFileWriterImpl.addAlignment for " +
                    getFilename() + ". Sort order is " + this.sortOrder + ". Offending records are at ["
                    + sortOrderChecker.getSortKey(prev) + "] and ["
                    + sortOrderChecker.getSortKey(alignment) + "]");
        }
    }

    /**
     * Must be called or else file will likely be defective.
     */
    @Override
    public final void close()
    {
        try {
            if (!isClosed) {
                if (alignmentSorter != null) {
                    try {
                        for (final SAMRecord alignment : alignmentSorter) {
                            writeAlignment(alignment);
                            if (progressLogger != null)
                                progressLogger.record(alignment);
                        }
                    } finally {
                        alignmentSorter.cleanup();
                    }
                }
                finish();
            }
        } finally {
            isClosed = true;
        }
    }

    /**
     * Writes the record to disk.  Sort order has been taken care of by the time
     * this method is called. The record must hava a non-null SAMFileHeader.
     * @param alignment
     */
    abstract protected void writeAlignment(SAMRecord alignment);

    /**
     * Write the header to disk.  Header object is available via getHeader().
     * @param textHeader for convenience if the implementation needs it.
     * @deprecated since 06/2018. {@link #writeHeader(SAMFileHeader)} is preferred for avoid String construction if not need it.
     */
    @Deprecated
    abstract protected void writeHeader(String textHeader);

    /**
     * Write the header to disk. Header object is available via getHeader().
     *
     * <p>IMPORTANT: this method will be abstract once {@link #writeHeader(String)} is removed.
     *
     * <p>Note: default implementation uses {@link SAMTextHeaderCodec#encode} and calls
     * {@link #writeHeader(String)}.
     *
     * @param header object to write.
     */
    protected void writeHeader(final SAMFileHeader header) {
        final StringWriter headerTextBuffer = new StringWriter();
        new SAMTextHeaderCodec().encode(headerTextBuffer, header);
        writeHeader(headerTextBuffer.toString());
    }

    /**
     * Do any required flushing here.
     */
    abstract protected void finish();

    /**
     * For producing error messages.
     * @return Output filename, or null if there isn't one.
     */
    abstract protected String getFilename();
}
