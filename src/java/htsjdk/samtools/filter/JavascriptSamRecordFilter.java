/*
 * The MIT License
 *
 * Copyright (c) 2015 Pierre Lindenbaum @yokofakun Institut du Thorax - Nantes - France
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
package htsjdk.samtools.filter;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

/**
 * javascript based read filter
 * 
 * 
 * The script puts the following variables in the script context:
 *
 * - 'record' a SamRecord (
 * https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/
 * SAMRecord.java ) - 'header' (
 * https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/
 * SAMFileHeader.java )
 * 
 * @author Pierre Lindenbaum PhD Institut du Thorax - INSERM - Nantes - France
 */
public class JavascriptSamRecordFilter extends AbstractJavascriptFilter<SAMFileHeader, SAMRecord>
        implements SamRecordFilter {
    /**
     * constructor using a javascript File
     * 
     * @param scriptFile
     *            the javascript file to be compiled
     * @param header
     *            the SAMHeader
     */
    public JavascriptSamRecordFilter(final File scriptFile, final SAMFileHeader header) throws IOException {
        super(scriptFile, header);
    }

    /**
     * constructor using a javascript expression
     * 
     * @param scriptExpression
     *            the javascript expression to be compiled
     * @param header
     *            the SAMHeader
     */
    public JavascriptSamRecordFilter(final String scriptExpression, final SAMFileHeader header) {
        super(scriptExpression, header);
    }

    /**
     * constructor using a java.io.Reader
     * 
     * @param scriptReader
     *            the javascript reader to be compiled. will be closed
     * @param header
     *            the SAMHeader
     */
    public JavascriptSamRecordFilter(final Reader scriptReader, final SAMFileHeader header) {
        super(scriptReader, header);
    }

    /** return true of both records are filteredOut (AND) */
    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        return filterOut(first) && filterOut(second);
    }

    /** read is filtered out if the javascript program returns false */
    @Override
    public boolean filterOut(final SAMRecord record) {
        return !accept(record);
    }

    @Override
    public String getRecordKey() {
        return "record";
    }
}
