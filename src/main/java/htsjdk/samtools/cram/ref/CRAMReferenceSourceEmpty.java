/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMSequenceRecord;

/**
 * Trivial implementation of CRAMReferenceSource with a private constructor
 * and a singlton instance to be used when opening CRAM files for reading their header only.
 * Any attempt at getting bases from this reference source will result in a thrown exception.
 *
 * @author Yossi Farjoun
 */

final public class CRAMReferenceSourceEmpty implements CRAMReferenceSource {
    private CRAMReferenceSourceEmpty() {
        super();
    }

    @Override
    public byte[] getReferenceBases(SAMSequenceRecord sequenceRecord, boolean tryNameVariants) {
        throw new IllegalAccessError("This reference source can only be used to view CRAM headers");
    }

    static public CRAMReferenceSourceEmpty EMPTY_REFERENCE = new CRAMReferenceSourceEmpty();
}
