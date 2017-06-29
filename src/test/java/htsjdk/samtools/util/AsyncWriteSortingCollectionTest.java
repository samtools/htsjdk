/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools.util;

import htsjdk.samtools.Defaults;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class AsyncWriteSortingCollectionTest extends SortingCollectionTest {

    public static final int SORT_COL_THREADS = 2;
    private int sortColThreadsDefaults;

    @BeforeClass
    void setupClass() throws NoSuchFieldException, IllegalAccessException {
        sortColThreadsDefaults = Defaults.SORTING_COLLECTION_THREADS;
        changeDefaultsParam(Defaults.class, "SORTING_COLLECTION_THREADS", SORT_COL_THREADS);
    }

    @AfterClass
    void tearDownClass() throws NoSuchFieldException, IllegalAccessException {
        changeDefaultsParam(Defaults.class, "SORTING_COLLECTION_THREADS", sortColThreadsDefaults);
    }

    @DataProvider(name = "test1")
    public Object[][] createTestData() {
        return new Object[][] {
                {"empty", 0, 100},
                {"singleton", 1, 100},

                // maxRecordInRam for AsyncWriteSortingCollection is equals to 300 / (sort_col_threads + 1) = 100
                {"less than threshold", 100, 200 * (SORT_COL_THREADS + 1)},
                {"threshold minus 1", 99, 100 * (SORT_COL_THREADS + 1)},
                {"greater than threshold", 550, 100 * (SORT_COL_THREADS + 1)},
                {"threshold multiple", 600, 100 * (SORT_COL_THREADS + 1)},
                {"threshold multiple plus one", 101, 100 * (SORT_COL_THREADS + 1)},
                {"exactly threshold", 100, 100 * (SORT_COL_THREADS + 1)},
        };
    }

    boolean shouldTmpDirBeEmpty(int numStringsToGenerate, int maxRecordsInRam) {
        return numStringsToGenerate <= maxRecordsInRam / (Defaults.SORTING_COLLECTION_THREADS + 1);
    }

    // for changing Defaults.SORTING_COLLECTION_THREADS
    private static void changeDefaultsParam(Class clazz, String fieldName, Object newValue)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    @Override
    SortingCollection<String> makeSortingCollection(int maxRecordsInRam) {
        return new AsyncWriteSortingCollection<>(String.class,
                new StringCodec(), new StringComparator(), maxRecordsInRam, tmpDir());
    }
}
