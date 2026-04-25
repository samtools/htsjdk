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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.RuntimeScriptException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link JavascriptSamRecordFilter}. Tests are written as many small, focused
 * cases against inline String scripts so the script body is visible next to the assertion.
 *
 * <p>The base contract being tested ({@link AbstractJavascriptFilter#accept}):
 * <ul>
 *   <li>script returns {@code null} or {@code undefined} -> reject</li>
 *   <li>script returns a {@code Boolean} -> accept iff true</li>
 *   <li>script returns a {@code Number} -> accept iff intValue() == 1</li>
 *   <li>script returns anything else -> reject</li>
 * </ul>
 * (here "accept" / "reject" describe what {@code accept()} returns; {@code filterOut()} negates).
 */
public class JavascriptSamRecordFilterTest extends HtsjdkTest {

    // --- helpers ----------------------------------------------------------------------------------

    /** A minimal header with one sequence "chr1" of length 1000. */
    private static SAMFileHeader header() {
        SAMFileHeader h = new SAMFileHeader();
        h.addSequence(new SAMSequenceRecord("chr1", 1000));
        return h;
    }

    /** Build a mapped fragment record on chr1 at the given start with the given read name and bases. */
    private static SAMRecord record(SAMFileHeader h, String name, int start, String bases) {
        SAMRecord r = new SAMRecord(h);
        r.setReadName(name);
        r.setReferenceName("chr1");
        r.setAlignmentStart(start);
        r.setReadString(bases);
        r.setBaseQualityString("I".repeat(bases.length()));
        r.setMappingQuality(60);
        r.setCigarString(bases.length() + "M");
        r.setReadUnmappedFlag(false);
        return r;
    }

    /** True iff the given script accepts the given record (i.e. filterOut returns false). */
    private static boolean accepts(String script, SAMFileHeader h, SAMRecord r) {
        return !new JavascriptSamRecordFilter(script, h).filterOut(r);
    }

    // --- constructors -----------------------------------------------------------------------------

    @Test
    public void stringConstructor_compilesAndRuns() {
        Assert.assertTrue(accepts("true;", header(), record(header(), "r", 100, "AAAA")));
    }

    @Test
    public void readerConstructor_compilesAndRuns() {
        SAMFileHeader h = header();
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter(new StringReader("true;"), h);
        Assert.assertFalse(f.filterOut(record(h, "r", 100, "AAAA")));
    }

    @Test
    public void fileConstructor_compilesAndRuns() throws IOException {
        File scriptFile = File.createTempFile("filter", ".js");
        scriptFile.deleteOnExit();
        Files.writeString(scriptFile.toPath(), "true;", StandardCharsets.UTF_8);
        SAMFileHeader h = header();
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter(scriptFile, h);
        Assert.assertFalse(f.filterOut(record(h, "r", 100, "AAAA")));
    }

    // --- return-type semantics --------------------------------------------------------------------

    @Test
    public void scriptReturnsTrue_isAccepted() {
        Assert.assertTrue(accepts("true;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsFalse_isRejected() {
        Assert.assertFalse(accepts("false;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsOne_isAccepted() {
        Assert.assertTrue(accepts("1;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsZero_isRejected() {
        Assert.assertFalse(accepts("0;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsTwo_isRejected() {
        // accept() requires intValue() == 1, not "any truthy number".
        Assert.assertFalse(accepts("2;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsNegativeOne_isRejected() {
        Assert.assertFalse(accepts("-1;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsOnePointZero_isAccepted() {
        // 1.0 has intValue() == 1, so this should be accepted under the documented contract.
        Assert.assertTrue(accepts("1.0;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsNull_isRejected() {
        Assert.assertFalse(accepts("null;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsUndefined_isRejected() {
        Assert.assertFalse(accepts("undefined;", header(), record(header(), "r", 100, "A")));
    }

    @Test
    public void scriptReturnsString_isRejected() {
        // Non-null, non-Boolean, non-Number falls into the "anything else" branch -> reject.
        Assert.assertFalse(accepts("'hello';", header(), record(header(), "r", 100, "A")));
    }

    // --- bindings: record key ---------------------------------------------------------------------

    @Test
    public void scriptCanCallMethodsOnRecord() {
        SAMFileHeader h = header();
        Assert.assertTrue(accepts("record.getReadName() == 'target';", h, record(h, "target", 100, "A")));
        Assert.assertFalse(accepts("record.getReadName() == 'target';", h, record(h, "other", 100, "A")));
    }

    @Test
    public void scriptCanReadAlignmentStart() {
        SAMFileHeader h = header();
        Assert.assertTrue(accepts("record.getAlignmentStart() == 250;", h, record(h, "r", 250, "A")));
        Assert.assertFalse(accepts("record.getAlignmentStart() == 250;", h, record(h, "r", 100, "A")));
    }

    @Test
    public void scriptCanReadReadString() {
        SAMFileHeader h = header();
        SAMRecord r = record(h, "r", 100, "ACGT");
        Assert.assertTrue(accepts("record.getReadString() == 'ACGT';", h, r));
    }

    // --- bindings: header key ---------------------------------------------------------------------

    @Test
    public void scriptCanReachHeader() {
        SAMFileHeader h = header();
        // header is bound under "header"; ensure it's reachable and methods work.
        Assert.assertTrue(accepts("header.getSequenceDictionary().size() == 1;", h, record(h, "r", 100, "A")));
    }

    @Test
    public void scriptCanReadSequenceFromHeader() {
        SAMFileHeader h = header();
        Assert.assertTrue(accepts(
                "header.getSequenceDictionary().getSequence(0).getSequenceName() == 'chr1';",
                h,
                record(h, "r", 100, "A")));
    }

    // --- subclass record-key override -------------------------------------------------------------

    @Test
    public void subclassCanOverrideRecordKey() {
        SAMFileHeader h = header();
        // Subclass that exposes the record under "rec" instead of "record".
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("rec.getReadName() == 'r';", h) {
            @Override
            public String getRecordKey() {
                return "rec";
            }
        };
        Assert.assertFalse(f.filterOut(record(h, "r", 100, "A")));
        Assert.assertTrue(f.filterOut(record(h, "other", 100, "A")));
    }

    // --- error paths ------------------------------------------------------------------------------

    @Test(expectedExceptions = RuntimeScriptException.class)
    public void malformedScript_throwsAtConstruction() {
        new JavascriptSamRecordFilter("this is not javascript ;)", header());
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void scriptThatThrowsAtEval_propagatesAsRuntimeException() {
        SAMFileHeader h = header();
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("throw 'boom';", h);
        f.filterOut(record(h, "r", 100, "A"));
    }

    // --- filterOut(first, second) AND-semantics ---------------------------------------------------

    @Test
    public void filterOutPair_bothRejected_isFilteredOut() {
        SAMFileHeader h = header();
        // "rejected" means accept() returns false, i.e. filterOut() returns true.
        // Script rejects everything -> both reject -> AND -> true.
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("false;", h);
        Assert.assertTrue(f.filterOut(record(h, "a", 100, "A"), record(h, "b", 200, "A")));
    }

    @Test
    public void filterOutPair_oneAccepted_isKept() {
        // "kept" means filterOut returns false, i.e. accept-or branch wins.
        SAMFileHeader h = header();
        // Script accepts only reads named "a"; pair has one of each -> AND of (false, true) -> false -> kept.
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("record.getReadName() == 'a';", h);
        Assert.assertFalse(f.filterOut(record(h, "a", 100, "A"), record(h, "b", 200, "A")));
    }

    @Test
    public void filterOutPair_bothAccepted_isKept() {
        SAMFileHeader h = header();
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("true;", h);
        Assert.assertFalse(f.filterOut(record(h, "a", 100, "A"), record(h, "b", 200, "A")));
    }

    // --- multi-record reuse -----------------------------------------------------------------------

    // --- error message for the no-engine path ----------------------------------------------------

    @Test
    public void noJsEngineMessage_containsActionableInfo() {
        // The "no engine on classpath" error is the primary UX for downstream consumers who
        // forget the runtime dep, so verify it stays actionable.
        String msg = AbstractJavascriptFilter.noJsEngineMessage("JavascriptSamRecordFilter");
        Assert.assertTrue(msg.contains("JavascriptSamRecordFilter"), "message should name the filter class");
        Assert.assertTrue(msg.contains("nashorn-core"), "message should name the recommended artifact");
        Assert.assertTrue(msg.contains("Gradle"), "message should show how to add it via Gradle");
        Assert.assertTrue(msg.contains("Maven"), "message should show how to add it via Maven");
    }

    @Test
    public void filterRefreshesBindingsAcrossManyRecords() {
        SAMFileHeader h = header();
        JavascriptSamRecordFilter f = new JavascriptSamRecordFilter("record.getAlignmentStart() >= 200;", h);

        List<SAMRecord> records = new ArrayList<>();
        for (int i = 1; i <= 4; i++) records.add(record(h, "r" + i, i * 100, "A"));

        // Expected: r1@100 -> reject, r2@200/r3@300/r4@400 -> accept.
        int kept = 0;
        for (SAMRecord r : records) if (!f.filterOut(r)) kept++;
        Assert.assertEquals(kept, 3);
    }
}
