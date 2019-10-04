/*******************************************************************************
 * Copyright 2013-2016 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License countingInputStream distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools;

import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.cram.build.CRAMReferenceState;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.build.CramSpanContainerIterator;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.Closeable;
import java.io.InputStream;
import java.util.*;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.util.RuntimeIOException;

public class CRAMIterator implements SAMRecordIterator, Closeable {
    private final CountingInputStream countingInputStream;
    private final CramHeader cramHeader;

    private final CRAMReferenceState cramReferenceState;

    //TODO: this should have a better (common) type (ContainerIterator??)
    private final Iterator<Container> containerIterator;

    private List<SAMRecord> samRecords;
    private Container container;
    private SamReader mReader;
    private final long firstContainerOffset;

    // Keep a cache of re-usable compressor instances to reduce the need to repeatedly reallocate
    // large numbers of small temporary objects, especially for the RANS compressor, which
    // allocates ~256k small objects every time its instantiated.
    private final CompressorCache compressorCache = new CompressorCache();

    /**
     * `samRecordIndex` only used when validation is not `SILENT`
     * (for identification by the validator which records are invalid)
     */
    private long samRecordIndex;
    private Iterator<SAMRecord> iterator = Collections.EMPTY_LIST.iterator();
    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    public CRAMIterator(final InputStream inputStream,
                        final CRAMReferenceSource referenceSource,
                        final ValidationStringency validationStringency) {
        this.countingInputStream = new CountingInputStream(inputStream);
        this.validationStringency = validationStringency;
        final CramContainerIterator containerIterator = new CramContainerIterator(this.countingInputStream);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = this.countingInputStream.getCount();
        //TODO: this needs a smarter initializer param (don't need encoding strategy here)
        samRecords = new ArrayList<>(new CRAMEncodingStrategy().getReadsPerSlice());
        cramReferenceState = new CRAMReferenceState(referenceSource, cramHeader.getSamFileHeader());
    }

    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final long[] coordinates,
                        final ValidationStringency validationStringency) {
        this.countingInputStream = new CountingInputStream(seekableStream);
        this.validationStringency = validationStringency;
        final CramSpanContainerIterator containerIterator = CramSpanContainerIterator.fromFileSpan(seekableStream, coordinates);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = containerIterator.getFirstContainerOffset();
        //TODO: this needs a smarter initializer param (don't need encoding strategy here)
        samRecords = new ArrayList<>(new CRAMEncodingStrategy().getReadsPerSlice());
        cramReferenceState = new CRAMReferenceState(referenceSource, cramHeader.getSamFileHeader());
    }

    @Deprecated
    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final long[] coordinates) {
        this(seekableStream, referenceSource, coordinates, ValidationStringency.DEFAULT_STRINGENCY);
    }

    private void nextContainer() throws IllegalArgumentException, CRAMException {

        if (containerIterator != null) {
            if (!containerIterator.hasNext()) {
                samRecords.clear();
                return;
            }
            container = containerIterator.next();
            if (container.isEOF()) {
                samRecords.clear();
                return;
            }
        } else {
            final long containerByteOffset = countingInputStream.getCount();
            container = new Container(cramHeader.getVersion(), countingInputStream, containerByteOffset);
            if (container.isEOF()) {
                samRecords.clear();
                return;
            }
        }

        samRecords = container.getSAMRecords(
                validationStringency,
                cramReferenceState,
                compressorCache,
                getSAMFileHeader());
        iterator = samRecords.iterator();
    }

    /**
     * Skip cached records until given alignment start position.
     *
     * @param refIndex reference sequence index
     * @param pos      alignment start to skip to
     */
    //TODO: this should first select the correct slice so we don't decode all slices unnecessarily
    public boolean advanceToAlignmentInContainer(final int refIndex, final int pos) {
        if (!hasNext()) return false;
        int i = 0;
        for (final SAMRecord record : samRecords) {
            if (refIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && record.getReferenceIndex() != refIndex) {
                continue;
            }

            if (pos <= 0) {
                if (record.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                    iterator = samRecords.listIterator(i);
                    return true;
                }
            } else {
                if (record.getAlignmentStart() >= pos) {
                    iterator = samRecords.listIterator(i);
                    return true;
                }
            }
            i++;
        }
        iterator = Collections.EMPTY_LIST.iterator();
        return false;
    }

    @Override
    public boolean hasNext() {
        if (container != null && container.isEOF()) return false;
        if (!iterator.hasNext()) {
            nextContainer();
        }

        return !samRecords.isEmpty();
    }

    @Override
    public SAMRecord next() {
        if (hasNext()) {
            SAMRecord samRecord = iterator.next();
            if (validationStringency != ValidationStringency.SILENT) {
                SAMUtils.processValidationErrors(samRecord.isValid(), samRecordIndex++, validationStringency);
            }
            return samRecord;
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void remove() {
        throw new RuntimeException("Removal of records not implemented.");
    }

    @Override
    public void close() {
        samRecords.clear();
        try {
            if (countingInputStream != null) {
                countingInputStream.close();
            }
        } catch (final RuntimeIOException e) { }
    }

    public long getFirstContainerOffset() {
        return firstContainerOffset;
    }

    @Override
    public SAMRecordIterator assertSorted(final SortOrder sortOrder) {
        return SamReader.AssertingIterator.of(this).assertSorted(sortOrder);
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    public SamReader getFileSource() {
        return mReader;
    }

    public void setFileSource(final SamReader mReader) {
        this.mReader = mReader;
    }

    public SAMFileHeader getSAMFileHeader() {
        return cramHeader.getSamFileHeader();
    }

}
