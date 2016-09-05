/*
 * The MIT License
 *
 * Copyright (c) 2016 Pierre Lindenbaum @yokofakun Institut du Thorax - Nantes - France
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

package htsjdk.variant.variantcontext;

/**
 * Type of Structural Variant as defined in the VCF spec 4.2
 *
 */
public enum StructuralVariantType {
    /** Deletion relative to the reference */
    DEL,
    /** Insertion of novel sequence relative to the reference */
    INS,
    /** Region of elevated copy number relative to the reference */
    DUP,
    /** Inversion of reference sequence */
    INV,
    /** Copy number variable region */
    CNV,
    /** breakend structural variation. VCF Specification : <cite>An arbitrary rearrangement
     *  event can be summarized as a set of novel adjacencies.
     *  Each adjacency ties together two breakends.</cite>
     */
    BND
}
