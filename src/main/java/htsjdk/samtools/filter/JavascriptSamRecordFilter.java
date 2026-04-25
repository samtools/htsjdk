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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * JavaScript-based {@link SamRecordFilter}.
 *
 * <p>The user-supplied script is evaluated against each {@link SAMRecord} with the following
 * variables in scope:
 *
 * <ul>
 *   <li>{@code record} - the {@link SAMRecord} being evaluated</li>
 *   <li>{@code header} - the {@link SAMFileHeader} associated with the reader</li>
 * </ul>
 *
 * <p>Example: keep only records with mapping quality >= 30:
 * <pre>{@code
 *     new JavascriptSamRecordFilter("record.getMappingQuality() >= 30;", header)
 * }</pre>
 *
 * <p><b>Runtime requirement:</b> as of htsjdk 5.0.0, htsjdk does not ship a JavaScript engine as
 * a runtime dependency. To use this class, add a JSR-223-compatible JavaScript engine
 * (e.g. {@code org.openjdk.nashorn:nashorn-core}) to your runtime classpath. If no engine is
 * available, the constructor throws a {@link htsjdk.samtools.util.RuntimeScriptException} whose
 * message lists the dependency coordinates.
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
