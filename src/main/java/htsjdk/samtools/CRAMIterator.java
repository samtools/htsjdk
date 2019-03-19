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
import htsjdk.samtools.cram.build.ContainerParser;
import htsjdk.samtools.cram.build.Cram2SamRecordFactory;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.build.CramSpanContainerIterator;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
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
    public Container container;
    private SamReader mReader;
    long firstContainerOffset = 0;
    private final Iterator<Container> containerIterator;

    private final ContainerParser parser;
    private final CRAMReferenceSource referenceSource;

    private Iterator<SAMRecord> iterator = Collections.<SAMRecord>emptyList().iterator();

    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    /**
     * `samRecordIndex` only used when validation is not `SILENT`
     * (for identification by the validator which records are invalid)
     */
    private long samRecordIndex;
    private ArrayList<CramCompressionRecord> cramRecords;

    public CRAMIterator(final InputStream inputStream,
                        final CRAMReferenceSource referenceSource,
                        final ValidationStringency validationStringency) {
        if (null == referenceSource) {
            throw new CRAMException("A reference source is required for CRAM files");
        }

        this.countingInputStream = new CountingInputStream(inputStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        final CramContainerIterator containerIterator = new CramContainerIterator(this.countingInputStream);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = this.countingInputStream.getCount();
        records = new ArrayList<>(CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE);
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(),
                referenceSource);
        parser = new ContainerParser(cramHeader.getSamFileHeader());
    }

    public CRAMIterator(final SeekableStream seekableStream,
                        final CRAMReferenceSource referenceSource,
                        final long[] coordinates,
                        final ValidationStringency validationStringency) {
        if (null == referenceSource) {
            throw new CRAMException("A reference source is required for CRAM files");
        }

        this.countingInputStream = new CountingInputStream(seekableStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        final CramSpanContainerIterator containerIterator = CramSpanContainerIterator.fromFileSpan(seekableStream, coordinates);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = containerIterator.getFirstContainerOffset();
        records = new ArrayList<>(CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE);
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(),
                referenceSource);
        parser = new ContainerParser(cramHeader.getSamFileHeader());
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
            container = ContainerIO.readContainer(cramHeader.getVersion(), countingInputStream);
            if (container.isEOF()) {
                records.clear();
                return;
            }
        }

        records.clear();
        if (cramRecords == null)
            cramRecords = new ArrayList<>(container.nofRecords);
        else
            cramRecords.clear();

        parser.getRecords(container, cramRecords, validationStringency);

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

        for (int i = 0; i < container.slices.length; i++) {
            final Slice slice = container.slices[i];
            final ReferenceContext sliceContext = slice.getReferenceContext();

            if (! sliceContext.isMappedSingleRef())
                continue;

            if (!slice.validateRefMD5(referenceBases)) {
                final String msg = String.format(
                        "Reference sequence MD5 mismatch for slice: sequence id %d, start %d, span %d, expected MD5 %s",
                        sliceContext.getSequenceId(),
                        slice.alignmentStart,
                        slice.alignmentSpan,
                        String.format("%032x", new BigInteger(1, slice.refMD5)));
                throw new CRAMException(msg);
            }
        }

        normalizer.normalize(cramRecords, referenceBases, 0,
                container.compressionHeader.substitutionMatrix);

        final Cram2SamRecordFactory cramToSamRecordFactory = new Cram2SamRecordFactory(
                cramHeader.getSamFileHeader());

        for (final CramCompressionRecord cramRecord : cramRecords) {
            final SAMRecord samRecord = cramToSamRecordFactory.create(cramRecord);
            if (!cramRecord.isSegmentUnmapped()) {
                final SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                        .getSequence(cramRecord.referenceContext.getSequenceId());
                referenceBases = referenceSource.getReferenceBases(sequence, true);
            }

            samRecord.setValidationStringency(validationStringency);

            if (mReader != null) {
                final long chunkStart = (container.offset << 16) | cramRecord.sliceIndex;
                final long chunkEnd = ((container.offset << 16) | cramRecord.sliceIndex) + 1;
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
