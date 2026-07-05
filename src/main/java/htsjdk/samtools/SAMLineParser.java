/*
 * The MIT License
 *
 * Copyright (c) 2012 The Broad Institute
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

import htsjdk.samtools.util.StringUtil;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * This class enables creation of a SAMRecord object from a String in SAM text format.  The SAM flag field will be inferred
 * for each record separately, unless the expected format is set using `withSamFlagField`.
 *
 * <p>Instances hold reusable per-line scratch state (tab-position arrays, current-line buffers,
 * the embedded {@link TextTagCodec}). They are <b>not thread-safe</b>; a single parser must not
 * be used from multiple threads concurrently.</p>
 */
public class SAMLineParser {

    // From SAM specification
    private static final int QNAME_COL = 0;
    private static final int FLAG_COL = 1;
    private static final int RNAME_COL = 2;
    private static final int POS_COL = 3;
    private static final int MAPQ_COL = 4;
    private static final int CIGAR_COL = 5;
    private static final int MRNM_COL = 6;
    private static final int MPOS_COL = 7;
    private static final int ISIZE_COL = 8;
    private static final int SEQ_COL = 9;
    private static final int QUAL_COL = 10;

    private static final int NUM_REQUIRED_FIELDS = 11;

    /**
     * Maximum number of tab-separated fields a single line may contain. This is both the size of
     * the per-parser scratch arrays ({@link #mFieldOffsets}, {@link #mFieldLengths}) and the
     * hard cap that triggers a "Too many fields" error during parsing. The size is generously
     * above anything observed in practice (11 mandatory fields plus a few dozen optional tags
     * per record); the memory cost is two {@code int[]}s (~80KB) per parser instance.
     */
    private static final int MAX_FIELDS = 10000;

    /**
     * Scratch arrays for byte-based parsing. Each pair (mFieldOffsets[i], mFieldLengths[i]) gives
     * the position and length of field i within the current line's byte buffer. Allocated once
     * and reused across calls to {@link #parseLineFromBytes}.
     */
    private final int[] mFieldOffsets = new int[MAX_FIELDS];

    private final int[] mFieldLengths = new int[MAX_FIELDS];

    /**
     * Add information about the origin (reader and position) to SAM records.
     */
    private final SamReader mParentReader;

    private final SAMRecordFactory samRecordFactory;
    private final ValidationStringency validationStringency;
    private final SAMFileHeader mFileHeader;
    private final SAMSequenceDictionary mSequenceDictionary;
    private final Path mPath;
    /**
     * Optional user-provided format override for the FLAG field. {@code null} means
     * "auto-detect format per record" via {@link SamFlagField#parseDefault}.
     */
    private SamFlagField samFlagField;

    private final TextTagCodec tagCodec = new TextTagCodec();

    private int currentLineNumber;
    private byte[] currentLineBuf;
    private int currentLineOff;
    private int currentLineLen;

    //
    // Constructors
    //

    /**
     * Public constructor. Use the default SAMRecordFactory and stringency.
     *
     * @param samFileHeader SAM file header
     */
    public SAMLineParser(final SAMFileHeader samFileHeader) {

        this(new DefaultSAMRecordFactory(), ValidationStringency.DEFAULT_STRINGENCY, samFileHeader, null, (Path) null);
    }

    /**
     * Public constructor. Use the default SAMRecordFactory and stringency.
     *
     * @param samFileHeader SAM file header
     * @param samFileReader SAM file reader For passing to SAMRecord.setFileSource, may be null.
     * @param samPath       SAM file path being read (for error message only, may be null)
     */
    public SAMLineParser(final SAMFileHeader samFileHeader, final SamReader samFileReader, final Path samPath) {

        this(
                new DefaultSAMRecordFactory(),
                ValidationStringency.DEFAULT_STRINGENCY,
                samFileHeader,
                samFileReader,
                samPath);
    }

    /**
     * Public constructor.
     *
     * @param samRecordFactory     SamRecord Factory
     * @param validationStringency validation stringency
     * @param samFileHeader        SAM file header
     * @param samFileReader        SAM file reader For passing to SAMRecord.setFileSource, may be null.
     * @param samPath              SAM file path being read (for error message only, may be null)
     */
    public SAMLineParser(
            final SAMRecordFactory samRecordFactory,
            final ValidationStringency validationStringency,
            final SAMFileHeader samFileHeader,
            final SamReader samFileReader,
            final Path samPath) {

        if (samRecordFactory == null) throw new NullPointerException("The SamRecordFactory must be set");

        if (validationStringency == null) throw new NullPointerException("The validationStringency must be set");

        if (samFileHeader == null) throw new NullPointerException("The mFileHeader must be set");

        this.samRecordFactory = samRecordFactory;
        this.validationStringency = validationStringency;
        this.mFileHeader = samFileHeader;
        this.mSequenceDictionary = samFileHeader.getSequenceDictionary();

        // Can be null
        this.mParentReader = samFileReader;

        // Can be null
        this.mPath = samPath;
    }

    /**
     * Get the File header.
     *
     * @return the SAM file header
     */
    public SAMFileHeader getFileHeader() {

        return this.mFileHeader;
    }

    /**
     * Get validation stringency.
     *
     * @return validation stringency
     */
    public ValidationStringency getValidationStringency() {
        return this.validationStringency;
    }

    /**
     * Sets the expected SAM flag type expected for all records.
     */
    public SAMLineParser withSamFlagField(final SamFlagField samFlagField) {
        if (samFlagField == null) throw new IllegalArgumentException("Sam flag field was null");
        this.samFlagField = samFlagField;
        return this;
    }

    private int parseFlag(final String s, final String fieldName) {
        try {
            return samFlagField != null ? samFlagField.parse(s) : SamFlagField.parseDefault(s);
        } catch (NumberFormatException e) {
            throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
        } catch (SAMFormatException e) {
            throw reportFatalErrorParsingLine("Error in " + fieldName + " column: " + e.getMessage(), e);
        }
    }

    private void validateReferenceName(final String rname, final String fieldName) {
        if (rname.equals("=")) {
            if (fieldName.equals("MRNM")) {
                return;
            }
            reportErrorParsingLine("= is not a valid value for " + fieldName + " field.");
        }
        if (!this.mFileHeader.getSequenceDictionary().isEmpty()) {
            if (this.mFileHeader.getSequence(rname) == null) {
                reportErrorParsingLine(fieldName + " '" + rname + "' not found in any SQ record");
            }
        }
    }

    /**
     * Parse a SAM line.
     *
     * @param line line to parse
     * @return a new SAMRecord object
     */
    public SAMRecord parseLine(final String line) {

        return parseLine(line, -1);
    }

    /**
     * Parse a SAM line.
     *
     * @param line       line to parse
     * @param lineNumber line number in the file. If the line number is not known
     *                   can be {@code <=0}.
     * @return a new SAMRecord object
     */
    public SAMRecord parseLine(final String line, final int lineNumber) {
        // SAM alignment lines are restricted to printable ASCII by the spec, so ISO-8859-1 is
        // lossless and turns getBytes() into a bulk arraycopy on compact-string JVMs.
        final byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
        return parseLineFromBytes(bytes, 0, bytes.length, lineNumber);
    }

    /**
     * Parse a SAM alignment line from a byte range without first allocating a {@link String} for
     * the line. The 5 numeric fields (FLAG, POS, MAPQ, PNEXT, TLEN), the SEQ bases, the QUAL
     * scores, and the {@code KEY:T:VALUE} substrings of optional tags are all consumed directly
     * from the byte range; only fields that ultimately need to be stored as {@link String}s on
     * the resulting {@link SAMRecord} (QNAME, RNAME/RNEXT, CIGAR, Z-type tag values, ...) cause
     * a {@link String} allocation.
     *
     * @param buf        backing byte array
     * @param off        offset of the first byte of the line within {@code buf}
     * @param len        length of the line in bytes (excluding any line terminator)
     * @param lineNumber line number in the file, or {@code <= 0} if unknown
     * @return a new SAMRecord object
     */
    public SAMRecord parseLineFromBytes(final byte[] buf, final int off, final int len, final int lineNumber) {
        this.currentLineNumber = lineNumber;
        this.currentLineBuf = buf;
        this.currentLineOff = off;
        this.currentLineLen = len;

        final int numFields = splitFieldsByTab(buf, off, len, mFieldOffsets, mFieldLengths);
        if (numFields < NUM_REQUIRED_FIELDS) {
            throw reportFatalErrorParsingLine("Not enough fields");
        }
        if (numFields == mFieldOffsets.length) {
            reportErrorParsingLine("Too many fields in SAM text record.");
        }
        // In SILENT mode reportErrorParsingLine is a no-op so iterating every field just to find
        // empty ones is pure waste; preserves observable behavior since no error would be raised.
        if (this.validationStringency != ValidationStringency.SILENT) {
            for (int i = 0; i < numFields; ++i) {
                if (mFieldLengths[i] == 0) {
                    reportErrorParsingLine("Empty field at position " + i + " (zero-based)");
                }
            }
        }
        // createSAMRecord(header) already assigns mHeader in the SAMRecord constructor.
        // Calling setHeader() again would trigger setReferenceName("*") and
        // setMateReferenceName("*") which each do a dictionary HashMap lookup, only for
        // parseLine to immediately overwrite both names and indices below.
        final SAMRecord samRecord = samRecordFactory.createSAMRecord(this.mFileHeader);
        samRecord.setValidationStringency(this.validationStringency);
        if (mParentReader != null) samRecord.setFileSource(new SAMFileSource(mParentReader, null));
        samRecord.setReadName(decodeField(buf, mFieldOffsets[QNAME_COL], mFieldLengths[QNAME_COL]));

        final int flags = parseFlagFromBytes(buf, mFieldOffsets[FLAG_COL], mFieldLengths[FLAG_COL]);
        samRecord.setFlags(flags);

        final int rnameOff = mFieldOffsets[RNAME_COL];
        final int rnameLen = mFieldLengths[RNAME_COL];
        String resolvedRname = null;
        int resolvedRnameIndex = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        final boolean rnameSpecified = !isAsterisk(buf, rnameOff, rnameLen);
        if (rnameSpecified) {
            String rname = SAMSequenceRecord.truncateSequenceName(decodeField(buf, rnameOff, rnameLen));
            final SAMSequenceRecord rnameRecord = mSequenceDictionary.getSequence(rname);
            if (rnameRecord != null) {
                resolvedRname = rnameRecord.getSequenceName();
                resolvedRnameIndex = rnameRecord.getSequenceIndex();
                samRecord.setReferenceNameAndIndex(resolvedRname, resolvedRnameIndex);
            } else {
                if (this.validationStringency != ValidationStringency.SILENT) {
                    validateReferenceName(rname, "RNAME");
                }
                samRecord.setReferenceName(rname);
            }
        } else if (!samRecord.getReadUnmappedFlag()) {
            reportErrorParsingLine("RNAME is not specified but flags indicate mapped");
        }

        final int pos = parseIntFromBytes(buf, mFieldOffsets[POS_COL], mFieldLengths[POS_COL], "POS");
        final int mapq = parseIntFromBytes(buf, mFieldOffsets[MAPQ_COL], mFieldLengths[MAPQ_COL], "MAPQ");
        final int cigarOff = mFieldOffsets[CIGAR_COL];
        final int cigarLen = mFieldLengths[CIGAR_COL];
        final boolean cigarIsAsterisk = isAsterisk(buf, cigarOff, cigarLen);
        if (!SAMRecord.NO_ALIGNMENT_REFERENCE_NAME.equals(samRecord.getReferenceName())) {
            if (pos == 0) {
                reportErrorParsingLine("POS must be non-zero if RNAME is specified");
            }
            if (!samRecord.getReadUnmappedFlag() && cigarIsAsterisk) {
                reportErrorParsingLine("CIGAR must not be '*' if RNAME is specified");
            }
        } else {
            if (pos != 0) {
                reportErrorParsingLine("POS must be zero if RNAME is not specified");
            }
            if (mapq != 0) {
                reportErrorParsingLine("MAPQ must be zero if RNAME is not specified");
            }
            if (!cigarIsAsterisk) {
                reportErrorParsingLine("CIGAR must be '*' if RNAME is not specified");
            }
        }
        samRecord.setAlignmentStart(pos);
        samRecord.setMappingQuality(mapq);
        samRecord.setCigarString(decodeField(buf, cigarOff, cigarLen));

        final int mrnmOff = mFieldOffsets[MRNM_COL];
        final int mrnmLen = mFieldLengths[MRNM_COL];
        if (isAsterisk(buf, mrnmOff, mrnmLen)) {
            if (samRecord.getReadPairedFlag() && !samRecord.getMateUnmappedFlag()) {
                reportErrorParsingLine("MRNM not specified but flags indicate mate mapped");
            }
        } else {
            if (!samRecord.getReadPairedFlag()) {
                reportErrorParsingLine("MRNM specified but flags indicate unpaired");
            }
            if (mrnmLen == 1 && buf[mrnmOff] == '=') {
                if (!rnameSpecified) {
                    reportErrorParsingLine("MRNM is '=', but RNAME is not set");
                }
                if (resolvedRname != null) {
                    // Dictionary-hit fast path: reuse the canonical name and index we already
                    // resolved when parsing RNAME.
                    samRecord.setMateReferenceNameAndIndex(resolvedRname, resolvedRnameIndex);
                } else {
                    // RNAME was specified but not in the dictionary (or was '*'); mirror the
                    // record's current reference name via setMateReferenceName, which will
                    // intern it consistent with how setReferenceName handled the miss.
                    samRecord.setMateReferenceName(samRecord.getReferenceName());
                }
            } else {
                final String mateRName = SAMSequenceRecord.truncateSequenceName(decodeField(buf, mrnmOff, mrnmLen));
                final SAMSequenceRecord mateRecord = mSequenceDictionary.getSequence(mateRName);
                if (mateRecord != null) {
                    samRecord.setMateReferenceNameAndIndex(mateRecord.getSequenceName(), mateRecord.getSequenceIndex());
                } else {
                    if (this.validationStringency != ValidationStringency.SILENT) {
                        validateReferenceName(mateRName, "MRNM");
                    }
                    samRecord.setMateReferenceName(mateRName);
                }
            }
        }

        final int matePos = parseIntFromBytes(buf, mFieldOffsets[MPOS_COL], mFieldLengths[MPOS_COL], "MPOS");
        final int isize = parseIntFromBytes(buf, mFieldOffsets[ISIZE_COL], mFieldLengths[ISIZE_COL], "ISIZE");
        if (!samRecord.getMateReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME)) {
            if (matePos == 0) {
                reportErrorParsingLine("MPOS must be non-zero if MRNM is specified");
            }
        } else {
            if (matePos != 0) {
                reportErrorParsingLine("MPOS must be zero if MRNM is not specified");
            }
            if (isize != 0) {
                reportErrorParsingLine("ISIZE must be zero if MRNM is not specified");
            }
        }
        samRecord.setMateAlignmentStart(matePos);
        samRecord.setInferredInsertSize(isize);

        final int seqOff = mFieldOffsets[SEQ_COL];
        final int seqLen = mFieldLengths[SEQ_COL];
        if (!isAsterisk(buf, seqOff, seqLen)) {
            // In SILENT mode reportErrorParsingLine is a no-op so the per-base scan is pure waste;
            // skipping it preserves observable behavior.
            if (this.validationStringency != ValidationStringency.SILENT) {
                validateReadBases(buf, seqOff, seqLen);
            }
            samRecord.setReadBases(SAMUtils.readStringToNormalizedBases(buf, seqOff, seqLen));
        } else {
            samRecord.setReadBases(SAMRecord.NULL_SEQUENCE);
        }
        final int qualOff = mFieldOffsets[QUAL_COL];
        final int qualLen = mFieldLengths[QUAL_COL];
        if (!isAsterisk(buf, qualOff, qualLen)) {
            if (samRecord.getReadBases() == SAMRecord.NULL_SEQUENCE) {
                reportErrorParsingLine("QUAL should not be specified if SEQ is not specified");
            }
            if (samRecord.getReadLength() != qualLen) {
                reportErrorParsingLine("length(QUAL) != length(SEQ)");
            }
            samRecord.setBaseQualities(SAMUtils.fastqToPhred(buf, qualOff, qualLen));
        } else {
            samRecord.setBaseQualities(SAMRecord.NULL_QUALS);
        }

        for (int i = NUM_REQUIRED_FIELDS; i < numFields; ++i) {
            parseTag(samRecord, buf, mFieldOffsets[i], mFieldLengths[i]);
        }

        // Only call samRecord.isValid() if errors would be reported since the validation
        // is quite expensive in and of itself.
        if (this.validationStringency != ValidationStringency.SILENT) {
            final List<SAMValidationError> validationErrors = samRecord.isValid();
            if (validationErrors != null) {
                for (final SAMValidationError errorMessage : validationErrors) {
                    reportErrorParsingLine(errorMessage.getMessage());
                }
            }
        }

        return samRecord;
    }

    /**
     * Reads 8 bytes from {@code buf[off..off+8)} as a little-endian {@code long}. The SWAR
     * scanner in {@link #splitFieldsByTab} processes 8 bytes per iteration this way, which is
     * much faster than the equivalent byte-by-byte loop because Java byte loops don't get
     * auto-vectorized while VarHandle long reads do.
     */
    private static final VarHandle BYTE_ARRAY_AS_LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    // SWAR constants for detecting a target byte within an 8-byte word.
    // See e.g. https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
    private static final long TAB_PATTERN = 0x0909090909090909L; // '\t' repeated 8x
    private static final long SWAR_ONES = 0x0101010101010101L;
    private static final long SWAR_HIGH = 0x8080808080808080L;

    /**
     * Split a byte range by tab characters into a parallel pair of offset/length arrays. The
     * last field is always recorded (mirroring {@link StringUtil#split}'s contract that the
     * final segment is included even with no trailing delimiter), and parsing stops once the
     * scratch arrays are full so the caller can detect "too many fields".
     *
     * <p>Scans 8 bytes at a time using a SWAR ("SIMD within a register") trick: XOR with a
     * word of repeated tab bytes makes matching bytes zero, then {@code (x - 0x01..01) & ~x &
     * 0x80..80} sets the high bit of any zero byte. This avoids the scalar byte loop that the
     * JIT does not auto-vectorize and roughly matches the throughput of HotSpot's intrinsified
     * {@code String.indexOf} without requiring us to materialize a {@link String}.</p>
     *
     * @return the number of fields recorded
     */
    private static int splitFieldsByTab(
            final byte[] buf, final int off, final int len, final int[] offsets, final int[] lengths) {
        final int end = off + len;
        final int maxFields = offsets.length;
        int fieldStart = off;
        int count = 0;

        int i = off;
        final int simdEnd = end - 7;
        while (i < simdEnd) {
            final long word = (long) BYTE_ARRAY_AS_LONG_LE.get(buf, i);
            final long xored = word ^ TAB_PATTERN;
            long detected = (xored - SWAR_ONES) & ~xored & SWAR_HIGH;
            if (detected == 0) {
                i += 8;
                continue;
            }
            // One or more tab bytes in this chunk. Each match contributes a single set bit
            // (the high bit of that byte), so we can iterate matches with the standard
            // "find lowest set bit, clear it" pattern.
            do {
                final int byteOff = Long.numberOfTrailingZeros(detected) >>> 3;
                final int tabPos = i + byteOff;
                if (count >= maxFields) {
                    return count;
                }
                offsets[count] = fieldStart;
                lengths[count] = tabPos - fieldStart;
                count++;
                fieldStart = tabPos + 1;
                detected &= detected - 1;
            } while (detected != 0);
            i += 8;
        }

        // Scalar tail for the final 0-7 bytes.
        for (; i < end; i++) {
            if (buf[i] == '\t') {
                if (count >= maxFields) {
                    return count;
                }
                offsets[count] = fieldStart;
                lengths[count] = i - fieldStart;
                count++;
                fieldStart = i + 1;
            }
        }
        if (count < maxFields) {
            offsets[count] = fieldStart;
            lengths[count] = end - fieldStart;
            count++;
        }
        return count;
    }

    /** True iff the byte range is exactly the single ASCII character {@code *}. */
    private static boolean isAsterisk(final byte[] buf, final int off, final int len) {
        return len == 1 && buf[off] == '*';
    }

    /** Decode a SAM field byte range as a {@link String} using ISO-8859-1 (lossless for ASCII). */
    private static String decodeField(final byte[] buf, final int off, final int len) {
        return new String(buf, off, len, StandardCharsets.ISO_8859_1);
    }

    /**
     * Parse a signed decimal integer directly from {@code buf[off..off+len)} without allocating a
     * {@link String}. Accepts an optional leading {@code +} or {@code -}; the value must fit in a
     * signed 32-bit integer. Reports a fatal error if any non-digit appears or the value
     * over/underflows. {@code fieldName} is used to construct the error message.
     */
    private int parseIntFromBytes(final byte[] buf, final int off, final int len, final String fieldName) {
        if (len == 0) {
            throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
        }
        final int end = off + len;
        int i = off;
        final boolean negative;
        final byte first = buf[i];
        if (first == '-') {
            negative = true;
            i++;
        } else if (first == '+') {
            negative = false;
            i++;
        } else {
            negative = false;
        }
        if (i == end) {
            throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
        }
        long acc = 0;
        for (; i < end; i++) {
            final byte b = buf[i];
            if (b < '0' || b > '9') {
                throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
            }
            acc = acc * 10 + (b - '0');
            if (acc > Integer.MAX_VALUE + 1L) {
                throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
            }
        }
        final long signed = negative ? -acc : acc;
        if (signed < Integer.MIN_VALUE || signed > Integer.MAX_VALUE) {
            throw reportFatalErrorParsingLine("Non-numeric value in " + fieldName + " column");
        }
        return (int) signed;
    }

    /**
     * Parse the FLAG field. Spec says FLAG is an unsigned 16-bit integer in decimal, so the
     * vast majority of inputs are short decimal strings. Fast-path that case directly off the
     * byte range to skip the String allocation and {@link SamFlagField} format-detection work.
     * Non-decimal formats (hex prefix, octal prefix, named flags, custom format set via
     * {@link #withSamFlagField}) fall back to the existing String-based path.
     *
     * <p>The fast path only fires when the first byte is a non-zero digit; this matches
     * {@link SamFlagField#of}'s format detection (a leading '0' followed by more digits is
     * interpreted as octal, "0x..." as hex, anything non-digit as named-flag string format).</p>
     */
    private int parseFlagFromBytes(final byte[] buf, final int off, final int len) {
        if (samFlagField == null && len > 0) {
            final byte first = buf[off];
            // Single '0' is fine (decimal zero). Leading '0' followed by more characters is octal
            // per SamFlagField.of, so defer to the String path.
            if (first >= '1' && first <= '9' || (first == '0' && len == 1)) {
                final int end = off + len;
                long acc = 0;
                boolean fast = true;
                for (int i = off; i < end; i++) {
                    final byte b = buf[i];
                    if (b < '0' || b > '9') {
                        fast = false;
                        break;
                    }
                    acc = acc * 10 + (b - '0');
                    // Reject values outside the signed-int range: SamFlagField.parseDefault is
                    // backed by Integer.parseInt and would throw NumberFormatException for these.
                    if (acc > Integer.MAX_VALUE) {
                        fast = false;
                        break;
                    }
                }
                if (fast) {
                    return (int) acc;
                }
            }
        }
        return parseFlag(decodeField(buf, off, len), "FLAG");
    }

    /**
     * Scan the SEQ field for any byte that isn't a valid IUPAC base character. Reports (or
     * throws) on the first invalid base, depending on validation stringency; using a switch is
     * substantially faster than a regex match.
     */
    private void validateReadBases(final byte[] buf, final int off, final int len) {
        for (int i = 0; i < len; ++i) {
            if (!isValidReadBase((char) (buf[off + i] & 0xff))) {
                reportErrorParsingLine("Invalid character in read bases");
                return;
            }
        }
    }

    /**
     * Lookup table for valid read-base characters: indexed by ASCII codepoint (0-127), value
     * is {@code true} iff that character is a legal IUPAC base, ambiguity code, '.', or '='.
     * Branch-free check replaces the ~30-case switch used previously.
     */
    private static final boolean[] VALID_READ_BASE = new boolean[128];

    static {
        for (final char c :
                new char[] {'A', 'C', 'M', 'G', 'R', 'S', 'V', 'T', 'W', 'Y', 'H', 'K', 'D', 'B', 'N', '.', '='}) {
            VALID_READ_BASE[c] = true;
            // Lowercase variants (except '.' and '=' which have no lowercase form)
            if (c >= 'A' && c <= 'Z') {
                VALID_READ_BASE[c + ('a' - 'A')] = true;
            }
        }
    }

    private static boolean isValidReadBase(final char base) {
        return base < VALID_READ_BASE.length && VALID_READ_BASE[base];
    }

    /**
     * Parse one {@code KEY:T:VALUE} optional tag from a byte range and attach the result to
     * {@code samRecord}. Decodes the value via {@link TextTagCodec#decodeValue(byte[], int, int)}
     * and computes the binary tag short directly from the two key bytes (avoiding a 2-character
     * String allocation that {@code SAMTag.makeBinaryTag} would otherwise force).
     */
    private void parseTag(final SAMRecord samRecord, final byte[] buf, final int off, final int len) {
        if (len < 2) {
            reportErrorParsingLine("Malformed tag");
            return;
        }
        final Object value;
        try {
            tagCodec.decodeValue(buf, off, len);
            value = tagCodec.getLastValue();
        } catch (SAMFormatException e) {
            reportErrorParsingLine(e);
            return;
        }
        final short binaryTag = (short) ((buf[off + 1] & 0xff) << 8 | (buf[off] & 0xff));
        if (value instanceof TagValueAndUnsignedArrayFlag) {
            final TagValueAndUnsignedArrayFlag valueAndFlag = (TagValueAndUnsignedArrayFlag) value;
            samRecord.setAttribute(binaryTag, valueAndFlag.value, valueAndFlag.isUnsignedArray);
        } else {
            samRecord.setAttribute(binaryTag, value);
        }
    }

    //
    // Error methods
    //

    private RuntimeException reportFatalErrorParsingLine(final String reason) {
        return new SAMFormatException(makeErrorString(reason));
    }

    private RuntimeException reportFatalErrorParsingLine(final String reason, final Throwable throwable) {
        return new SAMFormatException(makeErrorString(reason), throwable);
    }

    private void reportErrorParsingLine(final String reason) {
        final String errorMessage = makeErrorString(reason);

        if (validationStringency == ValidationStringency.STRICT) {
            throw new SAMFormatException(errorMessage);
        } else if (validationStringency == ValidationStringency.LENIENT) {
            System.err.println("Ignoring SAM validation error due to lenient parsing:");
            System.err.println(errorMessage);
        }
    }

    private void reportErrorParsingLine(final Exception e) {
        final String errorMessage = makeErrorString(e.getMessage());
        if (validationStringency == ValidationStringency.STRICT) {
            throw new SAMFormatException(errorMessage, e);
        } else if (validationStringency == ValidationStringency.LENIENT) {
            System.err.println("Ignoring SAM validation error due to lenient parsing:");
            System.err.println(errorMessage);
        }
    }

    private String makeErrorString(final String reason) {
        String fileMessage = "";
        if (mPath != null) {
            fileMessage = "File " + mPath + "; ";
        }
        // Materialize the offending line lazily from the byte buffer so we don't pay the String
        // allocation on the (much more common) success path.
        final String lineText = this.currentLineBuf != null
                ? new String(currentLineBuf, currentLineOff, currentLineLen, StandardCharsets.ISO_8859_1)
                : "";
        return "Error parsing text SAM file. "
                + reason + "; " + fileMessage + "Line "
                + (this.currentLineNumber <= 0 ? "unknown" : this.currentLineNumber)
                + "\nLine: " + lineText;
    }
}
