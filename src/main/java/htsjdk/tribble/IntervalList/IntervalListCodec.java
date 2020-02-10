/*
 * Copyright (c) 2019, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.tribble.IntervalList;

import htsjdk.samtools.*;
import htsjdk.samtools.util.FormatUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.LineReader;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.AsciiFeatureCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.readers.LineIterator;

/**
 * A tribble codec for IntervalLists.
 *
 * Also contains the parsing code for the non-tribble parsing of IntervalLists
 */

public class IntervalListCodec extends AsciiFeatureCodec<Interval> {

    private final Log log = Log.getInstance(IntervalListCodec.class);

    private SAMSequenceDictionary dictionary;

    public IntervalListCodec() {
        this(null);
    }

    public IntervalListCodec(final SAMSequenceDictionary dict) {
        super(Interval.class);
        dictionary = dict;
    }

    String lastSeq = null;

    private Interval parseIntervalString(final String line, final SAMSequenceDictionary dict) {
        final int SEQUENCE_POS = 0;
        final int START_POS = 1;
        final int END_POS = 2;
        final int STRAND_POS = 3;
        final int NAME_POS = 4;

        final FormatUtil format = new FormatUtil();

        // Make sure we have the right number of fields
        final String[] fields = line.split("\t");
        if (fields.length != 5) {
            throw new TribbleException("Invalid interval record contains " +
                    fields.length + " fields: " + line);
        }

        // Then parse them out
        String seq = fields[SEQUENCE_POS];
        if (seq.equals(lastSeq)) {
            seq = lastSeq;
        }
        lastSeq = seq;

        final int start = format.parseInt(fields[START_POS]);
        final int end = format.parseInt(fields[END_POS]);
        if (start < 1) {
            throw new IllegalArgumentException("Coordinate less than 1: start value of " + start +
                    " is less than 1 and thus illegal");
        }

        if (start > end + 1) {
            throw new IllegalArgumentException("Start value of " + start +
                    " is greater than end + 1 for end of value: " + end +
                    ". I'm afraid I cannot let you do that.");
        }

        Strand strand = Strand.decode(fields[STRAND_POS]);
        if (strand==Strand.NONE)  throw new IllegalArgumentException("Invalid strand field: " + fields[STRAND_POS]);

        final String name = fields[NAME_POS];

        final Interval interval = new Interval(seq, start, end, strand==Strand.NEGATIVE, name);
        final SAMSequenceRecord sequence = dict.getSequence(seq);
        if (sequence == null) {
            log.warn("Ignoring interval for unknown reference: " + interval);
            return null;
        } else {
            final int sequenceLength = sequence.getSequenceLength();
            if (sequenceLength > 0 && sequenceLength < end) {
                throw new IllegalArgumentException("interval with end: " + end + " extends beyond end of sequence with length: " + sequenceLength);
            }
            return interval;
        }
    }

    @Override
    public Interval decode(final String line) {
        if (line.startsWith("@")) {
            return null;
        }

        if (line.trim().isEmpty()) {
            return null;
        }
        // our header cannot be null, we need the dictionary from the header
        if (dictionary == null) {
            throw new TribbleException("IntervalList dictionary cannot be null when decoding a record");
        }

        return parseIntervalString(line, dictionary);
    }


    @Override
    public Object readActualHeader(LineIterator lineIterator) {
        final SAMTextHeaderCodec headerCodec = new SAMTextHeaderCodec();
        final SAMFileHeader header = headerCodec.decode(new LineReader() {
            int lineNo = 0;
            @Override
            public String readLine() {
                lineNo++;
                return lineIterator.next();
            }
            @Override
            public int getLineNumber() {
                return lineNo;
            }
            @Override
            public int peek() {
                return lineIterator.hasNext() ?
                        lineIterator.peek().charAt(0) :
                        LineReader.EOF_VALUE;
            }
            @Override
            public void close() { }
        }, "IntervalListCodec");
        dictionary = header.getSequenceDictionary();
        return header;
    }

    @Override
    public boolean canDecode(String s) {
        return s.endsWith(".interval_list");
    }
}
