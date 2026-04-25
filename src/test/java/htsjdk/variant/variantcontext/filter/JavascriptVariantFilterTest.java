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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeScriptException;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link JavascriptVariantFilter}. Mirrors the structure of
 * {@code JavascriptSamRecordFilterTest}: short, focused tests written against inline String scripts
 * so the body is visible next to the assertion.
 *
 * <p>The base contract being tested ({@link htsjdk.samtools.filter.AbstractJavascriptFilter#accept}):
 * <ul>
 *   <li>script returns {@code null} or {@code undefined} -> reject</li>
 *   <li>script returns a {@code Boolean} -> accept iff true</li>
 *   <li>script returns a {@code Number} -> accept iff intValue() == 1</li>
 *   <li>script returns anything else -> reject</li>
 * </ul>
 */
public class JavascriptVariantFilterTest extends HtsjdkTest {

    // --- helpers ----------------------------------------------------------------------------------

    /** A minimal header with one contig "chr1" of length 1000 and no samples. */
    private static VCFHeader header() {
        Map<String, String> contigFields = new LinkedHashMap<>();
        contigFields.put("ID", "chr1");
        contigFields.put("length", "1000");
        Set<VCFHeaderLine> meta = new LinkedHashSet<>();
        meta.add(new VCFContigHeaderLine(contigFields, 0));
        return new VCFHeader(meta);
    }

    /** Build a SNP variant on chr1 at the given position with the given REF/ALT alleles. */
    private static VariantContext snv(String contig, int pos, String ref, String alt) {
        return new VariantContextBuilder()
                .source("test")
                .chr(contig)
                .start(pos)
                .stop(pos)
                .alleles(ref, alt)
                .make();
    }

    /** True iff the given script accepts the given variant. */
    private static boolean accepts(String script, VCFHeader h, VariantContext v) {
        return new JavascriptVariantFilter(script, h).test(v);
    }

    // --- constructors -----------------------------------------------------------------------------

    @Test
    public void stringConstructor_compilesAndRuns() {
        Assert.assertTrue(accepts("true;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void readerConstructor_compilesAndRuns() throws IOException {
        VCFHeader h = header();
        JavascriptVariantFilter f = new JavascriptVariantFilter(new StringReader("true;"), h);
        Assert.assertTrue(f.test(snv("chr1", 100, "A", "C")));
    }

    @Test
    public void fileConstructor_compilesAndRuns() throws IOException {
        File scriptFile = File.createTempFile("filter", ".js");
        scriptFile.deleteOnExit();
        Files.writeString(scriptFile.toPath(), "true;", StandardCharsets.UTF_8);
        VCFHeader h = header();
        JavascriptVariantFilter f = new JavascriptVariantFilter(scriptFile, h);
        Assert.assertTrue(f.test(snv("chr1", 100, "A", "C")));
    }

    // --- return-type semantics --------------------------------------------------------------------

    @Test
    public void scriptReturnsTrue_isAccepted() {
        Assert.assertTrue(accepts("true;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsFalse_isRejected() {
        Assert.assertFalse(accepts("false;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsOne_isAccepted() {
        Assert.assertTrue(accepts("1;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsZero_isRejected() {
        Assert.assertFalse(accepts("0;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsTwo_isRejected() {
        Assert.assertFalse(accepts("2;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsNegativeOne_isRejected() {
        Assert.assertFalse(accepts("-1;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsOnePointZero_isAccepted() {
        Assert.assertTrue(accepts("1.0;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsNull_isRejected() {
        Assert.assertFalse(accepts("null;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsUndefined_isRejected() {
        Assert.assertFalse(accepts("undefined;", header(), snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptReturnsString_isRejected() {
        Assert.assertFalse(accepts("'hello';", header(), snv("chr1", 100, "A", "C")));
    }

    // --- bindings: variant key --------------------------------------------------------------------

    @Test
    public void scriptCanCallMethodsOnVariant() {
        VCFHeader h = header();
        Assert.assertTrue(accepts("variant.getStart() == 100;", h, snv("chr1", 100, "A", "C")));
        Assert.assertFalse(accepts("variant.getStart() == 100;", h, snv("chr1", 250, "A", "C")));
    }

    @Test
    public void scriptCanReadContig() {
        VCFHeader h = header();
        Assert.assertTrue(accepts("variant.getContig() == 'chr1';", h, snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptCanReadAlleles() {
        VCFHeader h = header();
        Assert.assertTrue(accepts("variant.getReference().getBaseString() == 'A';", h, snv("chr1", 100, "A", "C")));
    }

    // --- bindings: header key ---------------------------------------------------------------------

    @Test
    public void scriptCanReachHeader() {
        VCFHeader h = header();
        Assert.assertTrue(accepts("header.getContigLines().size() == 1;", h, snv("chr1", 100, "A", "C")));
    }

    @Test
    public void scriptCanReadContigFromHeader() {
        VCFHeader h = header();
        Assert.assertTrue(accepts("header.getContigLines().get(0).getID() == 'chr1';", h, snv("chr1", 100, "A", "C")));
    }

    // --- error paths ------------------------------------------------------------------------------

    @Test(expectedExceptions = RuntimeScriptException.class)
    public void malformedScript_throwsAtConstruction() {
        new JavascriptVariantFilter("this is not javascript ;)", header());
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void scriptThatThrowsAtEval_propagatesAsRuntimeException() {
        VCFHeader h = header();
        JavascriptVariantFilter f = new JavascriptVariantFilter("throw 'boom';", h);
        f.test(snv("chr1", 100, "A", "C"));
    }

    // --- multi-record reuse -----------------------------------------------------------------------

    @Test
    public void filterRefreshesBindingsAcrossManyVariants() {
        VCFHeader h = header();
        JavascriptVariantFilter f = new JavascriptVariantFilter("variant.getStart() >= 200;", h);

        List<VariantContext> variants = new ArrayList<>();
        for (int i = 1; i <= 4; i++) variants.add(snv("chr1", i * 100, "A", "C"));

        int kept = 0;
        for (VariantContext v : variants) if (f.test(v)) kept++;
        Assert.assertEquals(kept, 3);
    }
}
