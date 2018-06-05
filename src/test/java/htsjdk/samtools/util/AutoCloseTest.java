package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AutoCloseTest extends HtsjdkTest {

    @Test
    public void testClose() {

        class CanClose {
            private boolean wasClosed = false;
            public void close() {
                wasClosed = true;
            }
        }

        CanClose canClose = new CanClose();

        try (AutoClose<CanClose> auto = new AutoClose<>(canClose)) {
            CanClose innerCanClose = auto.object;
            assertFalse(innerCanClose.wasClosed);
        }

        assertTrue(canClose.wasClosed);
    }
}
