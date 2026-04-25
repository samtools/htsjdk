package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMTag;

/**
 * Caches the per-tag-ID metadata that is invariant across all records in a slice.
 *
 * <p>In CRAM, each tag is identified by a 3-byte ID (2 bytes tag name + 1 byte type) packed
 * into an int. The tag ID dictionary in the compression header defines the small set of
 * unique tag IDs used in a slice (typically 5-20). This class pre-computes and caches
 * the derived String keys, binary tag codes, and type characters so they can be reused
 * across millions of records without repeated allocation.</p>
 *
 * <p>Internally uses parallel arrays with linear scan lookup, which is optimal for the
 * small number of entries typical in CRAM slices (fits in 1-2 cache lines).</p>
 */
public final class TagKeyCache {

    /** Pre-computed metadata for a single tag ID. */
    public static final class TagKeyInfo {
        /** Two-character tag name, e.g. "NM", "MD", "RG". */
        public final String key;
        /** Three-character tag name + type, e.g. "NMi", "MDZ". */
        public final String keyType3Bytes;
        /** The 3-byte tag ID packed as an int (name high bytes, type low byte). */
        public final int keyType3BytesAsInt;
        /** Binary tag code as computed by {@link SAMTag#makeBinaryTag}. */
        public final short code;
        /** The single-character type code, e.g. 'i', 'Z', 'A'. */
        public final char type;

        private TagKeyInfo(final int id) {
            final char c1 = (char) ((id >> 16) & 0xFF);
            final char c2 = (char) ((id >> 8) & 0xFF);
            this.type = (char) (id & 0xFF);
            this.key = new String(new char[]{c1, c2});
            this.keyType3Bytes = new String(new char[]{c1, c2, this.type});
            this.keyType3BytesAsInt = id;
            this.code = SAMTag.makeBinaryTag(this.key);
        }
    }

    private final int[] ids;
    private final TagKeyInfo[] infos;
    private final int size;

    /**
     * Creates a TagKeyCache from a tag ID dictionary.
     *
     * @param tagIDDictionary the tag ID dictionary from the compression header, where each
     *                        entry in the outer array is a combination of tag IDs (as 3-byte arrays)
     *                        that appear together on records
     */
    public TagKeyCache(final byte[][][] tagIDDictionary) {
        // Collect unique tag IDs across all dictionary entries
        // Use a simple approach: accumulate into oversized arrays, then we'll use them directly.
        // Worst case there are ~50 unique tags; typical is 5-20.
        int capacity = 0;
        for (final byte[][] entry : tagIDDictionary) {
            capacity += entry.length;
        }

        final int[] tempIds = new int[capacity];
        final TagKeyInfo[] tempInfos = new TagKeyInfo[capacity];
        int count = 0;

        for (final byte[][] entry : tagIDDictionary) {
            for (final byte[] tagBytes : entry) {
                final int id = ReadTag.name3BytesToInt(tagBytes);
                // Check if we already have this ID (linear scan is fine for small N)
                boolean found = false;
                for (int i = 0; i < count; i++) {
                    if (tempIds[i] == id) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    tempIds[count] = id;
                    tempInfos[count] = new TagKeyInfo(id);
                    count++;
                }
            }
        }

        this.ids = tempIds;
        this.infos = tempInfos;
        this.size = count;
    }

    /**
     * Looks up the cached metadata for the given 3-byte tag ID.
     *
     * @param id the tag ID as a packed int (2 bytes name + 1 byte type)
     * @return the cached metadata, or {@code null} if the ID is not in the cache
     */
    public TagKeyInfo get(final int id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                return infos[i];
            }
        }
        return null;
    }
}
