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
package htsjdk.samtools.util.zip;

import com.intel.gkl.compression.IntelDeflater;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.zip.Deflater;

/**
 * Create a hardware-accelerated Intel deflater if the required library is available on the classpath and
 * {@link Defaults#TRY_USE_INTEL_DEFLATER} is true, otherwise create a standard JDK deflater.
 *
 * The Intel deflater has been shown to significantly (by ~30%) outperform the standard JDK deflater
 * at compression level 1, and to outperform the JDK deflater by smaller but still significant margins
 * at higher compression levels.
 *
 * We use reflection to instantiate the IntelDeflater so that DeflaterFactory can be loaded
 * and used at runtime even when the IntelDeflater is not on the classpath.
 */
public class DeflaterFactory {

    public static final String INTEL_DEFLATER_CLASS_NAME = "com.intel.gkl.compression.IntelDeflater";

    private static final boolean usingIntelDeflater;
    private static Constructor<? extends Deflater> intelDeflaterConstructor;

    static {
        boolean intelDeflaterLibrarySuccessfullyLoaded = false;

        try {
            if (Defaults.TRY_USE_INTEL_DEFLATER) {
                @SuppressWarnings("unchecked")
                final Class<IntelDeflater> clazz = (Class<IntelDeflater>)Class.forName(INTEL_DEFLATER_CLASS_NAME);

                // Get the constructor we'll use to create new instances of IntelDeflater in makeDeflater()
                intelDeflaterConstructor = clazz.getConstructor(Integer.TYPE, Boolean.TYPE);

                // We also need to call load() on an IntelDeflater instance to actually load the native library
                // (.so or .dylib) from a resource on the classpath. This should only be done once at startup.
                final Constructor<IntelDeflater> zeroArgIntelDeflaterConstructor = clazz.getConstructor();
                intelDeflaterLibrarySuccessfullyLoaded = zeroArgIntelDeflaterConstructor != null &&
                                                         zeroArgIntelDeflaterConstructor.newInstance().load();
            }
        }
        catch ( ClassNotFoundException | NoSuchMethodException | UnsatisfiedLinkError |
                IllegalAccessException | InstantiationException | InvocationTargetException e ) {
            intelDeflaterConstructor = null;
        }

        usingIntelDeflater = intelDeflaterConstructor != null && intelDeflaterLibrarySuccessfullyLoaded;
    }

    public static Deflater makeDeflater(final int compressionLevel, final boolean nowrap) {
        // The Intel Deflater currently only supports compression level 1
        if ( usingIntelDeflater() && compressionLevel == 1 ) {
            try {
                return intelDeflaterConstructor.newInstance(compressionLevel, nowrap);
            }
            catch ( IllegalAccessException | InstantiationException | InvocationTargetException e ) {
                throw new SAMException("Error constructing IntelDeflater", e);
            }
        } else {
            return new Deflater(compressionLevel, nowrap);
        }
    }

    public static boolean usingIntelDeflater() {
        return usingIntelDeflater;
    }
}
