package htsjdk.samtools.cram.build.tags;

import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTagUtil;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Created by vadim on 21/02/2017.
 */
public class TagFilterTest {

    private SAMBinaryTagAndValue tv1, tv2;
    private SAMRecord record;

    @BeforeTest
    public void beforeTest() {
        System.out.println("beforetest");
        // prepare binary tags:
        tv1 = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().AM, 123);
        tv2 = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().AS, "qwe");
        tv1.insert(tv2);

        // prepare SAMRecord with similar tags for experiment purity:
        record = new SAMRecord(new SAMFileHeader());
        record.setAttribute(SAMTagUtil.getSingleton().makeStringTag(SAMTagUtil.getSingleton().AM), 123);
        record.setAttribute(SAMTagUtil.getSingleton().makeStringTag(SAMTagUtil.getSingleton().AS), "qwe");
    }

    @Test
    public void testNoTagsFilter() {
        // no tags are expected from this policy:
        TagFilter tagsPolicy = new TagFilter.NoTagsFilter();
        Assert.assertNull(tagsPolicy.filterTags(tv1));
        Assert.assertNull(tagsPolicy.filterTags(record));
    }

    @Test
    public void testAllTagsFilter_no_copy() {
        // this policy must preserve everything:
        TagFilter tagsPolicy = new TagFilter.AllTagsFilter(false);
        // for binary tags the policy with copy=false should return the same tags:
        Assert.assertSame(tagsPolicy.filterTags(tv1), tv1);
        // for record tags only equality:
        Assert.assertEquals(tagsPolicy.filterTags(record), tv1);
    }

    @Test
    public void testAllTagsFilter_copy() {
        // all preserving policy that doesn't copy binary tags:
        TagFilter tagsPolicy = new TagFilter.AllTagsFilter(true);
        Assert.assertEquals(tagsPolicy.filterTags(tv1), tv1);
        Assert.assertEquals(tagsPolicy.filterTags(record), tv1);
    }

    @Test
    public void testInclusiveTagsPolicy() {
        // include only listed tags:
        TagFilter.InclusiveTagsPolicy inclusiveTagsPolicy = new TagFilter.InclusiveTagsPolicy();
        // so far no tags listed, so no tags transferred:
        Assert.assertNull(inclusiveTagsPolicy.filterTags(tv1));
        Assert.assertNull(inclusiveTagsPolicy.filterTags(record));

        // add one tag to the policy that must be transferred:
        inclusiveTagsPolicy.bamTagCodes.add(tv2.tag);
        SAMBinaryTagAndValue filteredTags = inclusiveTagsPolicy.filterTags(tv1);
        Assert.assertNotNull(filteredTags);
        Assert.assertEquals(filteredTags.tag, tv2.tag);
        // check there is no other tags:
        Assert.assertNull(filteredTags.getNext());

        // same for tags supplied in a SAMRecord:
        SAMBinaryTagAndValue filteredTagsFromRecord = inclusiveTagsPolicy.filterTags(record);
        Assert.assertNotNull(filteredTagsFromRecord);
        Assert.assertEquals(filteredTagsFromRecord.tag, tv2.tag);
        // check there is no other tags:
        Assert.assertNull(filteredTagsFromRecord.getNext());
    }

    @Test
    public void testExclusiveFilter() {
        // exclude any tags listed in the policy, but first test that empty policy transfers everything:
        TagFilter.ExclusiveTagsFilter exclusiveTagsFilter = new TagFilter.ExclusiveTagsFilter();
        Assert.assertEquals(exclusiveTagsFilter.filterTags(tv1), tv1);
        Assert.assertEquals(exclusiveTagsFilter.filterTags(record), tv1);

        // now add one tag to exclude:
        exclusiveTagsFilter.bamTagCodes.add(tv2.tag);
        // check that the other tag is present for binary tags:
        SAMBinaryTagAndValue filteredTags = exclusiveTagsFilter.filterTags(tv1);
        Assert.assertNotNull(filteredTags);
        Assert.assertEquals(filteredTags.tag, tv1.tag);
        Assert.assertNull(filteredTags.getNext());

        // check that the other tag is present for record tags:
        SAMBinaryTagAndValue filteredTagsFromRecord = exclusiveTagsFilter.filterTags(record);
        Assert.assertNotNull(filteredTagsFromRecord);
        Assert.assertEquals(filteredTagsFromRecord.tag, tv1.tag);
        Assert.assertNull(filteredTagsFromRecord.getNext());
    }
}
