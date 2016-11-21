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
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.cram.CRAMException;

public class CRAMIterator implements CRAMFileReader.IsClosableSAMRecordIterator {
    private static final Log log = Log.getInstance(CRAMIterator.class);
    private final CountingInputStream countingInputStream;
    private CramHeader cramHeader;
    private ArrayList<SAMRecord> records;
    private SAMRecord nextRecord = null;
    private CramNormalizer normalizer;
    private byte[] refs;
    private int prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
    public Container container;
    private SamReader mReader;
    final long firstContainerOffset;
    private Iterator<Container> containerIterator;

    private ContainerParser parser;
    private final CRAMReferenceSource referenceSource;

    private Iterator<SAMRecord> iterator = Collections.<SAMRecord>emptyList().iterator();

    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;
    private boolean isClosed = false;

    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public void setValidationStringency(
            final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    private long samRecordIndex;
    private ArrayList<CramCompressionRecord> cramRecords;

    CRAMIterator(final InputStream inputStream,
                 final CRAMReferenceSource referenceSource,
                 final ValidationStringency validationStringency,
                 final CramHeader cramHeader) throws IOException {
        if (null == referenceSource) {
            throw new CRAMException("A reference source is required for CRAM files");
        }
        this.countingInputStream = new CountingInputStream(inputStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        CramContainerIterator containerIterator;
        if (cramHeader == null) {
            containerIterator = new CramContainerIterator(this.countingInputStream);
            this.cramHeader = containerIterator.getCramHeader();
        } else {
            containerIterator = new CramContainerIterator(this.countingInputStream, cramHeader);
            this.cramHeader = cramHeader;
        }
        this.containerIterator = containerIterator;

        firstContainerOffset = this.countingInputStream.getCount();
        records = new ArrayList<>(10000);
        normalizer = new CramNormalizer(this.cramHeader.getSamFileHeader(), referenceSource);
        parser = new ContainerParser(this.cramHeader.getSamFileHeader());
    }

    @Deprecated
    public CRAMIterator(final InputStream inputStream, final CRAMReferenceSource referenceSource, final ValidationStringency validationStringency)
            throws IOException {
        this(inputStream, referenceSource, validationStringency, null);
    }

    CRAMIterator(final SeekableStream seekableStream,
                 final CRAMReferenceSource referenceSource,
                 final long[] coordinates,
                 final ValidationStringency validationStringency,
                 final CramHeader cramHeader) throws IOException {
        if (null == referenceSource) {
            throw new CRAMException("A reference source is required for CRAM files");
        }
        this.countingInputStream = new CountingInputStream(seekableStream);
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;
        final CramSpanContainerIterator containerIterator = CramSpanContainerIterator.fromFileSpan(seekableStream, coordinates);
        this.cramHeader = (cramHeader == null) ? containerIterator.getCramHeader() : cramHeader;
        this.containerIterator = containerIterator;

        firstContainerOffset = containerIterator.getFirstContainerOffset();
        records = new ArrayList<>(10000);
        normalizer = new CramNormalizer(this.cramHeader.getSamFileHeader(), referenceSource);
        parser = new ContainerParser(this.cramHeader.getSamFileHeader());
    }

    @Deprecated
    public CRAMIterator(final SeekableStream seekableStream, final CRAMReferenceSource referenceSource, final long[] coordinates, final ValidationStringency validationStringency)
            throws IOException {
        this(seekableStream, referenceSource, coordinates, validationStringency, null);
    }

    @Deprecated
    public CRAMIterator(final SeekableStream seekableStream, final CRAMReferenceSource referenceSource,
                        final long[] coordinates)
            throws IOException {
        this(seekableStream, referenceSource, coordinates, ValidationStringency.DEFAULT_STRINGENCY);
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    void nextContainer() throws IOException, IllegalArgumentException,
            IllegalAccessException, CRAMException {

        if (containerIterator != null) {
            if (!containerIterator.hasNext()) {
                records.clear();
                nextRecord = null;
                return;
            }
            container = containerIterator.next();
            if (container.isEOF()) {
                records.clear();
                nextRecord = null;
                return;
            }
        } else {
            container = ContainerIO.readContainer(cramHeader.getVersion(), countingInputStream);
            if (container.isEOF()) {
                records.clear();
                nextRecord = null;
                return;
            }
        }

        if (records == null)
            records = new ArrayList<SAMRecord>(container.nofRecords);
        else
            records.clear();
        if (cramRecords == null)
            cramRecords = new ArrayList<CramCompressionRecord>(container.nofRecords);
        else
            cramRecords.clear();

        parser.getRecords(container, cramRecords, validationStringency);

        if (container.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            refs = new byte[]{};
            prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        } else if (container.sequenceId == Slice.MULTI_REFERENCE) {
            refs = null;
            prevSeqId = Slice.MULTI_REFERENCE;
        } else if (prevSeqId < 0 || prevSeqId != container.sequenceId) {
            final SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                    .getSequence(container.sequenceId);
            refs = referenceSource.getReferenceBases(sequence, true);
            if (refs == null) {
                throw new CRAMException(String.format("Contig %s not found in the reference file.", sequence.getSequenceName()));
            }
            prevSeqId = container.sequenceId;
        }

        for (int i = 0; i < container.slices.length; i++) {
            final Slice slice = container.slices[i];
            if (slice.sequenceId < 0)
                continue;
            if (!slice.validateRefMD5(refs)) {
                final String msg = String.format(
                        "Reference sequence MD5 mismatch for slice: sequence id %d, start %d, span %d, expected MD5 %s",
                            slice.sequenceId,
                            slice.alignmentStart,
                            slice.alignmentSpan,
                            String.format("%032x", new BigInteger(1, slice.refMD5)));
                throw new CRAMException(msg);
            }
        }

        normalizer.normalize(cramRecords, refs, 0,
                container.header.substitutionMatrix);

        final Cram2SamRecordFactory cramToSamRecordFactory = new Cram2SamRecordFactory(
                cramHeader.getSamFileHeader());

        for (final CramCompressionRecord cramRecord : cramRecords) {
            final SAMRecord samRecord = cramToSamRecordFactory.create(cramRecord);
            if (!cramRecord.isSegmentUnmapped()) {
                final SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                        .getSequence(cramRecord.sequenceId);
                refs = referenceSource.getReferenceBases(sequence, true);
            }

            samRecord.setValidationStringency(validationStringency);

            if (validationStringency != ValidationStringency.SILENT) {
                final List<SAMValidationError> validationErrors = samRecord.isValid();
                SAMUtils.processValidationErrors(validationErrors,
                        samRecordIndex, validationStringency);
            }

            if (mReader != null) {
                final long chunkStart = (container.offset << 16) | cramRecord.sliceIndex;
                final long chunkEnd = ((container.offset << 16) | cramRecord.sliceIndex) + 1;
                nextRecord.setFileSource(new SAMFileSource(mReader,
                        new BAMFileSpan(new Chunk(chunkStart, chunkEnd))));
            }

            records.add(samRecord);
            samRecordIndex++;
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
            try {
                nextContainer();
            } catch (IOException e) {
                throw new SAMException(e);
            } catch (IllegalAccessException e) {
                throw new SAMException(e);
            }
        }

        return !records.isEmpty();
    }

    @Override
    public SAMRecord next() {
        return iterator.next();
    }

    @Override
    public void remove() {
        throw new RuntimeException("Removal of records not implemented.");
    }

    @Override
    public void close() {
        records.clear();
        isClosed = true;
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

    @Override
    public boolean isClosed() {
        return isClosed;
    }
}
