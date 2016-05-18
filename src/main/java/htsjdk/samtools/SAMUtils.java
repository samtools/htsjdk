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

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.CigarUtil;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.CoordMath;
import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Utilty methods.
 */
public final class SAMUtils {
    // Representation of bases, one for when in low-order nybble, one for when in high-order nybble.
    private static final byte COMPRESSED_EQUAL_LOW = 0;
    private static final byte COMPRESSED_A_LOW = 1;
    private static final byte COMPRESSED_C_LOW = 2;
    private static final byte COMPRESSED_M_LOW = 3;
    private static final byte COMPRESSED_G_LOW = 4;
    private static final byte COMPRESSED_R_LOW = 5;
    private static final byte COMPRESSED_S_LOW = 6;
    private static final byte COMPRESSED_V_LOW = 7;
    private static final byte COMPRESSED_T_LOW = 8;
    private static final byte COMPRESSED_W_LOW = 9;
    private static final byte COMPRESSED_Y_LOW = 10;
    private static final byte COMPRESSED_H_LOW = 11;
    private static final byte COMPRESSED_K_LOW = 12;
    private static final byte COMPRESSED_D_LOW = 13;
    private static final byte COMPRESSED_B_LOW = 14;
    private static final byte COMPRESSED_N_LOW = 15;
    private static final byte COMPRESSED_EQUAL_HIGH = COMPRESSED_EQUAL_LOW << 4;
    private static final byte COMPRESSED_A_HIGH = COMPRESSED_A_LOW << 4;
    private static final byte COMPRESSED_C_HIGH = COMPRESSED_C_LOW << 4;
    private static final byte COMPRESSED_G_HIGH = COMPRESSED_G_LOW << 4;
    private static final byte COMPRESSED_T_HIGH = (byte) (COMPRESSED_T_LOW << 4);
    private static final byte COMPRESSED_N_HIGH = (byte) (COMPRESSED_N_LOW << 4);

    private static final byte COMPRESSED_M_HIGH = (byte) (COMPRESSED_M_LOW << 4);
    private static final byte COMPRESSED_R_HIGH = (byte) (COMPRESSED_R_LOW << 4);
    private static final byte COMPRESSED_S_HIGH = (byte) (COMPRESSED_S_LOW << 4);
    private static final byte COMPRESSED_V_HIGH = (byte) (COMPRESSED_V_LOW << 4);
    private static final byte COMPRESSED_W_HIGH = (byte) (COMPRESSED_W_LOW << 4);
    private static final byte COMPRESSED_Y_HIGH = (byte) (COMPRESSED_Y_LOW << 4);
    private static final byte COMPRESSED_H_HIGH = (byte) (COMPRESSED_H_LOW << 4);
    private static final byte COMPRESSED_K_HIGH = (byte) (COMPRESSED_K_LOW << 4);
    private static final byte COMPRESSED_D_HIGH = (byte) (COMPRESSED_D_LOW << 4);
    private static final byte COMPRESSED_B_HIGH = (byte) (COMPRESSED_B_LOW << 4);
    
    private static final byte [] COMPRESSED_LOOKUP_TABLE = 
            new byte[]{
                '=',
                'A',
                'C',
                'M',
                'G',
                'R',
                'S',
                'V',
                'T',
                'W',
                'Y',
                'H',
                'K',
                'D',
                'B',
                'N'
            };
    
    public static final int MAX_PHRED_SCORE = 93;

    /**
     * Convert from a byte array containing =AaCcGgTtNn represented as ASCII, to a byte array half as long,
     * with =, A, C, G, T converted to 0, 1, 2, 4, 8, 15.
     *
     * @param readBases Bases as ASCII bytes.
     * @return New byte array with bases represented as nybbles, in BAM binary format.
     */
    static byte[] bytesToCompressedBases(final byte[] readBases) {
        final byte[] compressedBases = new byte[(readBases.length + 1) / 2];
        int i;
        for (i = 1; i < readBases.length; i += 2) {
            compressedBases[i / 2] = (byte) (charToCompressedBaseHigh(readBases[i - 1]) |
                    charToCompressedBaseLow(readBases[i]));
        }
        // Last nybble
        if (i == readBases.length) {
            compressedBases[i / 2] = charToCompressedBaseHigh((char) readBases[i - 1]);
        }
        return compressedBases;
    }

    /**
     * Convert from a byte array with basese stored in nybbles, with =, A, C, G, T represented as 0, 1, 2, 4, 8, 15,
     * to a a byte array containing =AaCcGgTtNn represented as ASCII.
     *
     * @param length Number of bases (not bytes) to convert.
     * @param compressedBases Bases represented as nybbles, in BAM binary format.
     * @param compressedOffset Byte offset in compressedBases to start.
     * @return New byte array with bases as ASCII bytes.
     */
    public static byte[] compressedBasesToBytes(final int length, final byte[] compressedBases, final int compressedOffset) {
        final byte[] ret = new byte[length];
        int i;
        for (i = 1; i < length; i += 2) {
            final int compressedIndex = i / 2 + compressedOffset;
            ret[i - 1] = compressedBaseToByteHigh(compressedBases[compressedIndex]);
            ret[i] = compressedBaseToByteLow(compressedBases[compressedIndex]);
        }
        // Last nybble
        if (i == length) {
            ret[i - 1] = compressedBaseToByteHigh(compressedBases[i / 2 + compressedOffset]);
        }
        return ret;
    }

    /**
     * Convert from ASCII byte to BAM nybble representation of a base in low-order nybble.
     *
     * @param base One of =AaCcGgTtNn.
     * @return Low-order nybble-encoded equivalent.
     */
    private static byte charToCompressedBaseLow(final int base) {
        switch (base) {
            case '=':
                return COMPRESSED_EQUAL_LOW;
            case 'a':
            case 'A':
                return COMPRESSED_A_LOW;
            case 'c':
            case 'C':
                return COMPRESSED_C_LOW;
            case 'g':
            case 'G':
                return COMPRESSED_G_LOW;
            case 't':
            case 'T':
                return COMPRESSED_T_LOW;
            case 'n':
            case 'N':
            case '.':
                return COMPRESSED_N_LOW;

            // IUPAC ambiguity codes
            case 'M':
            case 'm':
                return COMPRESSED_M_LOW;
            case 'R':
            case 'r':
                return COMPRESSED_R_LOW;
            case 'S':
            case 's':
                return COMPRESSED_S_LOW;
            case 'V':
            case 'v':
                return COMPRESSED_V_LOW;
            case 'W':
            case 'w':
                return COMPRESSED_W_LOW;
            case 'Y':
            case 'y':
                return COMPRESSED_Y_LOW;
            case 'H':
            case 'h':
                return COMPRESSED_H_LOW;
            case 'K':
            case 'k':
                return COMPRESSED_K_LOW;
            case 'D':
            case 'd':
                return COMPRESSED_D_LOW;
            case 'B':
            case 'b':
                return COMPRESSED_B_LOW;
            default:
                throw new IllegalArgumentException("Bad  byte passed to charToCompressedBase: " + base);
        }
    }

    /**
     * Convert from ASCII byte to BAM nybble representation of a base in high-order nybble.
     *
     * @param base One of =AaCcGgTtNn.
     * @return High-order nybble-encoded equivalent.
     */
    private static byte charToCompressedBaseHigh(final int base) {
        switch (base) {
            case '=':
                return COMPRESSED_EQUAL_HIGH;
            case 'a':
            case 'A':
                return COMPRESSED_A_HIGH;
            case 'c':
            case 'C':
                return COMPRESSED_C_HIGH;
            case 'g':
            case 'G':
                return COMPRESSED_G_HIGH;
            case 't':
            case 'T':
                return COMPRESSED_T_HIGH;
            case 'n':
            case 'N':
            case '.':
                return COMPRESSED_N_HIGH;

            // IUPAC ambiguity codes
            case 'M':
            case 'm':
                return COMPRESSED_M_HIGH;
            case 'R':
            case 'r':
                return COMPRESSED_R_HIGH;
            case 'S':
            case 's':
                return COMPRESSED_S_HIGH;
            case 'V':
            case 'v':
                return COMPRESSED_V_HIGH;
            case 'W':
            case 'w':
                return COMPRESSED_W_HIGH;
            case 'Y':
            case 'y':
                return COMPRESSED_Y_HIGH;
            case 'H':
            case 'h':
                return COMPRESSED_H_HIGH;
            case 'K':
            case 'k':
                return COMPRESSED_K_HIGH;
            case 'D':
            case 'd':
                return COMPRESSED_D_HIGH;
            case 'B':
            case 'b':
                return COMPRESSED_B_HIGH;
            default:
                throw new IllegalArgumentException("Bad  byte passed to charToCompressedBase: " + base);
        }
    }
    
    /**
     * Returns the byte corresponding to a certain nybble
     * @param base One of COMPRESSED_*_LOW, a low-order nybble encoded base.
     * @return ASCII base, one of ACGTN=.
     */
    private static byte compressedBaseToByte(byte base){
        try{
            return COMPRESSED_LOOKUP_TABLE[base];
        }catch(IndexOutOfBoundsException e){
            throw new IllegalArgumentException("Bad  byte passed to charToCompressedBase: " + base);
        }
    }

    /**
     * Convert from BAM nybble representation of a base in low-order nybble to ASCII byte.
     *
     * @param base One of COMPRESSED_*_LOW, a low-order nybble encoded base.
     * @return ASCII base, one of ACGTN=.
     */
    private static byte compressedBaseToByteLow(final int base) {
        return compressedBaseToByte((byte)(base & 0xf));
    }

    /**
     * Convert from BAM nybble representation of a base in high-order nybble to ASCII byte.
     *
     * @param base One of COMPRESSED_*_HIGH, a high-order nybble encoded base.
     * @return ASCII base, one of ACGTN=.
     */
    private static byte compressedBaseToByteHigh(final int base) {
        return compressedBaseToByte((byte)((base >> 4) & 0xf));
    }

    /**
     * Convert bases in place into canonical form, upper case, and with no-call represented as N.
     *
     * @param bases
     */
    static void normalizeBases(final byte[] bases) {
        for (int i = 0; i < bases.length; ++i) {
            bases[i] = StringUtil.toUpperCase(bases[i]);
            if (bases[i] == '.') {
                bases[i] = 'N';
            }
        }
    }

    /**
     * Convert an array of bytes, in which each byte is a binary phred quality score, to
     * printable ASCII representation of the quality scores, ala FASTQ format.
     * <p/>
     * Equivalent to phredToFastq(data, 0, data.length)
     *
     * @param data Array of bytes in which each byte is a binar phred score.
     * @return String with ASCII representation of those quality scores.
     */
    public static String phredToFastq(final byte[] data) {
        if (data == null) {
            return null;
        }
        return phredToFastq(data, 0, data.length);
    }

    /**
     * Convert an array of bytes, in which each byte is a binary phred quality score, to
     * printable ASCII representation of the quality scores, ala FASTQ format.
     *
     * @param buffer Array of bytes in which each byte is a binar phred score.
     * @param offset Where in buffer to start conversion.
     * @param length How many bytes of buffer to convert.
     * @return String with ASCII representation of those quality scores.
     */
    public static String phredToFastq(final byte[] buffer, final int offset, final int length) {
        final char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = phredToFastq(buffer[offset + i] & 0xFF);
        }
        return new String(chars);
    }

    /**
     * Convert a single binary phred score to printable ASCII representation, ala FASTQ format.
     *
     * @param phredScore binary phred score.
     * @return Printable ASCII representation of phred score.
     */
    public static char phredToFastq(final int phredScore) {
        if (phredScore < 0 || phredScore > MAX_PHRED_SCORE) {
            throw new IllegalArgumentException("Cannot encode phred score: " + phredScore);
        }
        return (char) (33 + phredScore);
    }

    /**
     * Convert a string with phred scores in printable ASCII FASTQ format to an array
     * of binary phred scores.
     *
     * @param fastq Phred scores in FASTQ printable ASCII format.
     * @return byte array of binary phred scores in which each byte corresponds to a character in the input string.
     */
    public static byte[] fastqToPhred(final String fastq) {
        if (fastq == null) {
            return null;
        }
        final int length = fastq.length();
        final byte[] scores = new byte[length];
        for (int i = 0; i < length; i++) {
            scores[i] = (byte) fastqToPhred(fastq.charAt(i));
        }
        return scores;
    }

    /**
     * Converts printable qualities in Sanger fastq format to binary phred scores.
     */
    public static void fastqToPhred(final byte[] fastq) {
        for (int i = 0; i < fastq.length; ++i) {
            fastq[i] = (byte) fastqToPhred((char) (fastq[i] & 0xff));
        }
    }

    /**
     * Convert a single printable ASCII FASTQ format phred score to binary phred score.
     *
     * @param ch Printable ASCII FASTQ format phred score.
     * @return Binary phred score.
     */
    public static int fastqToPhred(final char ch) {
        if (ch < 33 || ch > 126) {
            throw new IllegalArgumentException("Invalid fastq character: " + ch);
        }
        return (ch - 33);
    }

    /**
     * calculate the bin given an alignment in [beg,end)
     * Copied from SAM spec.
     *
     * @param beg 0-based start of read (inclusive)
     * @param end 0-based end of read (exclusive)
     * @deprecated Use GenomicIndexUtil.regionToBin
     */
    static int reg2bin(final int beg, final int end) {
        return GenomicIndexUtil.regionToBin(beg, end);
    }

    /**
     * Handle a list of validation errors according to the validation stringency.
     *
     * @param validationErrors List of errors to report, or null if there are no errors.
     * @param samRecordIndex Record number of the SAMRecord corresponding to the validation errors, or -1 if
     * the record number is not known.
     * @param validationStringency If STRICT, throw a SAMFormatException.  If LENIENT, print the validation
     * errors to stderr.  If SILENT, do nothing.
     */
    public static void processValidationErrors(final List<SAMValidationError> validationErrors,
                                               final long samRecordIndex,
                                               final ValidationStringency validationStringency) {
        if (validationErrors != null && !validationErrors.isEmpty()) {
            for (final SAMValidationError validationError : validationErrors) {
                validationError.setRecordNumber(samRecordIndex);
            }
            if (validationStringency == ValidationStringency.STRICT) {
                throw new SAMFormatException("SAM validation error: " + validationErrors.get(0));
            } else if (validationStringency == ValidationStringency.LENIENT) {
                for (final SAMValidationError error : validationErrors) {
                    System.err.println("Ignoring SAM validation error: " + error);
                }
            }
        }
    }

    public static void processValidationError(final SAMValidationError validationError,
                                              final ValidationStringency validationStringency) {
        if (validationStringency == ValidationStringency.STRICT) {
            throw new SAMFormatException("SAM validation error: " + validationError);
        } else if (validationStringency == ValidationStringency.LENIENT) {
            System.err.println("Ignoring SAM validation error: " + validationError);
        }

    }

    private static final SAMHeaderRecordComparator<SAMReadGroupRecord> HEADER_RECORD_COMPARATOR =
            new SAMHeaderRecordComparator<SAMReadGroupRecord>(
                    SAMReadGroupRecord.PLATFORM_UNIT_TAG,
                    SAMReadGroupRecord.LIBRARY_TAG,
                    SAMReadGroupRecord.DATE_RUN_PRODUCED_TAG,
                    SAMReadGroupRecord.READ_GROUP_SAMPLE_TAG,
                    SAMReadGroupRecord.SEQUENCING_CENTER_TAG,
                    SAMReadGroupRecord.PLATFORM_TAG,
                    SAMReadGroupRecord.DESCRIPTION_TAG,
                    SAMReadGroupRecord.READ_GROUP_ID_TAG    // We don't actually want to compare with ID but it's suitable
                    // "just in case" since it's the only one that's actually required
            );

    /**
     * Calculate a hash code from identifying information in the RG (read group) records in a SAM file's
     * header. This hash code changes any time read groups are added or removed. Comparing one file's
     * hash code to another's tells you if the read groups in the BAM files are different.
     */
    public static String calculateReadGroupRecordChecksum(final File input, final File referenceFasta) {
        final String ENCODING = "UTF-8";

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException nsae) {
            throw new Error("No MD5 algorithm was available in a Java JDK? Unheard-of!");
        }

        // Sort the read group records by their first
        final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(referenceFasta).open(input);
        final List<SAMReadGroupRecord> sortedRecords = new ArrayList<SAMReadGroupRecord>(reader.getFileHeader().getReadGroups());
        Collections.sort(sortedRecords, HEADER_RECORD_COMPARATOR);

        for (final SAMReadGroupRecord rgRecord : sortedRecords) {
            final TreeMap<String, String> sortedAttributes = new TreeMap<String, String>();
            for (final Map.Entry<String, String> attributeEntry : rgRecord.getAttributes()) {
                sortedAttributes.put(attributeEntry.getKey(), attributeEntry.getValue());
            }

            try {
                for (final Map.Entry<String, String> sortedEntry : sortedAttributes.entrySet()) {
                    if (!sortedEntry.getKey().equals(SAMReadGroupRecord.READ_GROUP_ID_TAG)) { // Redundant check, safety first
                        digest.update(sortedEntry.getKey().getBytes(ENCODING));
                        digest.update(sortedEntry.getValue().getBytes(ENCODING));
                    }
                }
            } catch (final UnsupportedEncodingException uee) {
                throw new Error("No " + ENCODING + "!? WTH?");
            }
        }

        // Convert to a String and pad to get the full 32 chars.
        final StringBuilder hashText = new StringBuilder((new BigInteger(1, digest.digest())).toString(16));
        while (hashText.length() < 32) hashText.insert(0, "0");

        CloserUtil.close(reader);
        return hashText.toString();
    }

    /**
     * Chains <code>program</code> in front of the first "head" item in the list of
     * SAMProgramRecords in <code>header</code>.  This method should not be used
     * when there are multiple chains of program groups in a header, only when
     * it can safely be assumed that there is only one chain.  It correctly handles
     * the case where <code>program</code> has already been added to the header, so
     * it can be used whether creating a SAMProgramRecord with a constructor or when
     * calling SAMFileHeader.createProgramRecord().
     */
    public static void chainSAMProgramRecord(final SAMFileHeader header, final SAMProgramRecord program) {

        final List<SAMProgramRecord> pgs = header.getProgramRecords();
        if (!pgs.isEmpty()) {
            final List<String> referencedIds = new ArrayList<String>();
            for (final SAMProgramRecord pg : pgs) {
                if (pg.getPreviousProgramGroupId() != null) {
                    referencedIds.add(pg.getPreviousProgramGroupId());
                }
            }
            for (final SAMProgramRecord pg : pgs) {
                // if record being chained has already been added, ignore it
                if (pg.getProgramGroupId().equals(program.getProgramGroupId())) {
                    continue;
                }
                if (!referencedIds.contains(pg.getProgramGroupId())) {
                    program.setPreviousProgramGroupId(pg.getProgramGroupId());
                    break;
                }
            }
        }
    }

    /**
     * Strip mapping information from a SAMRecord.
     *
     * WARNING: by clearing the secondary and supplementary flags,
     * this may have the affect of producing multiple distinct records with the
     * same read name and flags, which may lead to invalid SAM/BAM output.
     * Callers of this method should make sure to deal with this issue.
     */
    public static void makeReadUnmapped(final SAMRecord rec) {
        if (rec.getReadNegativeStrandFlag()) {
            SAMRecordUtil.reverseComplement(rec);
            rec.setReadNegativeStrandFlag(false);
        }
        rec.setDuplicateReadFlag(false);
        rec.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        rec.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        rec.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
        rec.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
        rec.setInferredInsertSize(0);
        rec.setNotPrimaryAlignmentFlag(false);
        rec.setSupplementaryAlignmentFlag(false);
        rec.setProperPairFlag(false);
        rec.setReadUnmappedFlag(true);
    }

    /**
     * Strip mapping information from a SAMRecord, but preserve it in the 'O' tags if it isn't already set.
     */
    public static void makeReadUnmappedWithOriginalTags(final SAMRecord rec) {
        if (!hasOriginalMappingInformation(rec)) {
            rec.setAttribute(SAMTag.OP.name(), rec.getAlignmentStart());
            rec.setAttribute(SAMTag.OC.name(), rec.getCigarString());
            rec.setAttribute(SAMTag.OF.name(), rec.getFlags());
            rec.setAttribute(SAMTag.OR.name(), rec.getReferenceName());
        }
        makeReadUnmapped(rec);
    }

    /**
     * See if any tags pertaining to original mapping information have been set.
     */
    public static boolean hasOriginalMappingInformation(final SAMRecord rec) {
        return rec.getAttribute(SAMTag.OP.name()) != null
                || rec.getAttribute(SAMTag.OC.name()) != null
                || rec.getAttribute(SAMTag.OF.name()) != null
                || rec.getAttribute(SAMTag.OR.name()) != null;
    }

    /**
     * Determines if a cigar has any element that both consumes read bases and consumes reference bases
     * (e.g. is not all soft-clipped)
     */
    public static boolean cigarMapsNoBasesToRef(final Cigar cigar) {
        for (final CigarElement el : cigar.getCigarElements()) {
            if (el.getOperator().consumesReadBases() && el.getOperator().consumesReferenceBases()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests if the provided record is mapped entirely beyond the end of the reference (i.e., the alignment start is greater than the
     * length of the sequence to which the record is mapped).
     * @param record must not have a null SamFileHeader
     */
    public static boolean recordMapsEntirelyBeyondEndOfReference(final SAMRecord record) {
        if (record.getHeader() == null) {
            throw new SAMException("A non-null SAMHeader is required to resolve the mapping position: " + record.getReadName());
        }
        else {
            return record.getHeader().getSequence(record.getReferenceIndex()).getSequenceLength() < record.getAlignmentStart();
        }
    }

    /**
     * @return negative if mapq1 < mapq2, etc.
     * Note that MAPQ(0) < MAPQ(255) < MAPQ(1)
     */
    public static int compareMapqs(final int mapq1, final int mapq2) {
        if (mapq1 == mapq2) return 0;
        if (mapq1 == 0) return -1;
        else if (mapq2 == 0) return 1;
        else if (mapq1 == 255) return -1;
        else if (mapq2 == 255) return 1;
        else return mapq1 - mapq2;
    }


    /**
     * Hokey algorithm for combining two MAPQs into values that are comparable, being cognizant of the fact
     * that in MAPQ world, 1 > 255 > 0. In this algorithm, 255 is treated as if it were 0.01, so that
     * CombinedMapq(1,0) > CombinedMapq(255, 255) > CombinedMapq(0, 0).
     * The return value should not be used for anything other than comparing to the return value of other
     * invocations of this method.
     */
    public static int combineMapqs(int m1, int m2) {
        if (m1 == 255) m1 = 1;
        else m1 *= 100;

        if (m2 == 255) m2 = 1;
        else m2 *= 100;

        return m1 + m2;

    }

    /**
     * Returns the virtual file offset of the first record in a BAM file - i.e. the virtual file
     * offset after skipping over the text header and the sequence records.
     */
    public static long findVirtualOffsetOfFirstRecordInBam(final File bamFile) {
        try {
            return BAMFileReader.findVirtualOffsetOfFirstRecord(bamFile);
        } catch (final IOException ioe) {
            throw new RuntimeEOFException(ioe);
        }
    }

    /**
     * Given a Cigar, Returns blocks of the sequence that have been aligned directly to the
     * reference sequence. Note that clipped portions, and inserted and deleted bases (vs. the reference)
     * are not represented in the alignment blocks.
     *
     * @param cigar The cigar containing the alignment information
     * @param alignmentStart The start (1-based) of the alignment
     * @param cigarTypeName The type of cigar passed - for error logging.
     * @return List of alignment blocks
     */
    public static List<AlignmentBlock> getAlignmentBlocks(final Cigar cigar, final int alignmentStart, final String cigarTypeName) {
        if (cigar == null) return Collections.emptyList();

        final List<AlignmentBlock> alignmentBlocks = new ArrayList<AlignmentBlock>();
        int readBase = 1;
        int refBase = alignmentStart;

        for (final CigarElement e : cigar.getCigarElements()) {
            switch (e.getOperator()) {
                case H:
                    break; // ignore hard clips
                case P:
                    break; // ignore pads
                case S:
                    readBase += e.getLength();
                    break; // soft clip read bases
                case N:
                    refBase += e.getLength();
                    break;  // reference skip
                case D:
                    refBase += e.getLength();
                    break;
                case I:
                    readBase += e.getLength();
                    break;
                case M:
                case EQ:
                case X:
                    final int length = e.getLength();
                    alignmentBlocks.add(new AlignmentBlock(readBase, refBase, length));
                    readBase += length;
                    refBase += length;
                    break;
                default:
                    throw new IllegalStateException("Case statement didn't deal with " + cigarTypeName + " op: " + e.getOperator());
            }
        }
        return Collections.unmodifiableList(alignmentBlocks);
    }

    /**
     * @param alignmentStart The start (1-based) of the alignment
     * @param cigar The cigar containing the alignment information
     * @return the alignment start (1-based, inclusive) adjusted for clipped bases.  For example if the read
     * has an alignment start of 100 but the first 4 bases were clipped (hard or soft clipped)
     * then this method will return 96.
     * <p/>
     * Invalid to call on an unmapped read.
     * Invalid to call with cigar = null
     */
    public static int getUnclippedStart(final int alignmentStart, final Cigar cigar) {
        int unClippedStart = alignmentStart;
        for (final CigarElement cig : cigar.getCigarElements()) {
            final CigarOperator op = cig.getOperator();
            if (op == CigarOperator.SOFT_CLIP || op == CigarOperator.HARD_CLIP) {
                unClippedStart -= cig.getLength();
            } else {
                break;
            }
        }

        return unClippedStart;
    }

    /**
     * @param alignmentEnd The end (1-based) of the alignment
     * @param cigar The cigar containing the alignment information
     * @return the alignment end (1-based, inclusive) adjusted for clipped bases.  For example if the read
     * has an alignment end of 100 but the last 7 bases were clipped (hard or soft clipped)
     * then this method will return 107.
     * <p/>
     * Invalid to call on an unmapped read.
     * Invalid to call with cigar = null
     */
    public static int getUnclippedEnd(final int alignmentEnd, final Cigar cigar) {
        int unClippedEnd = alignmentEnd;
        final List<CigarElement> cigs = cigar.getCigarElements();
        for (int i = cigs.size() - 1; i >= 0; --i) {
            final CigarElement cig = cigs.get(i);
            final CigarOperator op = cig.getOperator();

            if (op == CigarOperator.SOFT_CLIP || op == CigarOperator.HARD_CLIP) {
                unClippedEnd += cig.getLength();
            } else {
                break;
            }
        }

        return unClippedEnd;
    }

    /**
     * Returns the Mate Cigar String as stored in the attribute 'MC'.
     *
     * @param rec the SAM record
     * @return Mate Cigar String, or null if there is none.
     */
    public static String getMateCigarString(final SAMRecord rec) {
        return rec.getStringAttribute(SAMTag.MC.name());
    }

    /**
     * Returns the Mate Cigar or null if there is none.
     *
     * @param rec the SAM record
     * @param withValidation true if we are to validate the mate cigar before returning, false otherwise.
     * @return Cigar object for the read's mate, or null if there is none.
     */
    public static Cigar getMateCigar(final SAMRecord rec, final boolean withValidation) {
        final String mateCigarString = getMateCigarString(rec);
        Cigar mateCigar = null;
        if (mateCigarString != null) {
            mateCigar = TextCigarCodec.decode(mateCigarString);
            if (withValidation && rec.getValidationStringency() != ValidationStringency.SILENT) {
                final List<AlignmentBlock> alignmentBlocks = getAlignmentBlocks(mateCigar, rec.getMateAlignmentStart(), "mate cigar");
                SAMUtils.processValidationErrors(validateCigar(rec, mateCigar, rec.getMateReferenceIndex(), alignmentBlocks, -1, "Mate CIGAR"), -1L, rec.getValidationStringency());
            }
        }
        return mateCigar;
    }

    /**
     * Returns the Mate Cigar or null if there is none.  No validation is done on the returned cigar.
     *
     * @param rec the SAM record
     * @return Cigar object for the read's mate, or null if there is none.
     */
    public static Cigar getMateCigar(final SAMRecord rec) {
        return getMateCigar(rec, false);
    }

    /**
     * @param rec the SAM record
     * @return number of cigar elements (number + operator) in the mate cigar string.
     */
    public static int getMateCigarLength(final SAMRecord rec) {
        final Cigar mateCigar = getMateCigar(rec);
        return (mateCigar != null) ? mateCigar.numCigarElements() : 0;
    }

    /**
     * This method uses the MateCigar value as determined from the attribute MC.  It must be non-null.
     *
     * @param rec the SAM record
     * @return 1-based inclusive rightmost position of the clipped mate sequence, or 0 read if unmapped.
     */
    public static int getMateAlignmentEnd(final SAMRecord rec) {
        if (rec.getMateUnmappedFlag()) {
            throw new RuntimeException("getMateAlignmentEnd called on an unmapped mate.");
        }
        final Cigar mateCigar = SAMUtils.getMateCigar(rec);
        if (mateCigar == null) {
            throw new SAMException("Mate CIGAR (Tag MC) not found.");
        }
        return CoordMath.getEnd(rec.getMateAlignmentStart(), mateCigar.getReferenceLength());
    }

    /**
     * @param rec the SAM record
     * @return the mate alignment start (1-based, inclusive) adjusted for clipped bases.  For example if the mate
     * has an alignment start of 100 but the first 4 bases were clipped (hard or soft clipped)
     * then this method will return 96.
     * <p/>
     * Invalid to call on an unmapped read.
     */
    public static int getMateUnclippedStart(final SAMRecord rec) {
        if (rec.getMateUnmappedFlag())
            throw new RuntimeException("getMateUnclippedStart called on an unmapped mate.");
        final Cigar mateCigar = getMateCigar(rec);
        if (mateCigar == null) {
            throw new SAMException("Mate CIGAR (Tag MC) not found.");
        }
        return SAMUtils.getUnclippedStart(rec.getMateAlignmentStart(), mateCigar);
    }


    /**
     * @param rec the SAM record
     * @return the mate alignment end (1-based, inclusive) adjusted for clipped bases.  For example if the mate
     * has an alignment end of 100 but the last 7 bases were clipped (hard or soft clipped)
     * then this method will return 107.
     * <p/>
     * Invalid to call on an unmapped read.
     */
    public static int getMateUnclippedEnd(final SAMRecord rec) {
        if (rec.getMateUnmappedFlag()) {
            throw new RuntimeException("getMateUnclippedEnd called on an unmapped mate.");
        }
        final Cigar mateCigar = SAMUtils.getMateCigar(rec);
        if (mateCigar == null) {
            throw new SAMException("Mate CIGAR (Tag MC) not found.");
        }
        return SAMUtils.getUnclippedEnd(getMateAlignmentEnd(rec), mateCigar);
    }

    /**
     * @param rec the SAM record
     * Returns blocks of the mate sequence that have been aligned directly to the
     * reference sequence. Note that clipped portions of the mate and inserted and
     * deleted bases (vs. the reference) are not represented in the alignment blocks.
     */
    public static List<AlignmentBlock> getMateAlignmentBlocks(final SAMRecord rec) {
        return getAlignmentBlocks(getMateCigar(rec), rec.getMateAlignmentStart(), "mate cigar");
    }

    /**
     * Run all validations of the mate's CIGAR.  These include validation that the CIGAR makes sense independent of
     * placement, plus validation that CIGAR + placement yields all bases with M operator within the range of the reference.
     *
     * @param rec the SAM record
     * @param cigar The cigar containing the alignment information
     * @param referenceIndex The reference index
     * @param alignmentBlocks The alignment blocks (parsed from the cigar)
     * @param recordNumber For error reporting.  -1 if not known.
     * @param cigarTypeName For error reporting.  "Read CIGAR" or "Mate Cigar"
     * @return List of errors, or null if no errors.
     */

    public static List<SAMValidationError> validateCigar(final SAMRecord rec,
                                                         final Cigar cigar,
                                                         final Integer referenceIndex,
                                                         final List<AlignmentBlock> alignmentBlocks,
                                                         final long recordNumber,
                                                         final String cigarTypeName) {
        // Don't know line number, and don't want to force read name to be decoded.
        List<SAMValidationError> ret = cigar.isValid(rec.getReadName(), recordNumber);
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            SAMFileHeader samHeader = rec.getHeader();
            if (null == samHeader) {
                if (ret == null) ret = new ArrayList<SAMValidationError>();
                ret.add(new SAMValidationError(SAMValidationError.Type.MISSING_HEADER,
                        cigarTypeName + " A non-null SAMHeader is required to validate cigar elements for: ", rec.getReadName(), recordNumber));
            }
            else {
                final SAMSequenceRecord sequence = samHeader.getSequence(referenceIndex);
                final int referenceSequenceLength = sequence.getSequenceLength();
                for (final AlignmentBlock alignmentBlock : alignmentBlocks) {
                    if (alignmentBlock.getReferenceStart() + alignmentBlock.getLength() - 1 > referenceSequenceLength) {
                        if (ret == null) ret = new ArrayList<SAMValidationError>();
                        ret.add(new SAMValidationError(SAMValidationError.Type.CIGAR_MAPS_OFF_REFERENCE,
                                cigarTypeName + " M operator maps off end of reference", rec.getReadName(), recordNumber));
                        break;
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Run all validations of the mate's CIGAR.  These include validation that the CIGAR makes sense independent of
     * placement, plus validation that CIGAR + placement yields all bases with M operator within the range of the reference.
     *
     * @param rec the SAM record
     * @param recordNumber For error reporting.  -1 if not known.
     * @return List of errors, or null if no errors.
     */
    public static List<SAMValidationError> validateMateCigar(final SAMRecord rec, final long recordNumber) {
        List<SAMValidationError> ret = null;

        if (rec.getValidationStringency() != ValidationStringency.SILENT) {
            if (rec.getReadPairedFlag() && !rec.getMateUnmappedFlag()) {      // The mateCigar will be defined if the mate is mapped
                if (getMateCigarString(rec) != null) {
                    ret = SAMUtils.validateCigar(rec, getMateCigar(rec), rec.getMateReferenceIndex(), getMateAlignmentBlocks(rec), recordNumber, "Mate CIGAR");
                }
            } else {
                if (getMateCigarString(rec) != null) {
                    ret = new ArrayList<SAMValidationError>();
                    if (!rec.getReadPairedFlag()) {
                        // If the read is not paired, and the Mate Cigar String (MC Attribute) exists, that is a validation error
                        ret.add(new SAMValidationError(SAMValidationError.Type.MATE_CIGAR_STRING_INVALID_PRESENCE,
                                "Mate CIGAR String (MC Attribute) present for a read that is not paired", rec.getReadName(), recordNumber));
                    } else { // will hit here if rec.getMateUnmappedFlag() is true
                        // If the Mate is unmapped, and the Mate Cigar String (MC Attribute) exists, that is a validation error.
                        ret.add(new SAMValidationError(SAMValidationError.Type.MATE_CIGAR_STRING_INVALID_PRESENCE,
                                "Mate CIGAR String (MC Attribute) present for a read whose mate is unmapped", rec.getReadName(), recordNumber));
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Checks to see if it is valid for this record to have a mate CIGAR (MC) and then if there is a mate CIGAR available.  This is done by
     * checking that this record is paired, its mate is mapped, and that it returns a non-null mate CIGAR.
     *
     * @param rec
     * @return
     */
    public static boolean hasMateCigar(SAMRecord rec) {
        // NB: use getMateCigarString rather than getMateCigar to avoid validation.
        return (rec.getReadPairedFlag() && !rec.getMateUnmappedFlag() && null != SAMUtils.getMateCigarString(rec));
    }

    /**
     * Returns a string that is the the read group ID and read name separated by a colon.  This is meant to cannonically
     * identify a given record within a set of records.
     *
     * @param record
     * @return
     */
    public static String getCanonicalRecordName(final SAMRecord record) {
        String name = record.getStringAttribute(ReservedTagConstants.READ_GROUP_ID);
        if (null == name) name = record.getReadName();
        else name = name + ":" + record.getReadName();
        return name;
    }

    /**
     * Returns the number of bases that need to be clipped due to overlapping pairs.  If the record is not paired,
     * or the given record's start position is greater than its mate's start position, zero is automatically returned.
     * NB: This method assumes that the record's mate is not contained within the given record's alignment.
     *
     * @param rec
     * @return the number of bases at the end of the read that need to be clipped such that there would be no overlapping bases with its mate.
     * Read bases include only those from insertion, match, or mismatch Cigar operators.
     */
    public static int getNumOverlappingAlignedBasesToClip(final SAMRecord rec) {
        // NB: ignores how to handle supplemental records when present for both ends by just using the mate information in the record.

        if (!rec.getReadPairedFlag() || rec.getReadUnmappedFlag() || rec.getMateUnmappedFlag()) return 0;

        // Only clip records that are left-most in genomic order and overlapping.
        if (rec.getMateAlignmentStart() < rec.getAlignmentStart()) return 0; // right-most, so ignore.
        else if (rec.getMateAlignmentStart() == rec.getAlignmentStart() && rec.getFirstOfPairFlag()) return 0; // same start, so pick the first end

        // Find the number of read bases after the given mate's alignment start.
        int numBasesToClip = 0;
        final int refStartPos = rec.getMateAlignmentStart(); // relative reference position after which we should start clipping
        final Cigar cigar = rec.getCigar();
        int refPos = rec.getAlignmentStart();
        for (final CigarElement el : cigar.getCigarElements()) {
            final CigarOperator operator = el.getOperator();
            final int refBasesLength = operator.consumesReferenceBases() ? el.getLength() : 0;
            if (refStartPos <= refPos + refBasesLength - 1) { // add to clipped bases
                if (operator == CigarOperator.MATCH_OR_MISMATCH) { // M
                    if (refStartPos < refPos) numBasesToClip += refBasesLength; // use all of the bases
                    else numBasesToClip += (refPos + refBasesLength) - refStartPos;  // since the mate's alignment start can be in the middle of a cigar element
                }
                else if (operator == CigarOperator.SOFT_CLIP || operator == CigarOperator.HARD_CLIP || operator == CigarOperator.PADDING || operator == CigarOperator.SKIPPED_REGION) {
                    // ignore
                }
                else { // ID
                    numBasesToClip += operator.consumesReadBases() ? el.getLength() : 0; // clip all the bases in the read from this operator
                }
            }
            refPos += refBasesLength;
        }

        if (numBasesToClip < 0) return 0; // left-most but not overlapping

        return numBasesToClip;
    }

    /**
     * Returns a (possibly new) record that has been clipped if isa  mapped paired and has overlapping bases with its mate.
     * See {@link #getNumOverlappingAlignedBasesToClip(SAMRecord)} for how the number of overlapping bases is computed.
     * NB: this does not properly consider a cigar like: 100M20S10H.
     * NB: This method assumes that the record's mate is not contained within the given record's alignment.
     *
     * @param record the record from which to clip bases.
     * @param noSideEffects if true a modified clone of the original record is returned, otherwise we modify the record directly.
     * @return
     */
    public static SAMRecord clipOverlappingAlignedBases(final SAMRecord record, final boolean noSideEffects) {
        return clipOverlappingAlignedBases(record, getNumOverlappingAlignedBasesToClip(record), noSideEffects);
    }

    /**
     * Returns a (possibly new) SAMRecord with the given number of bases soft-clipped at the end of the read if is a mapped
     * paired and has overlapping bases with its mate.
     * NB: this does not properly consider a cigar like: 100M20S10H.
     * NB: This method assumes that the record's mate is not contained within the given record's alignment.
     *
     * @param record the record from which to clip bases.
     * @param numOverlappingBasesToClip the number of bases to clip at the end of the read.
     * @param noSideEffects if true a modified clone of the original record is returned, otherwise we modify the record directly.
     * @return
     */
    public static SAMRecord clipOverlappingAlignedBases(final SAMRecord record, final int numOverlappingBasesToClip, final boolean noSideEffects) {
        // NB: ignores how to handle supplemental records when present for both ends by just using the mate information in the record.

        if (numOverlappingBasesToClip <= 0 || record.getReadUnmappedFlag() || record.getMateUnmappedFlag()) return record;

        try {
            final SAMRecord rec = noSideEffects ? ((SAMRecord)record.clone()) : record;

            // watch out for when the second read overlaps all of the first read
            if (rec.getMateAlignmentStart() <= rec.getAlignmentStart()) { // make it unmapped
                rec.setReadUnmappedFlag(true);
                return rec;
            }

            // 1-based index of first base in read to clip.
            int clipFrom = rec.getReadLength() - numOverlappingBasesToClip + 1;
            // we have to check if the last cigar element is soft-clipping, so we can subtract that from clipFrom
            final CigarElement cigarElement = rec.getCigar().getCigarElement(rec.getCigarLength()-1);
            if (CigarOperator.SOFT_CLIP == cigarElement.getOperator()) clipFrom -= cigarElement.getLength();
            // FIXME: does not properly consider a cigar like: 100M20S10H

            // clip it, clip it good
            rec.setCigar(new Cigar(CigarUtil.softClipEndOfRead(clipFrom, rec.getCigar().getCigarElements())));
            return rec;
        } catch (final CloneNotSupportedException e) {
            throw new SAMException(e.getMessage(), e);
        }
    }

    /**
     * Checks if a long attribute value is within the allowed range of a 32-bit unsigned integer.
     *
     * @param value a long value to check
     * @return true if value is >= 0 and <= {@link BinaryCodec#MAX_UINT}, and false otherwise
     */
    public static boolean isValidUnsignedIntegerAttribute(long value) {
        return value >= 0 && value <= BinaryCodec.MAX_UINT;
    }
}
