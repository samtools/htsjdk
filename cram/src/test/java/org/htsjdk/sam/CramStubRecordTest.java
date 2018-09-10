package org.htsjdk.sam;

import org.htsjdk.test.HtsjdkBaseTest;
import org.htsjdk.cram.CramStubRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CramStubRecordTest extends HtsjdkBaseTest {

    @Test
    public void testNothing(){
        final CramStubRecord stubRecord = new CramStubRecord();
        Assert.assertNotNull(stubRecord);
    }

}
