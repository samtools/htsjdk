/*
 * The MIT License
 *
 * Copyright (c) 2016 Daniel Gomez-Sanchez
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
package htsjdk.samtools.fastq;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.TextTagCodec;
import htsjdk.samtools.util.SequenceUtil;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Codec for encoding records into FASTQ format.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public final class FastqEncoder {

    // cannot be instantiated because it is an utility class
    private FastqEncoder() {}

    /**
     * Encodes a FastqRecord in the String FASTQ format.
     */
    public static String encode(final FastqRecord record) {
        // reserve some memory based on the read length
        int capacity = record.getReadLength() * 2 +  5;
        // reserve some memory based on the read name
        if (record.getReadName() != null) {
            capacity += record.getReadName().length();
        }
        return write(new StringBuilder(capacity), record).toString();
    }

    /**
     * Writes a FastqRecord into the Appendable output.
     * @throws SAMException if any I/O error occurs.
     */
    public static Appendable write(final Appendable out,final FastqRecord record) {
        final String readName = record.getReadName();
        final String readString = record.getReadString();
        final String qualHeader = record.getBaseQualityHeader();
        final String qualityString = record.getBaseQualityString();
        try {
            return out.append(FastqConstants.SEQUENCE_HEADER)
                    .append(readName == null ? "" : readName).append('\n')
                    .append(readString == null ? "" : readString).append('\n')
                    .append(FastqConstants.QUALITY_HEADER)
                    .append(qualHeader == null ? "" : qualHeader).append('\n')
                    .append(qualityString == null ? "" : qualityString);
        } catch (IOException e) {
            throw new SAMException(e);
        }
    }

    /**
     * Encodes a SAMRecord in the String FASTQ format.
     * @see #encode(FastqRecord)
     * @see #asSAMRecord(FastqRecord, SAMFileHeader)
     */
    public static String encode(final SAMRecord record) {
        return encode(asFastqRecord(record));
    }

    /**
     * Converts a {@link SAMRecord} into a {@link FastqRecord}.
     */
    public static FastqRecord asFastqRecord(final SAMRecord record) {
        String readName = record.getReadName();
        if(record.getReadPairedFlag() && (record.getFirstOfPairFlag() || record.getSecondOfPairFlag())) {
            readName += (record.getFirstOfPairFlag()) ? FastqConstants.FIRST_OF_PAIR : FastqConstants.SECOND_OF_PAIR;
        }
        return new FastqRecord(readName, record.getReadString(), record.getStringAttribute(SAMTag.CO.name()), record.getBaseQualityString());
    }

    /**
     * Converts a {@link FastqRecord} into a simple unmapped {@link SAMRecord}.
     */
    public static SAMRecord asSAMRecord(final FastqRecord record, final SAMFileHeader header) {
        return asSAMRecord(record, header, (s, r) -> {});
    }

    /**
     * Converts a {@link FastqRecord} into a simple unmapped {@link SAMRecord}.
     *
     * <p>This method allows to pass a {@link BiConsumer} to add the information from the record in
     * a customizable manner.
     *
     * @param record    object to encode.
     * @param header    header for the returned object.
     * @param custom    function to customize encoding. Note that default information might be overriden.
     */
    public static SAMRecord asSAMRecord(final FastqRecord record, final SAMFileHeader header, final BiConsumer<FastqRecord, SAMRecord> custom) {
        // construct the SAMRecord and set the unmapped flag
        final SAMRecord samRecord = new SAMRecord(header);
        samRecord.setReadUnmappedFlag(true);
        // get the read name from the FastqRecord correctly formatted
        final String readName = SequenceUtil.getSamReadNameFromFastqHeader(record.getReadName());
        // set the basic information from the FastqRecord
        samRecord.setReadName(readName);
        samRecord.setReadBases(record.getReadBases());
        samRecord.setBaseQualities(record.getBaseQualities());
        custom.accept(record, samRecord);
        return samRecord;
    }

    /**
     * Encodes the quality header into the comment tag (use in {@link #asSAMRecord(FastqRecord, SAMFileHeader, BiConsumer)}.
     *
     * <p>Note that all tabs present in the quality header are replaced by spaces.
     */
    public static final BiConsumer<FastqRecord, SAMRecord> QUALITY_HEADER_TO_COMMENT_TAG = (record, samRecord) ->
        samRecord.setAttribute(SAMTag.CO.name(), record.getBaseQualityHeader().replaceAll("\t", " "));


    public static final BiConsumer<FastqRecord, SAMRecord> QUALITY_HEADER_PARSE_SAM_TAGS = (record, samRecord) -> {
        final String[] tokens = record.getBaseQualityHeader().split("\t");
        final TextTagCodec codec = new TextTagCodec();
        for (final String token: tokens) {
            final Map.Entry<String, Object> tagValue = codec.decode(token);
            samRecord.setAttribute(tagValue.getKey(), tagValue.getValue());
        }
    };
}
