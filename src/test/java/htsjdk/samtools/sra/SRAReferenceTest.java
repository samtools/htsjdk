package htsjdk.samtools.sra;

import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SRAReferenceTest extends AbstractSRATest {
    @DataProvider(name = "testReference")
    private Object[][] createDataForReference() {
        return new Object[][] {
                {"SRR2096940", "CM000681.1", 95001, 95050, "AGATGATTCAGTCTCACCAAGAACACTGAAAGTCACATGGCTACCAGCAT"},
        };
    }

    @Test(dataProvider = "testReference")
    public void testReference(String acc, String refContig, int refStart, int refStop, String refBases) {
        final ReferenceSequenceFile refSeqFile = new SRAIndexedSequenceFile(new SRAAccession(acc));
        final ReferenceSequence refSeq = refSeqFile.getSubsequenceAt(refContig, refStart, refStop);
        Assert.assertEquals(new String(refSeq.getBases()), refBases);
    }

    class TestReferenceMtData {
        String refContig;
        int refStart;
        int refStop;
        String refBases;

        TestReferenceMtData(String refContig, int refStart, int refStop, String refBases) {
            this.refContig = refContig;
            this.refStart = refStart;
            this.refStop = refStop;
            this.refBases = refBases;
        }

        @Override
        public String toString() {
            return refContig + ":" + refStart + "-" + refStop + " = " + refBases;
        }
    }

    @DataProvider(name = "testReferenceMt")
    private Object[][] createDataForReferenceMt() {
        return new Object[][] {
                {
                    "SRR353866", Arrays.asList(
                        new TestReferenceMtData("AAAB01001871.1", 1, 50, "TGACGCGCATGAATGGATTAACGAGATTCCCTCTGTCCCTATCTACTATC"),
                        new TestReferenceMtData("AAAB01001871.1", 901, 950, "ACCAAGCGTACGATTGTTCACCCTTTCAAGGGAACGTGAGCTGGGTTTAG"),
                        new TestReferenceMtData("AAAB01008987.1", 1, 50, "TTTTGGACGATGTTTTTGGTGAACAGAAAACGAGCTCAATCATCCAGAGC"),
                        new TestReferenceMtData("AAAB01008859.1", 1, 50, "CAAAACGATGCCACAGATCAGAAGTTAATTAACGCACATTCTCCACCCAC")
                    )
                },
        };
    }

    @Test(dataProvider = "testReferenceMt")
    public void testReferenceMt(String acc, List<TestReferenceMtData> parallelTests) throws Exception {
        final ReferenceSequenceFile refSeqFile = new SRAIndexedSequenceFile(new SRAAccession(acc));
        final long timeout = 1000L * 5; // just in case
        final List<Thread> threads = new ArrayList<Thread>(parallelTests.size());
        final Map<TestReferenceMtData, Exception> runErrors = Collections.synchronizedMap(new HashMap<TestReferenceMtData, Exception>());
        for (final TestReferenceMtData testData: parallelTests) {
            threads.add(new Thread() {
                @Override
                public void run() {
                    try {
                        final ReferenceSequence refSeq = refSeqFile.getSubsequenceAt(testData.refContig,
                                testData.refStart, testData.refStop);
                        Assert.assertEquals(new String(refSeq.getBases()), testData.refBases);
                    } catch (final Exception e) {
                        Assert.assertNull(runErrors.put(testData, e));
                    }
                }
            });
        }
        for (final Thread thread: threads) {
            thread.start();
        }
        for (final Thread thread: threads) {
            thread.join(timeout);
        }
        for (final Map.Entry<TestReferenceMtData, Exception> result: runErrors.entrySet()) {
            // Will fail only on the first, but a debugger will be able to see all the results.
            Assert.fail("failed: " + result.getKey(), result.getValue());
        }
    }
}
