/*
 * Copyright (c) 2007-2010 by The Broad Institute, Inc. and the Massachusetts Institute of Technology.
 * All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), Version 2.1 which
 * is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT
 * OR OTHER DEFECTS, WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR
 * RESPECTIVE TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES OF
 * ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES, ECONOMIC
 * DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER THE BROAD OR MIT SHALL
 * BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */

package htsjdk.tribble.index;

import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.util.LittleEndianInputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p/>
 * An abstract implementation of the index class.  This class takes care of the basics that are common
 * to all of the current indexing classes; including the version information, common header properties,
 * and reading and writing the header to disk.
 *
 * @author jrobinso
 */
public abstract class AbstractIndex implements MutableIndex {

    public enum IndexType {
        LINEAR(1),
        INTERVAL_TREE(2);
        public final int fileHeaderTypeIdentifier;

        IndexType(int fileHeaderTypeIdentifier) {
            this.fileHeaderTypeIdentifier = fileHeaderTypeIdentifier;
        }
    }

    // todo -- up to version 4 and use ETag to detect out of date
    // todo -- inode number + size in bytes + modification time
    // todo -- remove MD5

    // the current version of the index
    public static final int VERSION = 3;
    public static final int MAGIC_NUMBER = 1480870228;   //  byte[]{'T', 'I', 'D', 'X'};


    private final static String NO_MD5 = "";
    private final static long NO_FILE_SIZE = -1L;
    private final static long NO_TS = -1L;

    protected int version;                    // Our version value
    protected File indexedFile = null;         // The file we've created this index for
    protected long indexedFileSize = NO_FILE_SIZE; // The size of the indexed file
    protected long indexedFileTS = NO_TS;      // The timestamp
    protected String indexedFileMD5 = NO_MD5;        // The MD5 value, generally not filled in (expensive to calc)
    protected int flags;

    public boolean hasFileSize() {
        return indexedFileSize != NO_FILE_SIZE;
    }

    public boolean hasTimestamp() {
        return indexedFileTS != NO_TS;
    }

    public boolean hasMD5() {
        return indexedFileMD5 != NO_MD5;
    }

    private LinkedHashMap<String, String> properties;

    /**
     * the map of our chromosome bins
     */
    protected LinkedHashMap<String, ChrIndex> chrIndices;

    /**
     * Any flags we're using
     */
    private static final int SEQUENCE_DICTIONARY_FLAG = 0x8000; // if we have a sequence dictionary in our header

    /**
     * @param obj
     * @return true if this and obj are 'effectively' equivalent data structures.
     */
    public boolean equalsIgnoreProperties(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AbstractIndex)) {
            System.err.printf("equals: %s not instance of AbstractIndex", obj);
            return false;
        }

        final AbstractIndex other = (AbstractIndex) obj;

        if (version != other.version) {
            System.err.printf("equals version: this %d != other %d%n", version, other.version);
            return false;
        }

        if (indexedFile != other.indexedFile && (indexedFile == null || !indexedFile.equals(other.indexedFile))) {
            System.err.printf("equals indexedFile: this %s != other %s%n", indexedFile, other.indexedFile);
            return false;
        }

        if (indexedFileSize != other.indexedFileSize) {
            System.err.printf("equals indexedFileSize: this %d != other %d%n", indexedFileSize, other.indexedFileSize);
            return false;
        }

        if (!indexedFileMD5.equals(other.indexedFileMD5)) {
            System.err.printf("equals indexedFileMD5: this %s != other %s%n", indexedFileMD5, other.indexedFileMD5);
            return false;
        }

        if (flags != other.flags) {
            System.err.printf("equals flags: this %d != other %d%n", flags, other.flags);
            return false;
        }

        if (!chrIndices.equals(other.chrIndices)) {
            System.err.printf("equals chrIndeces: this %s != other %s%n", chrIndices, other.chrIndices);
            return false;
        }

        return true;
    }

    /**
     * create an abstract index, with defaults for the version value, and empty properties and chromosome lists
     */
    public AbstractIndex() {
        this.version = VERSION; // <= is overriden when file is read
        this.properties = new LinkedHashMap<String, String>();
        chrIndices = new LinkedHashMap<String, ChrIndex>();
    }

    /**
     * create an index file from the target feature file
     *
     * @param featureFile the feature file to create an index from
     */
    public AbstractIndex(final String featureFile) {
        this(new File(featureFile));
    }

    public AbstractIndex(final File featureFile) {
        this();
        this.indexedFile = featureFile;
    }

    public AbstractIndex(final AbstractIndex parent) {
        this();
        this.version = parent.version;
        this.indexedFile = parent.indexedFile;
        this.indexedFileSize = parent.indexedFileSize;
        this.indexedFileTS = parent.indexedFileTS;
        this.indexedFileMD5 = parent.indexedFileMD5;
        this.flags = parent.flags;
        this.properties = (LinkedHashMap<String, String>) parent.properties.clone();
    }

    protected void validateIndexHeader(final int indexType, final LittleEndianInputStream dis) throws IOException {
        final int magicNumber = dis.readInt();
        if (magicNumber != MAGIC_NUMBER) {
            throw new TribbleException(String.format("Unexpected magic number %d", magicNumber));
        }
        final int type = dis.readInt();
        if (type != indexType) {
            throw new TribbleException(String.format("Unexpected index type %d", type));
        }

    }

    /**
     * check the current version against the version we read in
     *
     * @return true if we're up to date, false otherwise
     */
    public boolean isCurrentVersion() {
        return version == VERSION;
    }

    public File getIndexedFile() {
        return indexedFile;
    }

    public long getIndexedFileSize() {
        return indexedFileSize;
    }

    public long getIndexedFileTS() {
        return indexedFileTS;
    }

    public String getIndexedFileMD5() {
        return indexedFileMD5;
    }

    public int getFlags() {
        return flags;
    }

    public int getVersion() {
        return version;
    }

    public void setMD5(final String md5) {
        this.indexedFileMD5 = md5;
    }

    public boolean containsChromosome(final String chr) {
        return chrIndices.containsKey(chr);
    }

    public void finalizeIndex() {
        // these two functions must be called now because the file may be being written during on the fly indexing
        if (indexedFile != null) {
            this.indexedFileSize = indexedFile.length();
            this.indexedFileTS = indexedFile.lastModified();
        }
    }

    /**
     * write the header to the target output stream
     *
     * @param dos the little endian output stream
     * @throws IOException an exception when we can't write to the file
     */
    private void writeHeader(final LittleEndianOutputStream dos) throws IOException {
        dos.writeInt(MAGIC_NUMBER);
        dos.writeInt(getType());
        dos.writeInt(version);
        dos.writeString(indexedFile.getAbsolutePath());
        dos.writeLong(indexedFileSize);
        dos.writeLong(indexedFileTS);
        dos.writeString(indexedFileMD5);
        dos.writeInt(flags);

        // Properties (Version 3 and later)
        dos.writeInt(properties.size());
        for (final Map.Entry<String, String> prop : properties.entrySet()) {
            dos.writeString(prop.getKey());
            dos.writeString(prop.getValue());
        }
    }

    /**
     * read the header from the input stream
     *
     * @param dis the little endian input stream
     * @throws IOException if we fail to read from the file at any point
     */
    private void readHeader(final LittleEndianInputStream dis) throws IOException {

        version = dis.readInt();
        indexedFile = new File(dis.readString());
        indexedFileSize = dis.readLong();
        indexedFileTS = dis.readLong();
        indexedFileMD5 = dis.readString();
        flags = dis.readInt();
        if (version < 3 && (flags & SEQUENCE_DICTIONARY_FLAG) == SEQUENCE_DICTIONARY_FLAG) {
            readSequenceDictionary(dis);
        }

        if (version >= 3) {
            int nProperties = dis.readInt();
            while (nProperties-- > 0) {
                final String key = dis.readString();
                final String value = dis.readString();
                properties.put(key, value);
            }
        }
    }

    /**
     * Kept to maintain backward compatibility with pre version 3 indexes.  The sequence dictionary is no longer
     * used,  use getSequenceNames() instead.
     *
     * @param dis
     * @throws IOException
     */
    private void readSequenceDictionary(final LittleEndianInputStream dis) throws IOException {
        final int size = dis.readInt();
        if (size < 0) throw new IllegalStateException("Size of the sequence dictionary entries is negative");
        for (int x = 0; x < size; x++) {
            dis.readString();
            dis.readInt();
        }
    }

    public List<String> getSequenceNames() {
        return new ArrayList<String>(chrIndices.keySet());
    }

    public List<Block> getBlocks(final String chr, final int start, final int end) {
        return getChrIndex(chr).getBlocks(start, end);
    }

    public List<Block> getBlocks(final String chr) {
        return getChrIndex(chr).getBlocks();
    }

    /**
     * @param chr
     * @return return the ChrIndex associated with chr,
     * @throws IllegalArgumentException if {@code chr} not found
     */
    private final ChrIndex getChrIndex(final String chr) {
        final ChrIndex chrIdx = chrIndices.get(chr);
        if (chrIdx == null) {
            throw new IllegalArgumentException("getBlocks() called with of unknown contig " + chr);
        } else {
            return chrIdx;
        }
    }

    public void write(final LittleEndianOutputStream stream) throws IOException {
        writeHeader(stream);

        //# of chromosomes
        stream.writeInt(chrIndices.size());
        for (final ChrIndex chrIdx : chrIndices.values()) {
            chrIdx.write(stream);
        }
    }

    @Override
    public void writeBasedOnFeatureFile(final File featureFile) throws IOException {
        if (!featureFile.isFile()) return;
        final LittleEndianOutputStream idxStream =
                new LittleEndianOutputStream(new BufferedOutputStream(new FileOutputStream(Tribble.indexFile(featureFile))));
        write(idxStream);
        idxStream.close();

    }

    public void read(final LittleEndianInputStream dis) throws IOException {
        try {
            readHeader(dis);

            int nChromosomes = dis.readInt();
            chrIndices = new LinkedHashMap<String, ChrIndex>(nChromosomes);

            while (nChromosomes-- > 0) {
                final ChrIndex chrIdx = (ChrIndex) getChrIndexClass().newInstance();
                chrIdx.read(dis);
                chrIndices.put(chrIdx.getName(), chrIdx);
            }

        } catch (final InstantiationException e) {
            throw new TribbleException.UnableToCreateCorrectIndexType("Unable to create class " + getChrIndexClass(), e);
        } catch (final IllegalAccessException e) {
            throw new TribbleException.UnableToCreateCorrectIndexType("Unable to create class " + getChrIndexClass(), e);
        } finally {
            dis.close();
        }

        //printIndexInfo();
    }

    protected void printIndexInfo() {
        System.out.println(String.format("Index for %s with %d indices", indexedFile, chrIndices.size()));
        final BlockStats stats = getBlockStats(true);
        System.out.println(String.format("  total blocks %d", stats.total));
        System.out.println(String.format("  total empty blocks %d", stats.empty));
    }

    protected static class BlockStats {
        long total = 0, empty = 0, objects = 0, size = 0;
    }

    protected BlockStats getBlockStats(final boolean logDetails) {
        final BlockStats stats = new BlockStats();
        for (final Map.Entry<String, ChrIndex> elt : chrIndices.entrySet()) {
            final List<Block> blocks = elt.getValue().getBlocks();

            if (blocks != null) {
                final int nBlocks = blocks.size();

                int nEmptyBlocks = 0;
                for (final Block b : elt.getValue().getBlocks()) {
                    if (b.getSize() == 0) nEmptyBlocks++;
                }
                stats.empty += nEmptyBlocks;
                stats.total += nBlocks;

                if (logDetails)
                    System.out.println(String.format("  %s => %d blocks, %d empty, %.2f", elt.getKey(), nBlocks, nEmptyBlocks, (100.0 * nEmptyBlocks) / nBlocks));
            }
        }

        return stats;
    }

    protected String statsSummary() {
        final BlockStats stats = getBlockStats(false);
        return String.format("%12d blocks (%12d empty (%.2f%%))", stats.total, stats.empty, (100.0 * stats.empty) / stats.total);
    }

    public void addProperty(final String key, final String value) {
        properties.put(key, value);
    }

    public void addProperties(final Map<String, String> properties) {
        this.properties.putAll(properties);
    }

    /**
     * return a mapping of name to property value
     *
     * @return the mapping of values as an unmodifiable map
     */
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * get the index type
     *
     * @return The index type
     */
    protected abstract int getType();

    /**
     * returns the class for the index type
     *
     * @return a Class, from which a new instance can be created
     */
    public abstract Class getChrIndexClass();
}
