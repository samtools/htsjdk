package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for managing a set of VCFHeaderLines maintained by a VCFHeader.
 *
 * Since this class is used to incrementally build up a set of header lines for use with a VCFHeader,
 * it does not require that the list always contain a fileformat line (its VCFHeader's job to enforce
 * that condition).
 *
 * This class maintains several invariants:
 *
 *  - The list keeps track of the "current version" by tracking whether a version line (a line that
 *    establishes the VCFHeaderVersion, such as format/fileformat line) is contained in the list. If
 *    no version line has been added, the list will have a null current version, and contain 0 version
 *    lines. If a version line has been added, it will have a non-null version, and contain 1 version line.
 *    If the version line is manually removed, the "current version" is reset to null.
 *
 *  - Each contig line that is retained is guaranteed to have a unique contig index. This does
 *    NOT guarantee that the contig indices are contiguous, or ordered, only that they are unique.
 *
 *  - Each structured (ID) line for a given key will have a unique ID. Any new line that has the same
 *    key/ID pair as an existing line will replace the previous line. (Previous htsjdk implementations
 *    preserve such lines in a master line list, but would silently drop them from the typed
 *    lookup lists, so such duplicates would never be returned in queries for typed lines such as
 *    getInfoHeaderLines(), but would still be serialized on write.)
 */
//Visible to allow disq Kryo registration for serialization
//TODO: annotate this class as InternalAPI when its rebased on master
final class VCFMetaDataLines implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFMetaDataLines.class);

    // Master map of all header lines (including file format version lines and contig header lines)
    private final Map<HeaderLineMapKey, VCFHeaderLine> mMetaData = new LinkedHashMap<>();

    // Map of contig index to contig header line. Must be kept in sync with the mMetaData map
    private final Map<Integer, VCFContigHeaderLine> contigIndexMap = new LinkedHashMap<>();

    // Current version for lines included in the list. May be null. Must be kept in sync with the mMetaData map
    private VCFHeaderVersion vcfVersion;

    /**
     * Add all metadata lines from Set. If a duplicate line is encountered (duplicate content for
     * unstructured lines with identical keys, or duplicate key/ID pair for structured lines), only
     * the new line will be retained.
     *
     * @param newMetaData Set of lines to be added to the list.
     * @throws IllegalArgumentException if a version is established or if any line fails validation for that version
     */
    public void addMetaDataLines(final Set<VCFHeaderLine> newMetaData) {
        newMetaData.forEach(this::addMetaDataLine);
    }

    /**
     * Add a metadata line to the list. If a duplicate line is encountered (duplicate content for
     * unstructured lines with identical keys, or duplicate key/ID pair for structured lines), only
     * the newest line will be retained.
     *
     * @param newMetaDataLine header line to attempt to add
     * @returns an existing (equivalent) header line that was replaced by newMetaDataLine, if any, otherwise
     * null
     * @throws IllegalArgumentException if the line being added is a fileformat (VCF version) line,
     * and if any existing line fails to validate against the new version
     */
    public VCFHeaderLine addMetaDataLine(final VCFHeaderLine newMetaDataLine) {
        ValidationUtils.nonNull(newMetaDataLine, "metadata line");

        if (VCFHeaderVersion.isFormatString(newMetaDataLine.getKey())) {
            // in this case, its possible for equivalentMetaDataLine to have a different key than the
            // new line (since old VCF versions use a different key than modern versions), even though
            // its replacing the old line in the map, because we only allow one version line in the map
            // at a time
            return updateVersion(newMetaDataLine);
        } else {
            final HeaderLineMapKey newMapKey = makeKeyForLine(newMetaDataLine);
            final VCFHeaderLine equivalentMetaDataLine = mMetaData.get(newMapKey);
            if (equivalentMetaDataLine == null) {
                createNewMapEntry(newMapKey, newMetaDataLine);
            } else {
                replaceExistingMapEntry(newMapKey, equivalentMetaDataLine, newMetaDataLine);
            }
            return equivalentMetaDataLine;
        }
    }

    /**
     * Remove a metadata line from the list. This is the inverse of addMetaDataLine - it removes a
     * line that has an identical key and value as lineToRemove if lineToRemove is an unstructured (non-ID)
     * but if lineToRemove is a structured line, it will remove the line that has the same key/ID pair as
     * lineToRemove, regardless of other content.
     *
     * The removed value is returned, and can be used by the caller to determine if the removed line has a
     * different value than the line presented.
     *
     * @param lineToRemove the header line to remove
     * @return The actual headerline removed, or null of no equivalent headerline was found to remove
     */
    public VCFHeaderLine removeMetaDataLine(final VCFHeaderLine lineToRemove) {
        final VCFHeaderLine removedLine = mMetaData.remove(makeKeyForLine(lineToRemove));
        if (removedLine != null) {
            // only synchronize the dependent variables if a line was ACTUALLY removed
            if (VCFHeaderVersion.isFormatString(removedLine.getKey())) {
                vcfVersion = null;
            } else if (lineToRemove.isIDHeaderLine() && lineToRemove.getKey().equals(VCFHeader.CONTIG_KEY)) {
                removeFromContigIndexMap((VCFContigHeaderLine) lineToRemove);
            }
        }
        return removedLine;
    }

    /**
     * @return the version for any contained version line. may be null if no file format version
     * line is in the list
     */
    public VCFHeaderVersion getVCFVersion() {
        return vcfVersion;
    }

    /**
     * Return any existing line if the list already contains a header line with the same key (and id
     * for structured header lines). The line need not satisfy equality for the queryLine for ID lines,
     * only that it is of the same type/id.
     *
     * @param queryLine the source line to use to check for equivalents
     * @return The existing header line of the type/key provided, otherwise NULL.
     */
    public VCFHeaderLine hasEquivalentHeaderLine(final VCFHeaderLine queryLine) {
        return mMetaData.get(makeKeyForLine(queryLine));
    }

    /**
     * Validate all metadata lines except the file format line against a target version.
     * If this returns true, the these lines can be upgraded to targetVersion. If false, the metadata lines
     * must be manually updated.
     * @param targetVersion the target version to validate against
     */
    //TODO: we need to tell users how to resolve the case where this fails due to version validation
    //i.e, use a custom upgrade tool
    public void validateMetaDataLines(final VCFHeaderVersion targetVersion) {
        mMetaData.values().forEach(headerLine -> headerLine.validateForVersion(targetVersion));
    }

    /**
     * get the meta data, associated with this header, in input order
     *
     * @return a set of the meta data
     */
    public Set<VCFHeaderLine> getMetaDataInInputOrder() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mMetaData.values()));
    }

    /**
     * get the meta data, associated with this header, in SORTED order
     *
     * @return a set of the meta data
     */
    public Set<VCFHeaderLine> getMetaDataInSortedOrder() {
        // Use an intermediate treeset to get the correct sorting (via the header line
        // comparators), but returned a LinkedHashSet because TreeSet has a `contains`
        // implementation based on comparator equality that can lead to inconsistent
        // results for header lines types like VCFContigHeaderLine that have a compareTo
        // implementation that is inconsistent with equals.
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(mMetaData.values())));
    }

    /**
     * @return all of the structured (ID) lines in their original file order, or an empty list if none were present
     */
    public List<VCFSimpleHeaderLine> getIDHeaderLines() {
        return mMetaData.values().stream()
                .filter(VCFHeaderLine::isIDHeaderLine)
                .map(hl -> (VCFSimpleHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return all of the VCF FILTER lines in their original file order, or an empty list if none were present
     */
    public List<VCFFilterHeaderLine> getFilterLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.FILTER_HEADER_KEY))
                .map(hl -> (VCFFilterHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return all of the VCF header lines of the ##contig form in SORTED order, or an empty list if none were present
     */
    public List<VCFContigHeaderLine> getContigLines() {
        return Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(contigIndexMap.values())));
    }

    /**
     * Get the VCFHeaderLine(s) whose key equals key.  Returns null if no such line exists
     * @param key the key to use to locate the headerline
     * @return collection of headerlines
     */
    public Collection<VCFHeaderLine> getMetaDataLines(final String key) {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(key)).collect(Collectors.toList());
    }

    /**
     * Returns the INFO HeaderLines in their original ordering
     */
    public Collection<VCFInfoHeaderLine> getInfoHeaderLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.INFO_HEADER_KEY))
                .map(hl -> (VCFInfoHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns the FORMAT HeaderLines in their original ordering
     */
    public Collection<VCFFormatHeaderLine> getFormatHeaderLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.FORMAT_HEADER_KEY))
                .map(hl -> (VCFFormatHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFInfoHeaderLine getInfoHeaderLine(final String id) {
        return (VCFInfoHeaderLine) mMetaData.get(makeKey(VCFConstants.INFO_HEADER_KEY, id));
    }

    /**
     * @param id  the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFormatHeaderLine getFormatHeaderLine(final String id) {
        return (VCFFormatHeaderLine) mMetaData.get(makeKey(VCFConstants.FORMAT_HEADER_KEY, id));
    }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String id) {
        return (VCFFilterHeaderLine) mMetaData.get(makeKey(VCFConstants.FILTER_HEADER_KEY, id));
    }

    /**
     * Returns the other VCFHeaderLines in their original ordering, where "other" means any
     * VCFHeaderLine that is not a contig, info, format or filter header line.
     */
    public Collection<VCFHeaderLine> getOtherHeaderLines() {
        return mMetaData.values().stream().filter(
            hl ->
                !hl.getKey().equals(VCFConstants.CONTIG_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.INFO_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.FILTER_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.FORMAT_HEADER_KEY)
        )
        .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * The version/fileformat header line if one exists, otherwise null.
     * @return The version/fileformat header line if one exists, otherwise null.
     */
    public VCFHeaderLine getFileFormatLine() {
        // find any existing version line(s). since there are multiple possible keys that
        // represent version lines (old V3 specs used "format" instead of "fileformat")
        final List<VCFHeaderLine> existingVersionLines = mMetaData.values()
                .stream()
                .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                .collect(Collectors.toList());

        // This class doesn't mandate that the list it maintains always contains a fileformat line
        // (its VCFHeader's job to maintain that condition for the header).
        if (!existingVersionLines.isEmpty()) {
            if (existingVersionLines.size() > 1) {
                throw new IllegalStateException(
                        String.format("The metadata lines class contains more than one version line (%s)",
                                existingVersionLines.stream()
                                        .map(VCFHeaderLine::toString)
                                        .collect(Collectors.joining(","))));
            }
            return existingVersionLines.get(0);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append("[VCFMetaDataLines:");
        for ( final VCFHeaderLine line : mMetaData.values() )
            b.append("\n\t").append(line);
        return b.append("\n]").toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VCFMetaDataLines)) return false;

        final VCFMetaDataLines that = (VCFMetaDataLines) o;

        return mMetaData.equals(that.mMetaData);
    }

    @Override
    public int hashCode() {
        return mMetaData.hashCode();
    }

    /**
     * Generate a unique key for a VCFHeaderLine. If the header line is a VCFStructuredHeaderLine, the key
     * is the concatenation of the VCFHeaderLine's key (i.e., the type of the VCFHeaderLine) and the ID for
     * that VCFHeaderLine (with a ":" separator). Otherwise, we use the concatenation of the OTHER_KEY, the
     * VCFHeaderLine's key, and a nonce value to ensure that unstructured lines never collide with structured
     * lines, and also can have duplicate identical instances.
     *
     * @param headerLine the {@link VCFHeaderLine} for which a key should be returned
     * @return the generated HeaderLineMapKey
     */
    private HeaderLineMapKey makeKeyForLine(final VCFHeaderLine headerLine) {
        if (headerLine.isIDHeaderLine()) {
            // these are required to have a unique ID, so use the line key as the key, and the id as the constraint
            return makeKey(headerLine.getKey(), headerLine.getID());
        } else {
            // Allow duplicate unstructured "other" keys, as long as they have different values. Use
            // the line key as the key, and the line hashcode as the constraint.
            //
            // The previous implementation dropped duplicate keys for unstructured lines, but the spec doesn't
            // require these to be unique (only to have unique values). This implementation is more permissive in
            // that it allows lines with duplicate keys to accumulate as long as they have different values, but
            // retains only one with a unique value.
            return makeKey(headerLine.getKey(), Integer.toString(headerLine.hashCode()));
        }
    }

    // Create a VCFHeaderLine hashmap key given a key and an id
    private HeaderLineMapKey makeKey(final String nameSpace, final String id) { return new HeaderLineMapKey(nameSpace, id); }

    private void createNewMapEntry(final HeaderLineMapKey newMapKey, final VCFHeaderLine newMetaDataLine) {
        final VCFHeaderLine existingLine = updateMapEntry(newMapKey, newMetaDataLine);
        if (existingLine != null ) {
            throw new TribbleException(String.format(
                    "Header synchronization error - found unexpected previous value %s while adding %s",
                    existingLine,
                    newMetaDataLine));
        }
    }

    private VCFHeaderLine updateMapEntry(final HeaderLineMapKey newMapKey, final VCFHeaderLine newMetaDataLine) {
        final VCFHeaderLine existingLine = mMetaData.put(newMapKey, newMetaDataLine);
        if (newMetaDataLine.isIDHeaderLine() && newMetaDataLine.getKey().equals(VCFHeader.CONTIG_KEY)) {
            addToContigIndexMap((VCFContigHeaderLine) newMetaDataLine);
        }
        return existingLine;
    }

    // We can't just blindly replace a line in the map based on the key using map.put, because the contig
    // map will get out of sync if the line being replaced is a contig line that has a different contig
    // index than the line being replaced. So replace the line in two atomic operations; first remove
    // the old line and it's corresponding contig index entry, then add the new contig line and it's
    // corresponding contig index entry.
    private VCFHeaderLine replaceExistingMapEntry(
            final HeaderLineMapKey newMapKey,
            final VCFHeaderLine existingMetaDataLine,
            final VCFHeaderLine newMetaDataLine) {
        removeMapEntryOrThrow(existingMetaDataLine);
        logger.warn(String.format("Replacing existing header line (%s) with new header line (%s).",
                existingMetaDataLine,
                newMetaDataLine));
        createNewMapEntry(newMapKey, newMetaDataLine);
        return existingMetaDataLine;
    }

    // remove a line that is expected to be  currently in the list, and throw if the line
    // isn't found, or if the removed line is different (not equal to) the line to remove
    private void removeMapEntryOrThrow(final VCFHeaderLine lineToRemove) {
        final VCFHeaderLine removedLine = removeMetaDataLine(lineToRemove);
        if (removedLine == null || !removedLine.equals(lineToRemove)) {
            // sanity check since in this case there should ALWAYS be a non-null line that was removed
            // that is an exact duplicate of the "existingLine"
            throw new TribbleException(String.format("Internal metadata line synchronization error %s/%s",
                    lineToRemove,
                    removedLine == null ? "null line" : removedLine));
        }
    }

    //add the new line to our contig index map
    private void addToContigIndexMap(final VCFContigHeaderLine newContigLine) {
        final VCFContigHeaderLine collidingContigLine = contigIndexMap.get(newContigLine.getContigIndex());
        if (collidingContigLine != null && !collidingContigLine.equals(newContigLine)) {
            if (collidingContigLine.getID().equals(newContigLine.getID())) {
                // the new line has the same contig ID and index as an existing line, but differ in
                // some other attribute, so accept it but log a warning
                logger.warn(String.format(
                        "Replacing an existing contig header line (%s) with a new, similar line that has different attributes (%s)",
                        collidingContigLine,
                        newContigLine));
            } else {
                // the new contig line collides with an existing contig index, but specifies a different
                // contig name, so reject it
                throw new TribbleException(String.format(
                        "Attempt to replace a contig header line (%s) that has the same contig index as an existing line (%s)",
                        newContigLine,
                        collidingContigLine));
            }
        }
        contigIndexMap.put(newContigLine.getContigIndex(), newContigLine);
    }

    // remove the contig header line from the contig index map
    private void removeFromContigIndexMap(final VCFContigHeaderLine existingContigLine) {
        // this remove overload only removes the specified object if its actually in the map
        contigIndexMap.remove(existingContigLine.getContigIndex(), existingContigLine);
    }

    private final VCFHeaderLine updateVersion(final VCFHeaderLine newMetaDataLine) {
        ValidationUtils.validateArg(
                VCFHeaderVersion.isFormatString(newMetaDataLine.getKey()),
                "must be a file format line");

        // First, check for header lines that establish a header version. Whenever one is added, we want
        // to remove any any previous header line that established a version, validate all existing lines
        // against the new
        // version, and and update. We have to explicitly call isFormatString, and manually update
        // the lines, since there is more than one header line key that can change the version. In some
        // cases this will result in removing a line fileformat/version line with one key and replacing
        // it with a line that has a different key. Delegate to setVCFVersion to handle that.
        final VCFHeaderLine currentVersionLine = getFileFormatLine();
        final VCFHeaderVersion newVCFVersion = VCFHeaderVersion.toHeaderVersion(newMetaDataLine.getValue());
        final VCFHeaderLine newVersionLine = VCFHeader.makeHeaderVersionLine(newVCFVersion);

        if (currentVersionLine != null) {
            logger.warn(String.format("Header version line %s is being replaced with version line %s",
                    currentVersionLine,
                    newVersionLine));
            removeMapEntryOrThrow(currentVersionLine);
        }

        mMetaData.put(makeKeyForLine(newVersionLine), newVersionLine);
        vcfVersion = newVCFVersion;
        return currentVersionLine;
    }

    // composite keys used by the metadata lines map
    private static class HeaderLineMapKey implements Serializable {
        public static final long serialVersionUID = 1L;

        final String key;
        final String constraint;

        public HeaderLineMapKey(final String key, final String constraint) {
            this.key = key;
            this.constraint = constraint;
        }

        public final String getKey() { return key; }
        public final String getConstraint() { return constraint; }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final HeaderLineMapKey that = (HeaderLineMapKey) o;

            if (!key.equals(that.key)) return false;
            return constraint.equals(that.constraint);
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + constraint.hashCode();
            return result;
        }
    }
}

