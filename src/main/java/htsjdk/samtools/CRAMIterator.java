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
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.build.CramSpanContainerIterator;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.util.RuntimeIOException;

public class CRAMIterator implements SAMRecordIterator {
    
    private final CountingInputStream countingInputStream;
    private final CramHeader cramHeader;
    private final ArrayList<SAMRecord> records;
    private final CramNormalizer normalizer;
    private byte[] referenceBases;
    private int prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;

    //TODO: encapsulate this
    public Container container;
    private SamReader mReader;
    long firstContainerOffset = 0;
    private final Iterator<Container> containerIterator;
    /**
     * `samRecordIndex` only used when validation is not `SILENT`
     * (for identification by the validator which records are invalid)
     */
    private long samRecordIndex;
    private List<CRAMRecord> cramRecords;

    private final CRAMReferenceSource referenceSource;

    private Iterator<SAMRecord> iterator = Collections.<SAMRecord>emptyList().iterator();

    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    public CRAMIterator(final InputStream inputStream,
                        final CRAMReferenceSource referenceSource,
                        final ValidationStringency validationStringency) {
        this.countingInputStream = new CountingInputStream(inputStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        final CramContainerIterator containerIterator = new CramContainerIterator(this.countingInputStream);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = this.countingInputStream.getCount();
        //TODO: this needs a smarter initializer param (don't need encoding strategy here)
        records = new ArrayList<>(new CRAMEncodingStrategy().getRecordsPerSlice());
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(), referenceSource);
    }

    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final long[] coordinates,
                        final ValidationStringency validationStringency) {
        this.countingInputStream = new CountingInputStream(seekableStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        final CramSpanContainerIterator containerIterator = CramSpanContainerIterator.fromFileSpan(seekableStream, coordinates);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = containerIterator.getFirstContainerOffset();
        //TODO: this needs a smarter initializer param (don't need encoding strategy here)
        records = new ArrayList<>(new CRAMEncodingStrategy().getRecordsPerSlice());
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(), referenceSource);
    }

    @Deprecated
    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final long[] coordinates) {
        this(seekableStream, referenceSource, coordinates, ValidationStringency.DEFAULT_STRINGENCY);
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    void nextContainer() throws IllegalArgumentException, CRAMException {

        if (containerIterator != null) {
            if (!containerIterator.hasNext()) {
                records.clear();
                return;
            }
            container = containerIterator.next();
            if (container.isEOF()) {
                records.clear();
                return;
            }
        } else {
            final long containerByteOffset = countingInputStream.getCount();
            container = new Container(cramHeader.getVersion(), countingInputStream, containerByteOffset);
            if (container.isEOF()) {
                records.clear();
                return;
            }
        }

        records.clear();
        cramRecords = container.getCRAMRecords(validationStringency);

        final ReferenceContext containerContext = container.getReferenceContext();
        switch (containerContext.getType()) {
            case UNMAPPED_UNPLACED_TYPE:
                referenceBases = new byte[]{};
                prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
                break;
            case MULTIPLE_REFERENCE_TYPE:
                referenceBases = null;
                prevSeqId = ReferenceContext.MULTIPLE_REFERENCE_ID;
                break;
            default:
                if (prevSeqId != containerContext.getSequenceId()) {
                    final SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                            .getSequence(containerContext.getSequenceId());
                    referenceBases = referenceSource.getReferenceBases(sequence, true);
                    if (referenceBases == null) {
                        throw new CRAMException(String.format("Contig %s not found in the reference file.", sequence.getSequenceName()));
                    }
                    prevSeqId = containerContext.getSequenceId();
                }
        }

        for (final Slice slice : container.getSlices()) {
            final ReferenceContext sliceContext = slice.getReferenceContext();

            if (! sliceContext.isMappedSingleRef())
                continue;

            if (!slice.validateRefMD5(referenceBases)) {
                final String msg = String.format(
                        "Reference sequence MD5 mismatch for slice: sequence id %d, start %d, span %d, expected MD5 %s",
                        sliceContext.getSequenceId(),
                        slice.getAlignmentStart(),
                        slice.getAlignmentSpan(),
                        String.format("%032x", new BigInteger(1, slice.getRefMD5())));
                throw new CRAMException(msg);
            }
        }

        normalizer.normalize(cramRecords, referenceBases, 0, container.getCompressionHeader().substitutionMatrix);

        for (final CRAMRecord cramRecord : cramRecords) {
            final SAMRecord samRecord = cramRecord.toSAMRecord(cramHeader.getSamFileHeader());
            if (!cramRecord.isSegmentUnmapped()) {
                final SAMSequenceRecord sequence = cramHeader.getSamFileHeader().getSequence(cramRecord.getReferenceIndex());
                referenceBases = referenceSource.getReferenceBases(sequence, true);
            }

            samRecord.setValidationStringency(validationStringency);

            //TODO:
            if (mReader != null) {
                final long chunkStart = (container.getContainerByteOffset() << 16) | cramRecord.getSliceIndex();
                final long chunkEnd = ((container.getContainerByteOffset() << 16) | cramRecord.getSliceIndex()) + 1;
                samRecord.setFileSource(new SAMFileSource(mReader, new BAMFileSpan(new Chunk(chunkStart, chunkEnd))));
            }
            
            records.add(samRecord);
        }
        cramRecords.clear();
        iterator = records.iterator();
    }

    /**
     * Skip cached records until given alignment start position.
     *
     * @param refIndex reference sequence index
     * @param pos      alignment start to skip to
     */
    public boolean advanceToAlignmentInContainer(final int refIndex, final int pos) {
        if (!hasNext()) return false;
        int i = 0;
        for (final SAMRecord record : records) {
            if (refIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && record.getReferenceIndex() != refIndex) continue;

            if (pos <= 0) {
                if (record.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                    iterator = records.listIterator(i);
                    return true;
                }
            } else {
                if (record.getAlignmentStart() >= pos) {
                    iterator = records.listIterator(i);
                    return true;
                }
            }
            i++;
        }
        iterator = Collections.<SAMRecord>emptyList().iterator();
        return false;
    }

    @Override
    public boolean hasNext() {
        if (container != null && container.isEOF()) return false;
        if (!iterator.hasNext()) {
            nextContainer();
        }

        return !records.isEmpty();
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
        records.clear();
        //noinspection EmptyCatchBlock
        try {
            if (countingInputStream != null) {
                countingInputStream.close();
            }
        } catch (final RuntimeIOException e) { }
    }

    @Override
    public SAMRecordIterator assertSorted(final SortOrder sortOrder) {
        return SamReader.AssertingIterator.of(this).assertSorted(sortOrder);
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
