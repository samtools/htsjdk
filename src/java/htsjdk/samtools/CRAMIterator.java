/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
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
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.SequenceUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CRAMIterator implements SAMRecordIterator {
    private static final Log log = Log.getInstance(CRAMIterator.class);
    private final CountingInputStream is;
    private CramHeader cramHeader;
    private ArrayList<SAMRecord> records;
    private SAMRecord nextRecord = null;
    private final boolean restoreNMTag = true;
    private final boolean restoreMDTag = false;
    private CramNormalizer normalizer;
    private byte[] refs;
    private int prevSeqId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
    public Container container;
    private SamReader mReader;
    long firstContainerOffset = 0;
    private Iterator<Container> containerIterator;

    private ContainerParser parser;
    private final ReferenceSource referenceSource;

    private Iterator<SAMRecord> iterator = Collections.<SAMRecord>emptyList().iterator();

    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public void setValidationStringency(
            ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    private long samRecordIndex;
    private ArrayList<CramCompressionRecord> cramRecords;

    public CRAMIterator(InputStream is, ReferenceSource referenceSource)
            throws IOException {
        this.is = new CountingInputStream(is);
        this.referenceSource = referenceSource;
        CramContainerIterator containerIterator = new CramContainerIterator(this.is);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = this.is.getCount();
        records = new ArrayList<SAMRecord>(10000);
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(),
                referenceSource);
        parser = new ContainerParser(cramHeader.getSamFileHeader());
    }

    public CRAMIterator(SeekableStream ss, ReferenceSource referenceSource, long[] coordinates)
            throws IOException {
        this.is = new CountingInputStream(ss);
        this.referenceSource = referenceSource;
        CramSpanContainerIterator containerIterator = CramSpanContainerIterator.fromFileSpan(ss, coordinates);
        cramHeader = containerIterator.getCramHeader();
        this.containerIterator = containerIterator;

        firstContainerOffset = containerIterator.getFirstContainerOffset();
        records = new ArrayList<SAMRecord>(10000);
        normalizer = new CramNormalizer(cramHeader.getSamFileHeader(),
                referenceSource);
        parser = new ContainerParser(cramHeader.getSamFileHeader());
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    private void nextContainer() throws IOException, IllegalArgumentException,
            IllegalAccessException {

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
            container = ContainerIO.readContainer(cramHeader.getVersion(), is);
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

        parser.getRecords(container, cramRecords);

        if (container.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            refs = new byte[]{};
        } else if (container.sequenceId == -2) {
            refs = null;
            prevSeqId = -2;
        } else if (prevSeqId < 0 || prevSeqId != container.sequenceId) {
            SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                    .getSequence(container.sequenceId);
            refs = referenceSource.getReferenceBases(sequence, true);
            prevSeqId = container.sequenceId;
        }

        for (int i = 0; i < container.slices.length; i++) {
            Slice s = container.slices[i];
            if (s.sequenceId < 0)
                continue;
            if (validationStringency != ValidationStringency.SILENT && !s.validateRefMD5(refs)) {
                log.error(String
                        .format("Reference sequence MD5 mismatch for slice: seq id %d, start %d, span %d, expected MD5 %s", s.sequenceId,
                                s.alignmentStart, s.alignmentSpan, String.format("%032x", new BigInteger(1, s.refMD5))));
            }
        }

        normalizer.normalize(cramRecords, refs, 0,
                container.h.substitutionMatrix);

        Cram2SamRecordFactory c2sFactory = new Cram2SamRecordFactory(
                cramHeader.getSamFileHeader());

        for (CramCompressionRecord r : cramRecords) {
            SAMRecord s = c2sFactory.create(r);
            if (!r.isSegmentUnmapped()) {
                SAMSequenceRecord sequence = cramHeader.getSamFileHeader()
                        .getSequence(r.sequenceId);
                refs = referenceSource.getReferenceBases(sequence, true);
                SequenceUtil.calculateMdAndNmTags(s, refs, restoreMDTag, restoreNMTag);
            }

            s.setValidationStringency(validationStringency);

            if (validationStringency != ValidationStringency.SILENT) {
                final List<SAMValidationError> validationErrors = s.isValid();
                SAMUtils.processValidationErrors(validationErrors,
                        samRecordIndex, validationStringency);
            }

            if (mReader != null) {
                final long chunkStart = (container.offset << 16) | r.sliceIndex;
                final long chunkEnd = ((container.offset << 16) | r.sliceIndex) + 1;
                nextRecord.setFileSource(new SAMFileSource(mReader,
                        new BAMFileSpan(new Chunk(chunkStart, chunkEnd))));
            }

            records.add(s);
            samRecordIndex++;
        }
        cramRecords.clear();
        iterator = records.iterator();
    }

    /**
     * Skip cached records until given alignment start position.
     * @param refIndex reference sequence index
     * @param pos alignment start to skip to
     */
    public void jumpWithinContainerToPos(int refIndex, int pos) {
        if (!hasNext()) return;
        int i = 0;
        for (SAMRecord record : records) {
            if (refIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && record.getReferenceIndex() != refIndex) continue;

            if (pos <= 0) {
                if (record.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                    iterator = records.listIterator(i);
                    return;
                }
            } else {
                if (record.getAlignmentStart() >= pos) {
                    iterator = records.listIterator(i);
                    return;
                }
            }
            i++;
        }
        iterator = Collections.<SAMRecord>emptyList().iterator();
    }

    @Override
    public boolean hasNext() {
        if (container != null && container.isEOF()) return false;
        if (!iterator.hasNext()) {
            try {
                nextContainer();
            } catch (Exception e) {
                throw new RuntimeEOFException(e);
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
        try {
            if (is != null)
                is.close();
        } catch (IOException e) {
        }
    }

    public static class CramFileIterable implements Iterable<SAMRecord> {
        private final ReferenceSource referenceSource;
        private final File cramFile;
        private final ValidationStringency validationStringency;

        public CramFileIterable(File cramFile, ReferenceSource referenceSource,
                                ValidationStringency validationStringency) {
            this.referenceSource = referenceSource;
            this.cramFile = cramFile;
            this.validationStringency = validationStringency;

        }

        public CramFileIterable(File cramFile, ReferenceSource referenceSource) {
            this(cramFile, referenceSource,
                    ValidationStringency.DEFAULT_STRINGENCY);
        }

        @Override
        public Iterator<SAMRecord> iterator() {
            try {
                FileInputStream fis = new FileInputStream(cramFile);
                BufferedInputStream bis = new BufferedInputStream(fis);
                CRAMIterator iterator = new CRAMIterator(bis, referenceSource);
                iterator.setValidationStringency(validationStringency);
                return iterator;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public SAMRecordIterator assertSorted(SortOrder sortOrder) {
        throw new RuntimeException("Not implemented.");
    }

    public SamReader getFileSource() {
        return mReader;
    }

    public void setFileSource(SamReader mReader) {
        this.mReader = mReader;
    }

    public SAMFileHeader getSAMFileHeader() {
        return cramHeader.getSamFileHeader();
    }

}
