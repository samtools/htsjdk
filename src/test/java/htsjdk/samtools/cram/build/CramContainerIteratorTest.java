package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMTestUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.util.Iterables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CramContainerIteratorTest extends HtsjdkTest {

    @Test
    public void theContainersAfterAnInternalEofContainerAreReturned() throws IOException {
        try (CramContainerIterator iterator =
                new CramContainerIterator(new ByteArrayInputStream(CRAMTestUtils.cramWithAnInternalEofContainer()))) {
            final List<Container> containers = Iterables.slurp(iterator);
            Assert.assertEquals(containers.size(), 2);
            for (final Container container : containers) {
                Assert.assertFalse(container.isEOF());
            }
        }
    }

    @Test
    public void iterationEndsAfterTwoTrailingEofContainers() throws IOException {
        final byte[] cram = CRAMTestUtils.cramWithAnInternalEofContainer();
        final ByteArrayOutputStream twoEofs = new ByteArrayOutputStream();
        twoEofs.write(cram);
        try (CramContainerIterator iterator = new CramContainerIterator(new ByteArrayInputStream(cram))) {
            CramIO.writeCramEOF(iterator.getCramHeader().getCRAMVersion(), twoEofs);
        }
        try (CramContainerIterator iterator =
                new CramContainerIterator(new ByteArrayInputStream(twoEofs.toByteArray()))) {
            Assert.assertEquals(Iterables.slurp(iterator).size(), 2);
        }
    }
}
