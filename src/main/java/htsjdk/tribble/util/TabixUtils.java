/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.tribble.util;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * classes that have anything to do with tabix
 */
public class TabixUtils {

    /**
     * @deprecated Use since June 2019 {@link FileExtensions#TABIX_INDEX} instead.
     */
    @Deprecated
    public static final String STANDARD_INDEX_EXTENSION = FileExtensions.TABIX_INDEX;

    /**
     * Finds the index of a block-compressed, tabix-indexed file, looking for a CSI index and then a TBI index
     * beside it, in the order htslib does.
     *
     * @param resourcePath path or URL of the indexed file
     * @return the index's path or URL, or null if neither exists
     */
    public static String findIndex(final String resourcePath) throws IOException {
        for (final TabixIndexType indexType : new TabixIndexType[] {TabixIndexType.CSI, TabixIndexType.TBI}) {
            final String indexPath = ParsingUtils.appendToPath(resourcePath, indexType.getExtension());
            if (ParsingUtils.resourceExists(indexPath)) {
                return indexPath;
            }
        }
        return null;
    }

    /**
     * Generates the SAMSequenceDictionary from the given tabix index file, TBI or CSI. Sequence lengths are not
     * recorded in the index and are given as the lengths of the names.
     *
     * @param tabixIndex the path to the tabix index file
     * @return non-null sequence dictionary
     */
    public static SAMSequenceDictionary getSequenceDictionary(final Path tabixIndex) {
        if (tabixIndex == null) throw new IllegalArgumentException();
        try {
            final List<SAMSequenceRecord> sequences = new ArrayList<>();
            for (final String name : new TabixIndex(tabixIndex).getSequenceNames()) {
                sequences.add(new SAMSequenceRecord(name, name.length()));
            }
            return new SAMSequenceDictionary(sequences);
        } catch (final Exception e) {
            throw new TribbleException("Unable to read tabix index: " + e.getMessage(), e);
        }
    }
}
