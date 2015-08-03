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
package htsjdk.tribble;

import java.io.IOException;

/**
 * Simple basic class providing much of the basic functionality of codecs
 * Every concrete subclass must implement {@link FeatureCodec#canDecode(String)} to indicate whether it can decode the file.
 * Note that that method is the only way that the right codec for a file is identified and that <bold>only one</bold> codec
 * is allowed to identify itself as being able to decode any given file.
 */
public abstract class AbstractFeatureCodec<FEATURE_TYPE extends Feature, SOURCE> implements FeatureCodec<FEATURE_TYPE, SOURCE> {
    private final Class<FEATURE_TYPE> myClass;

    protected AbstractFeatureCodec(final Class<FEATURE_TYPE> myClass) {
        this.myClass = myClass;
    }
    
    @Override
    public Feature decodeLoc(final SOURCE source) throws IOException {
        return decode(source);
    }

    @Override
    public Class<FEATURE_TYPE> getFeatureType() {
        return myClass;
    }
}
