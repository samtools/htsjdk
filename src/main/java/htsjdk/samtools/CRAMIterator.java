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
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.build.CramSpanContainerIterator;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.Closeable;
import java.io.InputStream;
import java.util.*;

import htsjdk.samtools.util.RuntimeIOException;

public class CRAMIterator implements SAMRecordIterator, Closeable {
    private final CountingInputStream countingInputStream;
    private final CramContainerIterator containerIterator;
    private final CramHeader cramHeader;
    private final SAMFileHeader samFileHeader;
    private final CRAMReferenceRegion cramReferenceState;
    private final QueryInterval[] queryIntervals;

    private ValidationStringency validationStringency;
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
    private Iterator<SAMRecord> samRecordIterator = Collections.EMPTY_LIST.iterator();

    public CRAMIterator(final InputStream inputStream,
                        final CRAMReferenceSource referenceSource,
                        final ValidationStringency validationStringency) {
        this.countingInputStream = new CountingInputStream(inputStream);
        this.containerIterator = new CramContainerIterator(this.countingInputStream);

        this.validationStringency = validationStringency;
        samFileHeader = containerIterator.getSamFileHeader();
        cramReferenceState = new CRAMReferenceRegion(referenceSource, samFileHeader);
        cramHeader = containerIterator.getCramHeader();
        firstContainerOffset = this.countingInputStream.getCount();
        samRecords = new ArrayList<>(new CRAMEncodingStrategy().getReadsPerSlice());
        this.queryIntervals = null;
    }

    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final ValidationStringency validationStringency,
                        final QueryInterval[] queryIntervals,
                        final long[] coordinates) {
        this.countingInputStream = new CountingInputStream(seekableStream);
        this.containerIterator = CramSpanContainerIterator.fromFileSpan(seekableStream, coordinates);

        this.validationStringency = validationStringency;
        samFileHeader = containerIterator.getSamFileHeader();
        cramReferenceState = new CRAMReferenceRegion(referenceSource, samFileHeader);
        cramHeader = containerIterator.getCramHeader();
        firstContainerOffset = this.countingInputStream.getCount();
        samRecords = new ArrayList<>(new CRAMEncodingStrategy().getReadsPerSlice());
        this.queryIntervals = queryIntervals;
    }

    private BAMIteratorFilter.FilteringIteratorState nextContainer() {
        if (containerIterator != null) {
            if (!containerIterator.hasNext()) {
                samRecords.clear();
                return BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION;
            }
            container = containerIterator.next();
            if (container.isEOF()) {
                samRecords.clear();
                return BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION;
            }
        } else {
            final long containerByteOffset = countingInputStream.getCount();
            container = new Container(cramHeader.getCRAMVersion(), countingInputStream, containerByteOffset);
            if (container.isEOF()) {
                samRecords.clear();
                return BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION;
            }
        }

        if (containerMatchesQuery(container)) {
            samRecords = container.getSAMRecords(
                    validationStringency,
                    cramReferenceState,
                    compressorCache,
                    getSAMFileHeader());
            samRecordIterator = samRecords.iterator();
            return BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER;
        } else {
            return BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION;
        }
    }

    private boolean containerMatchesQuery(final Container container) {
        if (queryIntervals == null) {
            return true;
        } else {
            // binary search our query intervals to see if the alignment span of this container
            // overlaps any query - it doesn't matter which one, we only care whether or not there is a match
            final AlignmentContext alignmentContext = container.getAlignmentContext();
            return (!alignmentContext.getReferenceContext().isMappedSingleRef() ||
                Arrays.binarySearch(
                    queryIntervals,
                    new QueryInterval(
                            alignmentContext.getReferenceContext().getReferenceContextID(),
                            alignmentContext.getAlignmentStart(),
                            alignmentContext.getAlignmentStart() + alignmentContext.getAlignmentSpan() - 1
                    ),
                    overlapsContainerSpan) >= 0);
        }
    }

    //TODO: this should filter at the slice level!
    //we don't actually care which QueryInterval overlaps with the container; we just want to know if there is one...
    private final static Comparator<QueryInterval> overlapsContainerSpan = (queryInterval, containerInterval) -> {
        int comp = queryInterval.referenceIndex - containerInterval.referenceIndex;
        if (comp != 0) {
            return comp;
        }
        if (queryInterval.end <= 0) {
            // our query interval specifies a symbolic end, so call it a match if the container span
            // overlaps the start of the queryInterval
            return containerInterval.end <= queryInterval.start ?
                    -1 :
                    0;
        } else if (containerInterval.overlaps(queryInterval)) {
            return 0; // there is overlap so call it a match
        }
        return queryInterval.compareTo(containerInterval);
    };

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
                    samRecordIterator = samRecords.listIterator(i);
                    return true;
                }
            } else {
                if (record.getAlignmentStart() >= pos) {
                    samRecordIterator = samRecords.listIterator(i);
                    return true;
                }
            }
            i++;
        }
        samRecordIterator = Collections.EMPTY_LIST.iterator();
        return false;
    }

    @Override
    public boolean hasNext() {
        if (container != null && container.isEOF()) {
            return false;
        }

        if (!samRecordIterator.hasNext()) {
            BAMIteratorFilter.FilteringIteratorState nextContainerPasses =
                    BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION;
            while (nextContainerPasses == BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION){
                nextContainerPasses = nextContainer();
            }
            return nextContainerPasses == BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER;
        }

        return !samRecords.isEmpty();
    }

    @Override
    public SAMRecord next() {
        if (hasNext()) {
            SAMRecord samRecord = samRecordIterator.next();
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
        return samFileHeader;
    }

}
