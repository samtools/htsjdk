package htsjdk.samtools.cram.build.tags;

import htsjdk.samtools.SAMBinaryTagAndUnsignedArrayValue;
import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTagUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * An abstract class for filtering SAM tags and some handy implementations.
 * Minimally implementation of {@link TagFilter#accept(short)} is required.
 * Overriding of {@link TagFilter#filterTags(htsjdk.samtools.SAMRecord)} and {@link TagFilter#filterTags(htsjdk.samtools.SAMRecord)}
 * is intended for better performance.
 */
public abstract class TagFilter {
    /**
     * Return true if the tag with this short tag code should be passed by the filter.
     *
     * @param bamTagCode BAM tag code, see {@link SAMTagUtil#makeBinaryTag(String)}
     * @return allow the tag if true, prohibit otherwise
     */
    abstract boolean accept(short bamTagCode);

    /**
     * Given a linked list of {@link SAMBinaryTagAndValue} objects create a filtered linked list of copied tags.
     * The new tags are shallow copies of the original tags.
     *
     * @param tv the tags to be filtered
     * @return linked list of binary tags that passed the filter
     */
    public SAMBinaryTagAndValue filterTags(SAMBinaryTagAndValue tv) {
        SAMBinaryTagAndValue tmp = tv;
        SAMBinaryTagAndValue result = null;
        while (tmp != null) {
            if (accept(tmp.tag)) {
                SAMBinaryTagAndValue copy = cloneBinaryTagWithoutLinks(tmp);

                if (result == null) result = copy;
                else result = result.insert(copy);
            }
            tmp = tmp.getNext();
        }
        return result;
    }

    /**
     * For attributes in a {@link SAMRecord} create a filtered linked list of binary tags.
     *
     * @param samRecord a record with attributes to be copied
     * @return linked list of binary tags that passed the filter
     */
    public SAMBinaryTagAndValue filterTags(SAMRecord samRecord) {
        SAMBinaryTagAndValue result = null;
        for (SAMRecord.SAMTagAndValue attribute : samRecord.getAttributes()) {
            if (!accept(SAMTagUtil.getSingleton().makeBinaryTag(attribute.tag))) continue;

            SAMBinaryTagAndValue tv;
            if (samRecord.isUnsignedArrayAttribute(attribute.tag)) {
                tv = new SAMBinaryTagAndUnsignedArrayValue(SAMTagUtil.getSingleton().makeBinaryTag(attribute.tag), attribute.value);
            } else {
                tv = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag(attribute.tag), attribute.value);
            }

            if (result == null) {
                result = tv;
            } else {
                result = result.insert(tv);
            }
        }
        return result;
    }

    /**
     * Copy a single tag ignoring links. Similar to {@link SAMBinaryTagAndValue#copy()} but doesn't copy links.
     * The method is aware of unsigned arrays.
     *
     * @param tv a tag to be copied
     * @return a new binary tag without any links
     */
    private static SAMBinaryTagAndValue cloneBinaryTagWithoutLinks(SAMBinaryTagAndValue tv) {
        if (tv.isUnsignedArray()) {
            return new SAMBinaryTagAndUnsignedArrayValue(tv.tag, tv.value);
        } else {
            return new SAMBinaryTagAndValue(tv.tag, tv.value);
        }
    }

    /**
     * A filter that allows no tags.
     */
    public static class NoTagsFilter extends TagFilter {

        @Override
        public boolean accept(short bamTagCode) {
            return false;
        }

        /**
         * Overridden for performance
         *
         * @param tv tags to ignore
         * @return null
         */
        @Override
        public SAMBinaryTagAndValue filterTags(SAMBinaryTagAndValue tv) {
            return null;
        }

        /**
         * Overridden for performance
         *
         * @param samRecord a record that doesn't matter
         * @return null
         */
        @Override
        public SAMBinaryTagAndValue filterTags(SAMRecord samRecord) {
            return null;
        }
    }

    /**
     * A filter to allow all tags by either providing the same tags or a shallow copy.
     */
    public static class AllTagsFilter extends TagFilter {
        private boolean copy = false;

        public AllTagsFilter(boolean copy) {
            this.copy = copy;
        }

        @Override
        public boolean accept(short bamTagCode) {
            return true;
        }

        /**
         * Performance optimization: return either the same list or a shallow copy.
         *
         * @param tv the original tags
         * @return the original tags object or a shallow copy of it based on the {@link AllTagsFilter#copy} field.
         */
        @Override
        public SAMBinaryTagAndValue filterTags(SAMBinaryTagAndValue tv) {
            if (tv == null) return null;

            return copy ? tv.copy() : tv;
        }
    }

    /**
     * A filter to allow only tags mentioned in a set.
     */
    public static class InclusiveTagsPolicy extends TagFilter {
        public final Set<Short> bamTagCodes;

        public InclusiveTagsPolicy(Set<Short> bamTagCodes) {
            this.bamTagCodes = bamTagCodes;
        }

        public InclusiveTagsPolicy() {
            this.bamTagCodes = new HashSet<>();
        }

        @Override
        public boolean accept(short bamTagCode) {
            return bamTagCodes.contains(bamTagCode);
        }
    }

    /**
     * A filter to exclude tags mentioned in a set, all other tags are allowed.
     */
    public static class ExclusiveTagsFilter extends TagFilter {
        public final Set<Short> bamTagCodes;

        public ExclusiveTagsFilter() {
            this.bamTagCodes = new HashSet<>();
        }

        public ExclusiveTagsFilter(Set<Short> bamTagCodes) {
            this.bamTagCodes = bamTagCodes;
        }

        /**
         * Create a filter that exclude only one specific tag.
         *
         * @param tagToExclude a tag to exclude
         */
        public ExclusiveTagsFilter(short tagToExclude) {
            this();
            bamTagCodes.add(tagToExclude);
        }

        @Override
        public boolean accept(short bamTagCode) {
            return !bamTagCodes.contains(bamTagCode);
        }

        /**
         * Overridden for better performance: copy all tags and then remove the excluded ones.
         *
         * @param tv the tags to be filtered
         * @return
         */
        @Override
        public SAMBinaryTagAndValue filterTags(SAMBinaryTagAndValue tv) {
            if (tv == null) return null;
            SAMBinaryTagAndValue copy = tv.copy();
            for (short tagToRemove : bamTagCodes) {
                copy.remove(tagToRemove);
            }
            return copy;
        }
    }
}
