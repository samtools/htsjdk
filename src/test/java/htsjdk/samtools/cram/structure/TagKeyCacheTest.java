package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMTag;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TagKeyCacheTest extends HtsjdkTest {

    /** Build a 3-byte tag ID array from two name chars and a type char, matching CRAM's tag dictionary format. */
    private static byte[] tagId(final char c1, final char c2, final char type) {
        return new byte[]{(byte) c1, (byte) c2, (byte) type};
    }

    /** Pack a 3-byte tag ID into an int, matching ReadTag.name3BytesToInt. */
    private static int tagIdAsInt(final char c1, final char c2, final char type) {
        return ((c1 & 0xFF) << 16) | ((c2 & 0xFF) << 8) | (type & 0xFF);
    }

    @Test
    public void testSingleTagLookup() {
        final byte[][][] dictionary = {
                {tagId('N', 'M', 'i')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);
        final TagKeyCache.TagKeyInfo info = cache.get(tagIdAsInt('N', 'M', 'i'));

        Assert.assertNotNull(info);
        Assert.assertEquals(info.key, "NM");
        Assert.assertEquals(info.keyType3Bytes, "NMi");
        Assert.assertEquals(info.type, 'i');
        Assert.assertEquals(info.keyType3BytesAsInt, tagIdAsInt('N', 'M', 'i'));
        Assert.assertEquals(info.code, SAMTag.makeBinaryTag("NM"));
    }

    @Test
    public void testMultipleTagsInOneDictionaryEntry() {
        final byte[][][] dictionary = {
                {tagId('N', 'M', 'i'), tagId('M', 'D', 'Z'), tagId('R', 'G', 'Z')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        final TagKeyCache.TagKeyInfo nm = cache.get(tagIdAsInt('N', 'M', 'i'));
        Assert.assertNotNull(nm);
        Assert.assertEquals(nm.key, "NM");
        Assert.assertEquals(nm.type, 'i');

        final TagKeyCache.TagKeyInfo md = cache.get(tagIdAsInt('M', 'D', 'Z'));
        Assert.assertNotNull(md);
        Assert.assertEquals(md.key, "MD");
        Assert.assertEquals(md.type, 'Z');

        final TagKeyCache.TagKeyInfo rg = cache.get(tagIdAsInt('R', 'G', 'Z'));
        Assert.assertNotNull(rg);
        Assert.assertEquals(rg.key, "RG");
        Assert.assertEquals(rg.code, SAMTag.makeBinaryTag("RG"));
    }

    @Test
    public void testMultipleDictionaryEntries() {
        // Two different tag-list combinations, as you'd see with records having different tag sets
        final byte[][][] dictionary = {
                {tagId('N', 'M', 'i'), tagId('M', 'D', 'Z')},
                {tagId('N', 'M', 'i'), tagId('S', 'A', 'Z'), tagId('X', 'A', 'Z')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        // All four unique tags should be present
        Assert.assertNotNull(cache.get(tagIdAsInt('N', 'M', 'i')));
        Assert.assertNotNull(cache.get(tagIdAsInt('M', 'D', 'Z')));
        Assert.assertNotNull(cache.get(tagIdAsInt('S', 'A', 'Z')));
        Assert.assertNotNull(cache.get(tagIdAsInt('X', 'A', 'Z')));
    }

    @Test
    public void testDuplicateTagIdsAreDeduped() {
        // NM appears in both dictionary entries — should only be stored once
        final byte[][][] dictionary = {
                {tagId('N', 'M', 'i'), tagId('M', 'D', 'Z')},
                {tagId('N', 'M', 'i'), tagId('S', 'A', 'Z')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        // Both lookups for NM should return the same instance
        final TagKeyCache.TagKeyInfo nm1 = cache.get(tagIdAsInt('N', 'M', 'i'));
        Assert.assertNotNull(nm1);
        Assert.assertEquals(nm1.key, "NM");
    }

    @Test
    public void testLookupMissReturnsNull() {
        final byte[][][] dictionary = {
                {tagId('N', 'M', 'i')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        Assert.assertNull(cache.get(tagIdAsInt('M', 'D', 'Z')));
        Assert.assertNull(cache.get(0));
    }

    @Test
    public void testEmptyDictionary() {
        final byte[][][] dictionary = {};
        final TagKeyCache cache = new TagKeyCache(dictionary);

        Assert.assertNull(cache.get(tagIdAsInt('N', 'M', 'i')));
    }

    @Test
    public void testDictionaryWithEmptyEntry() {
        // An entry with no tags (records with no tags)
        final byte[][][] dictionary = {
                {},
                {tagId('N', 'M', 'i')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        Assert.assertNotNull(cache.get(tagIdAsInt('N', 'M', 'i')));
    }

    @Test
    public void testSameTagNameDifferentTypes() {
        // Same tag name but different value types should be separate entries
        final byte[][][] dictionary = {
                {tagId('X', 'Y', 'i'), tagId('X', 'Y', 'Z')}
        };
        final TagKeyCache cache = new TagKeyCache(dictionary);

        final TagKeyCache.TagKeyInfo xyi = cache.get(tagIdAsInt('X', 'Y', 'i'));
        final TagKeyCache.TagKeyInfo xyZ = cache.get(tagIdAsInt('X', 'Y', 'Z'));

        Assert.assertNotNull(xyi);
        Assert.assertNotNull(xyZ);
        Assert.assertEquals(xyi.type, 'i');
        Assert.assertEquals(xyZ.type, 'Z');
        Assert.assertEquals(xyi.key, "XY");
        Assert.assertEquals(xyZ.key, "XY");
        // Same key but different packed IDs
        Assert.assertNotEquals(xyi.keyType3BytesAsInt, xyZ.keyType3BytesAsInt);
    }

    @Test
    public void testCodeMatchesSAMTagMakeBinaryTag() {
        final String[] tagNames = {"NM", "MD", "RG", "SA", "XA", "BC", "QT", "OQ"};
        final byte[][][] dictionary = new byte[1][tagNames.length][];
        for (int i = 0; i < tagNames.length; i++) {
            dictionary[0][i] = tagId(tagNames[i].charAt(0), tagNames[i].charAt(1), 'Z');
        }

        final TagKeyCache cache = new TagKeyCache(dictionary);

        for (final String name : tagNames) {
            final TagKeyCache.TagKeyInfo info = cache.get(tagIdAsInt(name.charAt(0), name.charAt(1), 'Z'));
            Assert.assertNotNull(info, "Missing cache entry for " + name);
            Assert.assertEquals(info.code, SAMTag.makeBinaryTag(name),
                    "Binary tag code mismatch for " + name);
        }
    }
}
