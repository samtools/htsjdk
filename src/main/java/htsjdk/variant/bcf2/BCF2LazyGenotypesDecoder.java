/*
 * Copyright (c) 2012 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decodes the genotype block of a BCF record on demand. One instance serves every record of a file: everything a
 * record needs is in its {@link BCF2Codec.LazyData}, and what the decoder holds itself (the dictionary and the field
 * decoders) never changes after the header is read, so records may be decoded on any thread, several at once.
 *
 * @author Mark DePristo
 * @since 5/12
 */
public class BCF2LazyGenotypesDecoder implements LazyGenotypesContext.LazyParser {
    private final BCFDictionary dictionary;
    private final BCF2GenotypeFieldDecoders gtFieldDecoders;

    /**
     * One array of builders, one per sample, lent to whichever decode asks first and handed back when it is done.
     * A decode that finds it lent out makes its own. Reusing them matters: a genotype decodes in a few tens of
     * nanoseconds, and a fresh builder per sample per record costs a measurable share of that on wide files.
     */
    private final AtomicReference<GenotypeBuilder[]> spareBuilders = new AtomicReference<>();

    BCF2LazyGenotypesDecoder(final BCFDictionary dictionary, final BCF2GenotypeFieldDecoders gtFieldDecoders) {
        this.dictionary = dictionary;
        this.gtFieldDecoders = gtFieldDecoders;
    }

    @Override
    public LazyGenotypesContext.LazyData parse(final Object data) {
        final BCF2Codec.LazyData lazyData = (BCF2Codec.LazyData) data;
        final List<String> samples = lazyData.header.getGenotypeSamples();
        final int nSamples = samples.size();

        GenotypeBuilder[] builders = spareBuilders.getAndSet(null);
        if (builders == null || builders.length != nSamples) {
            builders = new GenotypeBuilder[nSamples];
            for (int i = 0; i < nSamples; i++) builders[i] = new GenotypeBuilder(samples.get(i));
        } else {
            for (final GenotypeBuilder builder : builders) builder.reset(true);
        }

        try {
            final BCF2Decoder decoder = new BCF2Decoder(lazyData.bytes);
            for (int i = 0; i < lazyData.nGenotypeFields; i++) {
                final int offset = (Integer) decoder.decodeTypedValue();
                final String field = dictionary.getString(offset);

                final byte typeDescriptor = decoder.readTypeDescriptor();
                final int numElements = decoder.decodeNumberOfElements(typeDescriptor);
                final BCF2GenotypeFieldDecoders.Decoder fieldDecoder = gtFieldDecoders.getDecoder(field);
                try {
                    fieldDecoder.decode(lazyData.alleles, field, decoder, typeDescriptor, numElements, builders);
                } catch (ClassCastException e) {
                    throw new TribbleException("BUG: expected encoding of field " + field
                            + " inconsistent with the value observed in the decoded value");
                }
            }

            final ArrayList<Genotype> genotypes = new ArrayList<Genotype>(nSamples);
            for (final GenotypeBuilder gb : builders) genotypes.add(gb.make());
            spareBuilders.set(builders);

            return new LazyGenotypesContext.LazyData(
                    genotypes, lazyData.header.getSampleNamesInOrder(), lazyData.header.getSampleNameToOffset());
        } catch (IOException e) {
            throw new TribbleException("Unexpected IOException parsing already read genotypes data block", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new TribbleException("BCF genotype block is truncated: " + e.getMessage(), e);
        }
    }
}
