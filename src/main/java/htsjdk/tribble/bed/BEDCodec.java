/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble.bed;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.AsciiFeatureCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.util.ParsingUtils;
import java.util.regex.Pattern;

/**
 * Codec for parsing BED file, as described by UCSC
 * See https://genome.ucsc.edu/FAQ/FAQformat.html#format1
 *
 * @author jrobinso
 *         Date: Dec 20, 2009
 */
public class BEDCodec extends AsciiFeatureCodec<BEDFeature> {

    /** Default extension for BED files. */
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#BED} instead.
     */
    @Deprecated
    public static final String BED_EXTENSION = FileExtensions.BED;

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t|( +)");
    private final int startOffsetValue;

    /**
     * Calls {@link #BEDCodec(StartOffset)} with an argument
     * of {@code StartOffset.ONE}
     */
    public BEDCodec() {
        this(StartOffset.ONE);
    }

    /**
     * BED format is 0-based, but Tribble is 1-based.
     * Set desired start position at either ZERO or ONE
     */
    public BEDCodec(final StartOffset startOffset) {
        super(BEDFeature.class);
        this.startOffsetValue = startOffset.value();
    }

    public BEDFeature decodeLoc(String line) {
        return decode(line);
    }

    @Override
    public BEDFeature decode(String line) {

        if (line.trim().isEmpty()) {
            return null;
        }
        // discard header lines in case the caller hasn't called readHeader
        if (isBEDHeaderLine(line)) {
            this.readHeaderLine(line);
            return null;
        }

        String[] tokens = SPLIT_PATTERN.split(line, -1);
        return decode(tokens);
    }

    /**
     * The BED codec doesn't retain the actual header, but we need to parse through
     * it and advance to the beginning of the first feature. This is especially true
     * if we're indexing, since we want to underlying stream offset to be established
     * correctly, but is also the case for when we're simply iterating to satisfy a
     * feature query (otherwise the feature reader can terminate prematurely if the
     * header is large).
     * @param lineIterator
     * @return Always null. The BEDCodec currently doesn't model or preserve the BED header.
     */
    @Override
    public Object readActualHeader(final LineIterator lineIterator) {
        while (lineIterator.hasNext()) {
            // Only peek, since we don't want to actually consume a line of input unless its a header line.
            // This prevents us from advancing past the first feature.
            final String nextLine = lineIterator.peek();
            if (isBEDHeaderLine(nextLine)) {
                // advance the iterator and consume the line (which is a no-op)
                this.readHeaderLine(lineIterator.next());
            } else {
                return null; // break out when we've seen the end of the header
            }
        }

        return null;
    }

    // Return true if the candidateLine looks like a BED header line.
    private boolean isBEDHeaderLine(final String candidateLine) {
        return candidateLine.startsWith("#")
                || candidateLine.startsWith("track")
                || candidateLine.startsWith("browser");
    }

    /**
     * Decodes the columns of one BED line.
     *
     * @param tokens the line's columns
     * @return the feature, or null if there are fewer than two columns
     * @throws TribbleException.MalformedFeatureFile if a column it reads is malformed; the message names the column
     *     and its value and shows the line
     */
    public BEDFeature decode(String[] tokens) {
        int tokenCount = tokens.length;

        // The first 3 columns are non optional for BED.  We will relax this
        // and only require 2.

        if (tokenCount < 2) {
            return null;
        }

        String chr = tokens[0];

        // The BED format uses a first-base-is-zero convention,  Tribble features use 1 => add 1.
        int start = parseInteger(tokens, 1, "chromStart") + startOffsetValue;

        int end = start;
        if (tokenCount > 2) {
            end = parseInteger(tokens, 2, "chromEnd");
        }

        FullBEDFeature feature = new FullBEDFeature(chr, start, end);

        // The rest of the columns are optional.  Stop parsing upon encountering
        // a non-expected value

        // Name
        if (tokenCount > 3) {
            String name = tokens[3].replaceAll("\"", "");
            feature.setName(name);
        }

        // Score
        if (tokenCount > 4) {
            try {
                float score = Float.parseFloat(tokens[4]);
                feature.setScore(score);
            } catch (NumberFormatException numberFormatException) {

                // Unexpected, but does not invalidate the previous values.
                // Stop parsing the line here but keep the feature
                // Don't log, would just slow parsing down.
                return feature;
            }
        }

        // Strand
        if (tokenCount > 5) {
            String strandString = tokens[5].trim();
            char strand = (strandString.isEmpty()) ? ' ' : strandString.charAt(0);

            if (strand == '-') {
                feature.setStrand(Strand.NEGATIVE);
            } else if (strand == '+') {
                feature.setStrand(Strand.POSITIVE);
            } else {
                feature.setStrand(Strand.NONE);
            }
        }

        // Color
        if (tokenCount > 8) {
            String colorString = tokens[8];
            try {
                feature.setColor(ParsingUtils.parseColor(colorString));
            } catch (final IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                throw malformedLine(tokens, "itemRgb '" + colorString + "' is not a colour", e);
            }
        }

        // Coding information is optional
        if (tokenCount > 11) {
            createExons(start, tokens, feature, feature.getStrand());
        }

        return feature;
    }

    protected boolean readHeaderLine(String line) {
        // We don't parse BED header
        return false;
    }

    private void createExons(int start, String[] tokens, FullBEDFeature gene, Strand strand) {

        int cdStart = parseInteger(tokens, 6, "thickStart") + startOffsetValue;
        int cdEnd = parseInteger(tokens, 7, "thickEnd");

        int exonCount = parseInteger(tokens, 9, "blockCount");
        if (exonCount < 1) {
            throw malformedLine(tokens, "blockCount " + exonCount + " is not a positive integer", null);
        }
        String[] exonSizes = new String[exonCount];
        String[] startsBuffer = new String[exonCount];
        ParsingUtils.split(tokens[10], exonSizes, ',');
        ParsingUtils.split(tokens[11], startsBuffer, ',');

        int exonNumber = (strand == Strand.NEGATIVE ? exonCount : 1);

        if (startsBuffer.length == exonSizes.length) {
            for (int i = 0; i < startsBuffer.length; i++) {
                int exonStart = start + parseBlockListEntry(tokens, 11, "blockStarts", startsBuffer[i], exonCount);
                int exonEnd = exonStart + parseBlockListEntry(tokens, 10, "blockSizes", exonSizes[i], exonCount) - 1;
                gene.addExon(exonStart, exonEnd, cdStart, cdEnd, exonNumber);

                if (strand == Strand.NEGATIVE) {
                    exonNumber--;
                } else {
                    exonNumber++;
                }
            }
        }
    }

    /** Parses the integer in column {@code index}, naming the column and showing the line if it is not one. */
    private int parseInteger(final String[] tokens, final int index, final String column) {
        try {
            return Integer.parseInt(tokens[index]);
        } catch (final NumberFormatException e) {
            throw malformedLine(tokens, column + " '" + tokens[index] + "' is not an integer", e);
        }
    }

    /**
     * Parses one entry of the comma-separated list in column {@code index}, naming the column and showing the line if
     * the entry is missing or not an integer.
     */
    private int parseBlockListEntry(
            final String[] tokens, final int index, final String column, final String entry, final int blockCount) {
        try {
            return Integer.parseInt(entry);
        } catch (final NumberFormatException e) {
            throw malformedLine(
                    tokens, column + " '" + tokens[index] + "' is not a list of " + blockCount + " integers", e);
        }
    }

    /**
     * Builds the exception for a line that cannot be decoded. Its source is unset, for the reader that knows the file
     * name to fill in.
     */
    private TribbleException.MalformedFeatureFile malformedLine(
            final String[] tokens, final String problem, final Exception cause) {
        return new TribbleException.MalformedFeatureFile(
                "Malformed BED line: " + problem + ".  Line is: " + String.join("\t", tokens), null, cause);
    }

    @Override
    public boolean canDecode(final String path) {
        final String toDecode;
        if (IOUtil.hasBlockCompressedExtension(path)) {
            toDecode = path.substring(0, path.lastIndexOf("."));
        } else {
            toDecode = path;
        }
        return toDecode.toLowerCase().endsWith(FileExtensions.BED);
    }

    public int getStartOffset() {
        return this.startOffsetValue;
    }

    /**
     * Indicate whether co-ordinates or 0-based or 1-based.
     * <p/>
     * Tribble uses 1-based, BED files use 0.
     * e.g.:
     * start_position = bedline_start_position - startIndex.value()
     */
    public enum StartOffset {
        ZERO(0),
        ONE(1);
        private int start;

        private StartOffset(int start) {
            this.start = start;
        }

        public int value() {
            return this.start;
        }
    }

    @Override
    public TabixFormat getTabixFormat() {
        return TabixFormat.BED;
    }
}
