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
package htsjdk.variant.variantcontext.filter;

import htsjdk.samtools.filter.AbstractJavascriptFilter;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * JavaScript-based {@link VariantContextFilter}.
 *
 * <p>The user-supplied script is evaluated against each {@link VariantContext} with the following
 * variables in scope:
 *
 * <ul>
 *   <li>{@code variant} - the {@link VariantContext} being evaluated</li>
 *   <li>{@code header} - the {@link VCFHeader} associated with the reader</li>
 * </ul>
 *
 * <p>Example: keep only variants on chromosome 1:
 * <pre>{@code
 *     new JavascriptVariantFilter("variant.getContig() == '1';", header)
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
public class JavascriptVariantFilter extends AbstractJavascriptFilter<VCFHeader, VariantContext>
        implements VariantContextFilter {
    /**
     * constructor using a javascript File
     *
     * @param scriptFile
     *            the javascript file to be compiled
     * @param header
     *            the SAMHeader
     */
    public JavascriptVariantFilter(final File scriptFile, final VCFHeader header) throws IOException {
        super(scriptFile, header);
    }

    /**
     * constructor using a Reader
     *
     * @param scriptReader
     *            the reader for the script to be compiled. Will be closed
     * @param header
     *            the SAMHeader
     */
    public JavascriptVariantFilter(final Reader scriptReader, final VCFHeader header) throws IOException {
        super(scriptReader, header);
    }

    /**
     * constructor using a javascript expression
     *
     * @param scriptExpression
     *            the javascript expression to be compiled
     * @param header
     *            the SAMHeader
     */
    public JavascriptVariantFilter(final String scriptExpression, final VCFHeader header) {
        super(scriptExpression, header);
    }

    /**
     * Determines whether a VariantContext matches this filter
     *
     * @param record
     *            the VariantContext to evaluate
     * @return true if accept(record) returned true
     */
    @Override
    public boolean test(final VariantContext record) {
        return accept(record);
    }

    @Override
    public String getRecordKey() {
        return "variant";
    }
}
