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

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.SAMValidationError.Type;
import htsjdk.samtools.metrics.MetricBase;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileWalker;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FastqQualityFormat;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.QualityEncodingDetector;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates SAM files as follows:
 * <ul>
 * <li>checks sam file header for sequence dictionary</li>
 * <li>checks sam file header for read groups</li>
 * <li>for each sam record
 * <ul>
 * <li>reports error detected by SAMRecord.isValid()</li>
 * <li>validates NM (nucleotide differences) exists and matches reality</li>
 * <li>validates mate fields agree with data in the mate record</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @author Doug Voet
 * @see SAMRecord#isValid()
 */
public class SamFileValidator {
    private Histogram<Type> errorsByType = new Histogram<Type>();
    private final PrintWriter out;
    private PairEndInfoMap pairEndInfoByName;
    private ReferenceSequenceFileWalker refFileWalker = null;
    private boolean verbose = false;
    private int maxVerboseOutput = 100;
    private SAMSortOrderChecker orderChecker;
    private Set<Type> errorsToIgnore = EnumSet.noneOf(Type.class);
    private boolean ignoreWarnings = false;
    private boolean bisulfiteSequenced = false;
    private boolean validateIndex = false;
    private boolean sequenceDictionaryEmptyAndNoWarningEmitted = false;
    private final int maxTempFiles;

    private final static Log log = Log.getInstance(SamFileValidator.class);

    public SamFileValidator(final PrintWriter out, final int maxTempFiles) {
        this.out = out;
        this.maxTempFiles = maxTempFiles;
    }

    /**
     * Sets one or more error types that should not be reported on.
     */
    public void setErrorsToIgnore(final Collection<Type> types) {
        if (!types.isEmpty()) {
            this.errorsToIgnore = EnumSet.copyOf(types);
        }
    }

    public void setIgnoreWarnings(final boolean ignoreWarnings) {
        this.ignoreWarnings = ignoreWarnings;
    }

    /**
     * Outputs validation summary report to out.
     *
     * @param samReader records to validate
     * @param reference if null, NM tag validation is skipped
     * @return boolean  true if there are no validation errors, otherwise false
     */
    public boolean validateSamFileSummary(final SAMFileReader samReader, final ReferenceSequenceFile reference) {
        init(reference, samReader.getFileHeader());

        validateSamFile(samReader, out);

        boolean result = errorsByType.isEmpty();

        if (errorsByType.getCount() > 0) {
            // Convert to a histogram with String IDs so that WARNING: or ERROR: can be prepended to the error type.
            final Histogram<String> errorsAndWarningsByType = new Histogram<String>("Error Type", "Count");
            for (final Histogram<SAMValidationError.Type>.Bin bin : errorsByType.values()) {
                errorsAndWarningsByType.increment(bin.getId().getHistogramString(), bin.getValue());
            }
            final MetricsFile<ValidationMetrics, String> metricsFile = new MetricsFile<ValidationMetrics, String>();
            errorsByType.setBinLabel("Error Type");
            errorsByType.setValueLabel("Count");
            metricsFile.setHistogram(errorsAndWarningsByType);
            metricsFile.write(out);
        }
        cleanup();
        return result;
    }

    /**
     * Outputs validation error details to out.
     *
     * @param samReader records to validate
     * @param reference if null, NM tag validation is skipped
     *                  processing will stop after this threshold has been reached
     * @return boolean  true if there are no validation errors, otherwise false
     */
    public boolean validateSamFileVerbose(final SAMFileReader samReader, final ReferenceSequenceFile reference) {
        init(reference, samReader.getFileHeader());

        try {
            validateSamFile(samReader, out);
        } catch (MaxOutputExceededException e) {
            out.println("Maximum output of [" + maxVerboseOutput + "] errors reached.");
        }
        boolean result = errorsByType.isEmpty();
        cleanup();
        return result;
    }

    public void validateBamFileTermination(final File inputFile) {
        BufferedInputStream inputStream = null;
        try {
            inputStream = IOUtil.toBufferedStream(new FileInputStream(inputFile));
            if (!BlockCompressedInputStream.isValidFile(inputStream)) {
                return;
            }
            final BlockCompressedInputStream.FileTermination terminationState =
                    BlockCompressedInputStream.checkTermination(inputFile);
            if (terminationState.equals(BlockCompressedInputStream.FileTermination.DEFECTIVE)) {
                addError(new SAMValidationError(Type.TRUNCATED_FILE, "BAM file has defective last gzip block",
                        inputFile.getPath()));
            } else if (terminationState.equals(BlockCompressedInputStream.FileTermination.HAS_HEALTHY_LAST_BLOCK)) {
                addError(new SAMValidationError(Type.BAM_FILE_MISSING_TERMINATOR_BLOCK,
                        "Older BAM file -- does not have terminator block",
                        inputFile.getPath()));

            }
        } catch (IOException e) {
            throw new SAMException("IOException", e);
        } finally {
            if (inputStream != null) {
                CloserUtil.close(inputStream);
            }
        }
    }

    private void validateSamFile(final SAMFileReader samReader, final PrintWriter out) {
        try {
            samReader.setValidationStringency(ValidationStringency.SILENT);
            validateHeader(samReader.getFileHeader());
            orderChecker = new SAMSortOrderChecker(samReader.getFileHeader().getSortOrder());
            validateSamRecordsAndQualityFormat(samReader, samReader.getFileHeader());
            validateUnmatchedPairs();
            if (validateIndex) {
                try {
                    BamIndexValidator.exhaustivelyTestIndex(samReader);
                } catch (Exception e) {
                    addError(new SAMValidationError(Type.INVALID_INDEX_FILE_POINTER, e.getMessage(), null));
                }
            }

            if (errorsByType.isEmpty()) {
                out.println("No errors found");
            }
        } finally {
            out.flush();
        }
    }


    /**
     * Report on reads marked as paired, for which the mate was not found.
     */
    private void validateUnmatchedPairs() {
        final InMemoryPairEndInfoMap inMemoryPairMap;
        if (pairEndInfoByName instanceof CoordinateSortedPairEndInfoMap) {
            // For the coordinate-sorted map, need to detect mate pairs in which the mateReferenceIndex on one end
            // does not match the readReference index on the other end, so the pairs weren't united and validated.
            inMemoryPairMap = new InMemoryPairEndInfoMap();
            CloseableIterator<Map.Entry<String, PairEndInfo>> it = ((CoordinateSortedPairEndInfoMap) pairEndInfoByName).iterator();
            while (it.hasNext()) {
                Map.Entry<String, PairEndInfo> entry = it.next();
                PairEndInfo pei = inMemoryPairMap.remove(entry.getValue().readReferenceIndex, entry.getKey());
                if (pei != null) {
                    // Found a mismatch btw read.mateReferenceIndex and mate.readReferenceIndex
                    List<SAMValidationError> errors = pei.validateMates(entry.getValue(), entry.getKey());
                    for (final SAMValidationError error : errors) {
                        addError(error);
                    }
                } else {
                    // Mate not found.
                    inMemoryPairMap.put(entry.getValue().mateReferenceIndex, entry.getKey(), entry.getValue());
                }
            }
            it.close();
        } else {
            inMemoryPairMap = (InMemoryPairEndInfoMap) pairEndInfoByName;
        }
        // At this point, everything in InMemoryMap is a read marked as a pair, for which a mate was not found.
        for (final Map.Entry<String, PairEndInfo> entry : inMemoryPairMap) {
            addError(new SAMValidationError(Type.MATE_NOT_FOUND, "Mate not found for paired read", entry.getKey()));
        }
    }

    /**
     * SAM record and quality format validations are combined into a single method because validation must be completed
     * in only a single pass of the SamRecords (because a SamReader's iterator() method may not return the same
     * records on a subsequent call).
     */
    private void validateSamRecordsAndQualityFormat(final Iterable<SAMRecord> samRecords, final SAMFileHeader header) {
        final SAMRecordIterator iter = (SAMRecordIterator) samRecords.iterator();
        final ProgressLogger progress = new ProgressLogger(log, 10000000, "Validated Read");
        final QualityEncodingDetector qualityDetector = new QualityEncodingDetector();
        try {
            while (iter.hasNext()) {
                final SAMRecord record = iter.next();

                qualityDetector.add(record);

                final long recordNumber = progress.getCount() + 1;
                final Collection<SAMValidationError> errors = record.isValid();
                if (errors != null) {
                    for (final SAMValidationError error : errors) {
                        error.setRecordNumber(recordNumber);
                        addError(error);
                    }
                }

                validateMateFields(record, recordNumber);
                validateSortOrder(record, recordNumber);
                validateReadGroup(record, header);
                final boolean cigarIsValid = validateCigar(record, recordNumber);
                if (cigarIsValid) {
                    validateNmTag(record, recordNumber);
                }
                validateSecondaryBaseCalls(record, recordNumber);
                validateTags(record, recordNumber);
                if (sequenceDictionaryEmptyAndNoWarningEmitted && !record.getReadUnmappedFlag()) {
                    addError(new SAMValidationError(Type.MISSING_SEQUENCE_DICTIONARY, "Sequence dictionary is empty", null));
                    sequenceDictionaryEmptyAndNoWarningEmitted = false;

                }
                progress.record(record);
            }

            try {
                if (progress.getCount() > 0) { // Avoid exception being thrown as a result of no qualities being read
                    final FastqQualityFormat format = qualityDetector.generateBestGuess(QualityEncodingDetector.FileContext.SAM, FastqQualityFormat.Standard);
                    if (format != FastqQualityFormat.Standard) {
                        addError(new SAMValidationError(Type.INVALID_QUALITY_FORMAT, String.format("Detected %s quality score encoding, but expected %s.", format, FastqQualityFormat.Standard), null));
                    }
                }
            } catch (SAMException e) {
                addError(new SAMValidationError(Type.INVALID_QUALITY_FORMAT, e.getMessage(), null));
            }
        } catch (SAMFormatException e) {
            // increment record number because the iterator behind the SAMFileReader
            // reads one record ahead so we will get this failure one record ahead
            final String msg = "SAMFormatException on record " + progress.getCount() + 1;
            out.println(msg);
            throw new SAMException(msg, e);
        } catch (FileTruncatedException e) {
            addError(new SAMValidationError(Type.TRUNCATED_FILE, "File is truncated", null));
        } finally {
            iter.close();
        }
    }

    private void validateReadGroup(final SAMRecord record, final SAMFileHeader header) {
        final SAMReadGroupRecord rg = record.getReadGroup();
        if (rg == null) {
            addError(new SAMValidationError(Type.RECORD_MISSING_READ_GROUP,
                    "A record is missing a read group", record.getReadName()));
        } else if (header.getReadGroup(rg.getId()) == null) {
            addError(new SAMValidationError(Type.READ_GROUP_NOT_FOUND,
                    "A record has a read group not found in the header: ",
                    record.getReadName() + ", " + rg.getReadGroupId()));
        }
    }

    /**
     * Report error if a tag value is a Long.
     */
    private void validateTags(final SAMRecord record, final long recordNumber) {
        for (final SAMRecord.SAMTagAndValue tagAndValue : record.getAttributes()) {
            if (tagAndValue.value instanceof Long) {
                addError(new SAMValidationError(Type.TAG_VALUE_TOO_LARGE,
                        "Numeric value too large for tag " + tagAndValue.tag,
                        record.getReadName(), recordNumber));
            }
        }
    }

    private void validateSecondaryBaseCalls(final SAMRecord record, final long recordNumber) {
        final String e2 = (String) record.getAttribute(SAMTag.E2.name());
        if (e2 != null) {
            if (e2.length() != record.getReadLength()) {
                addError(new SAMValidationError(Type.MISMATCH_READ_LENGTH_AND_E2_LENGTH,
                        String.format("E2 tag length (%d) != read length (%d)", e2.length(), record.getReadLength()),
                        record.getReadName(), recordNumber));
            }
            final byte[] bases = record.getReadBases();
            final byte[] secondaryBases = StringUtil.stringToBytes(e2);
            for (int i = 0; i < Math.min(bases.length, secondaryBases.length); ++i) {
                if (SequenceUtil.isNoCall(bases[i]) || SequenceUtil.isNoCall(secondaryBases[i])) {
                    continue;
                }
                if (SequenceUtil.basesEqual(bases[i], secondaryBases[i])) {
                    addError(new SAMValidationError(Type.E2_BASE_EQUALS_PRIMARY_BASE,
                            String.format("Secondary base call  (%c) == primary base call (%c)",
                                    (char) secondaryBases[i], (char) bases[i]),
                            record.getReadName(), recordNumber));
                    break;
                }
            }
        }
        final String u2 = (String) record.getAttribute(SAMTag.U2.name());
        if (u2 != null && u2.length() != record.getReadLength()) {
            addError(new SAMValidationError(Type.MISMATCH_READ_LENGTH_AND_U2_LENGTH,
                    String.format("U2 tag length (%d) != read length (%d)", u2.length(), record.getReadLength()),
                    record.getReadName(), recordNumber));
        }
    }

    private boolean validateCigar(final SAMRecord record, final long recordNumber) {
        if (record.getReadUnmappedFlag()) {
            return true;
        }
        return validateCigar(record, recordNumber, true);
    }

    private boolean validateMateCigar(final SAMRecord record, final long recordNumber) {
        return validateCigar(record, recordNumber, false);
    }

    private boolean validateCigar(final SAMRecord record, final long recordNumber, final boolean isReadCigar) {
        final ValidationStringency savedStringency = record.getValidationStringency();
        record.setValidationStringency(ValidationStringency.LENIENT);
        final List<SAMValidationError> errors = isReadCigar ? record.validateCigar(recordNumber) : SAMUtils.validateMateCigar(record, recordNumber);
        record.setValidationStringency(savedStringency);
        if (errors == null) {
            return true;
        }
        boolean valid = true;
        for (final SAMValidationError error : errors) {
            addError(error);
            valid = false;
        }
        return valid;
    }


    private void validateSortOrder(final SAMRecord record, final long recordNumber) {
        final SAMRecord prev = orderChecker.getPreviousRecord();
        if (!orderChecker.isSorted(record)) {
            addError(new SAMValidationError(
                    Type.RECORD_OUT_OF_ORDER,
                    String.format(
                            "The record is out of [%s] order, prior read name [%s], prior coodinates [%d:%d]",
                            record.getHeader().getSortOrder().name(),
                            prev.getReadName(),
                            prev.getReferenceIndex(),
                            prev.getAlignmentStart()),
                    record.getReadName(),
                    recordNumber));
        }
    }

    private void init(final ReferenceSequenceFile reference, final SAMFileHeader header) {
        if (header.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
            this.pairEndInfoByName = new CoordinateSortedPairEndInfoMap();
        } else {
            this.pairEndInfoByName = new InMemoryPairEndInfoMap();
        }
        if (reference != null) {
            this.refFileWalker = new ReferenceSequenceFileWalker(reference);
        }
    }

    private void cleanup() {
        this.errorsByType = null;
        this.pairEndInfoByName = null;
        this.refFileWalker = null;
    }

    private void validateNmTag(final SAMRecord record, final long recordNumber) {
        if (!record.getReadUnmappedFlag()) {
            final Integer tagNucleotideDiffs = record.getIntegerAttribute(ReservedTagConstants.NM);
            if (tagNucleotideDiffs == null) {
                addError(new SAMValidationError(
                        Type.MISSING_TAG_NM,
                        "NM tag (nucleotide differences) is missing",
                        record.getReadName(),
                        recordNumber));
            } else if (refFileWalker != null) {
                final ReferenceSequence refSequence = refFileWalker.get(record.getReferenceIndex());
                final int actualNucleotideDiffs = SequenceUtil.calculateSamNmTag(record, refSequence.getBases(),
                        0, isBisulfiteSequenced());

                if (!tagNucleotideDiffs.equals(actualNucleotideDiffs)) {
                    addError(new SAMValidationError(
                            Type.INVALID_TAG_NM,
                            "NM tag (nucleotide differences) in file [" + tagNucleotideDiffs +
                                    "] does not match reality [" + actualNucleotideDiffs + "]",
                            record.getReadName(),
                            recordNumber));
                }
            }
        }
    }

    private void validateMateFields(final SAMRecord record, final long recordNumber) {
        if (!record.getReadPairedFlag() || record.isSecondaryOrSupplementary()) {
            return;
        }
        validateMateCigar(record, recordNumber);

        final PairEndInfo pairEndInfo = pairEndInfoByName.remove(record.getReferenceIndex(), record.getReadName());
        if (pairEndInfo == null) {
            pairEndInfoByName.put(record.getMateReferenceIndex(), record.getReadName(), new PairEndInfo(record, recordNumber));
        } else {
            final List<SAMValidationError> errors =
                    pairEndInfo.validateMates(new PairEndInfo(record, recordNumber), record.getReadName());
            for (final SAMValidationError error : errors) {
                addError(error);
            }
        }
    }

    private void validateHeader(final SAMFileHeader fileHeader) {
        for (final SAMValidationError error : fileHeader.getValidationErrors()) {
            addError(error);
        }
        if (fileHeader.getVersion() == null) {
            addError(new SAMValidationError(Type.MISSING_VERSION_NUMBER, "Header has no version number", null));
        } else if (!SAMFileHeader.ACCEPTABLE_VERSIONS.contains(fileHeader.getVersion())) {
            addError(new SAMValidationError(Type.INVALID_VERSION_NUMBER, "Header version: " +
                    fileHeader.getVersion() + " does not match any of the acceptable versions: " +
                    StringUtil.join(", ", SAMFileHeader.ACCEPTABLE_VERSIONS.toArray(new String[0])),
                    null));
        }
        if (fileHeader.getSequenceDictionary().isEmpty()) {
            sequenceDictionaryEmptyAndNoWarningEmitted = true;
        }
        if (fileHeader.getReadGroups().isEmpty()) {
            addError(new SAMValidationError(Type.MISSING_READ_GROUP, "Read groups is empty", null));
        }
        final List<SAMProgramRecord> pgs = fileHeader.getProgramRecords();
        for (int i = 0; i < pgs.size() - 1; i++) {
            for (int j = i + 1; j < pgs.size(); j++) {
                if (pgs.get(i).getProgramGroupId().equals(pgs.get(j).getProgramGroupId())) {
                    addError(new SAMValidationError(Type.DUPLICATE_PROGRAM_GROUP_ID, "Duplicate " +
                            "program group id: " + pgs.get(i).getProgramGroupId(), null));
                }
            }
        }

        final List<SAMReadGroupRecord> rgs = fileHeader.getReadGroups();
        final Set<String> readGroupIDs = new HashSet<String>();

        for (final SAMReadGroupRecord record : rgs) {
            final String readGroupID = record.getReadGroupId();
            if (readGroupIDs.contains(readGroupID)) {
                addError(new SAMValidationError(Type.DUPLICATE_READ_GROUP_ID, "Duplicate " +
                        "read group id: " + readGroupID, null));
            } else {
                readGroupIDs.add(readGroupID);
            }

            final String platformValue = record.getPlatform();
            if (platformValue == null || "".equals(platformValue)) {
                addError(new SAMValidationError(Type.MISSING_PLATFORM_VALUE,
                        "A platform (PL) attribute was not found for read group ",
                        readGroupID));
            }
        }

    }

    private void addError(final SAMValidationError error) {
        // Just ignore an error if it's of a type we're not interested in
        if (this.errorsToIgnore.contains(error.getType())) return;

        if (this.ignoreWarnings && error.getType().severity == SAMValidationError.Severity.WARNING) return;

        this.errorsByType.increment(error.getType());
        if (verbose) {
            out.println(error);
            out.flush();
            if (this.errorsByType.getCount() >= maxVerboseOutput) {
                throw new MaxOutputExceededException();
            }
        }
    }

    /**
     * Control verbosity
     *
     * @param verbose          True in order to emit a message per error or warning.
     * @param maxVerboseOutput If verbose, emit no more than this many messages.  Ignored if !verbose.
     */
    public void setVerbose(final boolean verbose, final int maxVerboseOutput) {
        this.verbose = verbose;
        this.maxVerboseOutput = maxVerboseOutput;
    }

    public boolean isBisulfiteSequenced() {
        return bisulfiteSequenced;
    }

    public void setBisulfiteSequenced(boolean bisulfiteSequenced) {
        this.bisulfiteSequenced = bisulfiteSequenced;
    }

    public SamFileValidator setValidateIndex(boolean validateIndex) {
        // The SAMFileReader must also have IndexCaching enabled to have the index validated,
        // samReader.enableIndexCaching(true);
        this.validateIndex = validateIndex;
        return this;
    }

    public static class ValidationMetrics extends MetricBase {
    }

    /**
     * This class is used so we don't have to store the entire SAMRecord in memory while we wait
     * to find a record's mate and also to store the record number.
     */
    private static class PairEndInfo {
        private final int readAlignmentStart;
        private final int readReferenceIndex;
        private final boolean readNegStrandFlag;
        private final boolean readUnmappedFlag;
        private final String readCigarString;

        private final int mateAlignmentStart;
        private final int mateReferenceIndex;
        private final boolean mateNegStrandFlag;
        private final boolean mateUnmappedFlag;
        private final String mateCigarString;

        private final boolean firstOfPairFlag;

        private final long recordNumber;

        public PairEndInfo(final SAMRecord record, final long recordNumber) {
            this.recordNumber = recordNumber;

            this.readAlignmentStart = record.getAlignmentStart();
            this.readNegStrandFlag = record.getReadNegativeStrandFlag();
            this.readReferenceIndex = record.getReferenceIndex();
            this.readUnmappedFlag = record.getReadUnmappedFlag();
            this.readCigarString = record.getCigarString();

            this.mateAlignmentStart = record.getMateAlignmentStart();
            this.mateNegStrandFlag = record.getMateNegativeStrandFlag();
            this.mateReferenceIndex = record.getMateReferenceIndex();
            this.mateUnmappedFlag = record.getMateUnmappedFlag();
            final Object mcs = record.getAttribute(SAMTag.MC.name());
            this.mateCigarString = (mcs != null) ? (String) mcs : null;

            this.firstOfPairFlag = record.getFirstOfPairFlag();
        }

        private PairEndInfo(int readAlignmentStart, int readReferenceIndex, boolean readNegStrandFlag, boolean readUnmappedFlag,
                            String readCigarString,
                            int mateAlignmentStart, int mateReferenceIndex, boolean mateNegStrandFlag, boolean mateUnmappedFlag,
                            String mateCigarString,
                            boolean firstOfPairFlag, long recordNumber) {
            this.readAlignmentStart = readAlignmentStart;
            this.readReferenceIndex = readReferenceIndex;
            this.readNegStrandFlag = readNegStrandFlag;
            this.readUnmappedFlag = readUnmappedFlag;
            this.readCigarString = readCigarString;
            this.mateAlignmentStart = mateAlignmentStart;
            this.mateReferenceIndex = mateReferenceIndex;
            this.mateNegStrandFlag = mateNegStrandFlag;
            this.mateUnmappedFlag = mateUnmappedFlag;
            this.mateCigarString = mateCigarString;
            this.firstOfPairFlag = firstOfPairFlag;
            this.recordNumber = recordNumber;
        }

        public List<SAMValidationError> validateMates(final PairEndInfo mate, final String readName) {
            final List<SAMValidationError> errors = new ArrayList<SAMValidationError>();
            validateMateFields(this, mate, readName, errors);
            validateMateFields(mate, this, readName, errors);
            // Validations that should not be repeated on both ends
            if (this.firstOfPairFlag == mate.firstOfPairFlag) {
                final String whichEnd = this.firstOfPairFlag ? "first" : "second";
                errors.add(new SAMValidationError(
                        Type.MATES_ARE_SAME_END,
                        "Both mates are marked as " + whichEnd + " of pair",
                        readName,
                        this.recordNumber
                ));
            }
            return errors;
        }

        private void validateMateFields(final PairEndInfo end1, final PairEndInfo end2, final String readName, final List<SAMValidationError> errors) {
            if (end1.mateAlignmentStart != end2.readAlignmentStart) {
                errors.add(new SAMValidationError(
                        Type.MISMATCH_MATE_ALIGNMENT_START,
                        "Mate alignment does not match alignment start of mate",
                        readName,
                        end1.recordNumber));
            }
            if (end1.mateNegStrandFlag != end2.readNegStrandFlag) {
                errors.add(new SAMValidationError(
                        Type.MISMATCH_FLAG_MATE_NEG_STRAND,
                        "Mate negative strand flag does not match read negative strand flag of mate",
                        readName,
                        end1.recordNumber));
            }
            if (end1.mateReferenceIndex != end2.readReferenceIndex) {
                errors.add(new SAMValidationError(
                        Type.MISMATCH_MATE_REF_INDEX,
                        "Mate reference index (MRNM) does not match reference index of mate",
                        readName,
                        end1.recordNumber));
            }
            if (end1.mateUnmappedFlag != end2.readUnmappedFlag) {
                errors.add(new SAMValidationError(
                        Type.MISMATCH_FLAG_MATE_UNMAPPED,
                        "Mate unmapped flag does not match read unmapped flag of mate",
                        readName,
                        end1.recordNumber));
            }
            if ((end1.mateCigarString != null) && (!end1.mateCigarString.equals(end2.readCigarString))) {
                errors.add(new SAMValidationError(
                        Type.MISMATCH_MATE_CIGAR_STRING,
                        "Mate CIGAR string does not match CIGAR string of mate",
                        readName,
                        end1.recordNumber));
            }
            // Note - don't need to validate that the mateCigarString is a valid cigar string, since this
            // will be validated by validateCigar on the mate's record itself.
        }
    }

    /**
     * Thrown in addError indicating that maxVerboseOutput has been exceeded and processing should stop
     */
    private static class MaxOutputExceededException extends SAMException {
        MaxOutputExceededException() {
            super("maxVerboseOutput exceeded.");
        }
    }

    interface PairEndInfoMap extends Iterable<Map.Entry<String, PairEndInfo>> {
        void put(int mateReferenceIndex, String key, PairEndInfo value);

        PairEndInfo remove(int mateReferenceIndex, String key);

        CloseableIterator<Map.Entry<String, PairEndInfo>> iterator();
    }

    private class CoordinateSortedPairEndInfoMap implements PairEndInfoMap {
        private final CoordinateSortedPairInfoMap<String, PairEndInfo> onDiskMap =
                new CoordinateSortedPairInfoMap<String, PairEndInfo>(maxTempFiles, new Codec());

        public void put(int mateReferenceIndex, String key, PairEndInfo value) {
            onDiskMap.put(mateReferenceIndex, key, value);
        }

        public PairEndInfo remove(int mateReferenceIndex, String key) {
            return onDiskMap.remove(mateReferenceIndex, key);
        }

        public CloseableIterator<Map.Entry<String, PairEndInfo>> iterator() {
            return onDiskMap.iterator();
        }

        private class Codec implements CoordinateSortedPairInfoMap.Codec<String, PairEndInfo> {
            private DataInputStream in;
            private DataOutputStream out;

            public void setOutputStream(final OutputStream os) {
                this.out = new DataOutputStream(os);
            }

            public void setInputStream(final InputStream is) {
                this.in = new DataInputStream(is);
            }

            public void encode(final String key, final PairEndInfo record) {
                try {
                    out.writeUTF(key);
                    out.writeInt(record.readAlignmentStart);
                    out.writeInt(record.readReferenceIndex);
                    out.writeBoolean(record.readNegStrandFlag);
                    out.writeBoolean(record.readUnmappedFlag);
                    out.writeUTF(record.readCigarString);
                    out.writeInt(record.mateAlignmentStart);
                    out.writeInt(record.mateReferenceIndex);
                    out.writeBoolean(record.mateNegStrandFlag);
                    out.writeBoolean(record.mateUnmappedFlag);
                    // writeUTF can't take null, so store a null mateCigarString as an empty string
                    out.writeUTF(record.mateCigarString != null ? record.mateCigarString : "");
                    out.writeBoolean(record.firstOfPairFlag);
                    out.writeLong(record.recordNumber);
                } catch (IOException e) {
                    throw new SAMException("Error spilling PairInfo to disk", e);
                }
            }

            public Map.Entry<String, PairEndInfo> decode() {
                try {
                    final String key = in.readUTF();
                    final int readAlignmentStart = in.readInt();
                    final int readReferenceIndex = in.readInt();
                    final boolean readNegStrandFlag = in.readBoolean();
                    final boolean readUnmappedFlag = in.readBoolean();
                    final String readCigarString = in.readUTF();

                    final int mateAlignmentStart = in.readInt();
                    final int mateReferenceIndex = in.readInt();
                    final boolean mateNegStrandFlag = in.readBoolean();
                    final boolean mateUnmappedFlag = in.readBoolean();

                    // read mateCigarString - note that null value is stored as an empty string
                    final String mcs = in.readUTF();
                    final String mateCigarString = !mcs.isEmpty() ? mcs : null;

                    final boolean firstOfPairFlag = in.readBoolean();

                    final long recordNumber = in.readLong();
                    final PairEndInfo rec = new PairEndInfo(readAlignmentStart, readReferenceIndex, readNegStrandFlag,
                            readUnmappedFlag, readCigarString, mateAlignmentStart, mateReferenceIndex, mateNegStrandFlag,
                            mateUnmappedFlag, mateCigarString,
                            firstOfPairFlag, recordNumber);
                    return new AbstractMap.SimpleEntry(key, rec);
                } catch (IOException e) {
                    throw new SAMException("Error reading PairInfo from disk", e);
                }
            }
        }
    }

    private static class InMemoryPairEndInfoMap implements PairEndInfoMap {
        private final Map<String, PairEndInfo> map = new HashMap<String, PairEndInfo>();

        public void put(int mateReferenceIndex, String key, PairEndInfo value) {
            if (mateReferenceIndex != value.mateReferenceIndex)
                throw new IllegalArgumentException("mateReferenceIndex does not agree with PairEndInfo");
            map.put(key, value);
        }

        public PairEndInfo remove(int mateReferenceIndex, String key) {
            return map.remove(key);
        }

        public CloseableIterator<Map.Entry<String, PairEndInfo>> iterator() {
            final Iterator<Map.Entry<String, PairEndInfo>> it = map.entrySet().iterator();
            return new CloseableIterator<Map.Entry<String, PairEndInfo>>() {
                public void close() {
                    // do nothing
                }

                public boolean hasNext() {
                    return it.hasNext();
                }

                public Map.Entry<String, PairEndInfo> next() {
                    return it.next();
                }

                public void remove() {
                    it.remove();
                }
            };
        }
    }
}
