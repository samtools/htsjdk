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
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ParallelBlockCompressedOutputStreamTest extends BlockCompressedOutputStreamTest {

    @BeforeClass
    public void setup() throws NoSuchFieldException, IllegalAccessException {
        changeDefaultsParam(Defaults.class, "ZIP_THREADS", 2);
    }

    @AfterClass
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        changeDefaultsParam(Defaults.class, "ZIP_THREADS", 0);
    }

    @Test(expectedExceptions = CompletionException.class)
    public void pbcosShouldRethrowExceptionFromDeflateTasksOnFlush() throws IOException {
        final byte[] data = new byte[65536];
        File tmp = File.createTempFile("pbcos", "tmp");
        AbstractBlockCompressedOutputStream stream = new ParallelBlockCompressedOutputStreamForTests(tmp);
        stream.write(data);
        stream.flush();
    }

    @Test(expectedExceptions = CompletionException.class)
    public void pbcosShouldRethrowExceptionFromDeflateTasksOnClose() throws IOException {
        final byte[] data = new byte[65536];
        File tmp = File.createTempFile("pbcos", "tmp");
        AbstractBlockCompressedOutputStream stream = new ParallelBlockCompressedOutputStreamForTests(tmp);
        stream.write(data);
        stream.close();
    }

    private static class ParallelBlockCompressedOutputStreamForTests extends ParallelBlockCompressedOutputStream {

        public ParallelBlockCompressedOutputStreamForTests(File file) {
            super(file);
        }

        @Override
        void submitDeflateTask() {
            compressedBlocksInFuture.add(
                    CompletableFuture.supplyAsync(() -> {
                        throw new RuntimeIOException("Bang!");
                    })
            );
        }
    }

    // for changing Defaults.ZIP_THREADS
    private static void changeDefaultsParam(Class clazz, String fieldName, Object newValue)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

}
