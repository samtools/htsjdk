/*
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
package htsjdk.variant.vcf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.util.AbstractIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * A Class building {@link VCFReaderIterator}
 * 
 * Example:
 * 
 * <pre>
 * VCFIterator r = new VCFIteratorBuilder().open(System.in);
 * while (r.hasNext()) {
 *     System.out.println(r.next());
 * }
 * r.close();
 * </pre>
 * 
 * @author Pierre Lindenbaum / @yokofakun
 * @see {@link VCFIterator}
 *
 */

public class VCFIteratorBuilder {

    /**
     * creates a VCF iterator from an input stream It detects if the stream is a
     * BCF stream or a GZipped stream.
     * 
     * @param in inputstream
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("input stream is null");
            }
        // buffer must be large enough to contain the BCF header (+ min/max version) and/or GZIP signature
        final int bufferSize = Math.max(BCFVersion.MAGIC_HEADER_START.length + 2*Byte.BYTES , IOUtil.GZIP_SIGNATURE.length);
        // try to read the BCF header
        final BufferedInputStream bufferedinput = new BufferedInputStream(in,bufferSize);
        bufferedinput.mark(bufferSize);
        final BCFVersion bcfVersion = BCFVersion.readBCFVersion(bufferedinput);
        bufferedinput.reset();

        try {
            if (bcfVersion != null) {
                //this is BCF
                return new BCFInputStreamIterator(bufferedinput);
            } else {
                //this is VCF or VCF.gz, gunzip the input stream if needed
                return new VCFReaderIterator( IOUtil.isGZIPInputStream(bufferedinput) ? 
                        new GZIPInputStream(bufferedinput) : bufferedinput );
            }
        } catch (final IOException error) {
           throw new IOException("Cannot read the input stream as 'BCF' or 'VCF.gz' or 'VCF'", error);
        }
    }

    /**
     * creates a VCF iterator from a URI It detects if the stream is a BCF
     * stream or a GZipped stream.
     * 
     * @param path the Path
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final String path) throws IOException {
        return open(ParsingUtils.openInputStream(path, null));
    }

    /**
     * creates a VCF iterator from a Path
     * 
     * @param path the path
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final Path path) throws IOException {
        return open(new SeekablePathStream(path, null));
    }
    
    /**
     * creates a VCF iterator from a File
     * 
     * @param file the file (can be bcf, vcf, vcf.gz)
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final File file) throws IOException {
        IOUtil.assertFileIsReadable(file);
        return open(new FileInputStream(file));
    }

    /** implementation of VCFIterator, reading VCF */
    private static class VCFReaderIterator 
            extends AbstractIterator<VariantContext>
            implements VCFIterator {
        /** delegate input stream */
        private final InputStream inputStream;
        /** VCF codec */
        private final VCFCodec codec = new VCFCodec();
        /** VCF header */
        private final VCFHeader vcfHeader;
        /** Iterator over the lines of the VCF */
        private final AsciiLineReaderIterator lineIterator;

        VCFReaderIterator(final InputStream inputStream) {
            this.inputStream = inputStream;
            this.lineIterator = new AsciiLineReaderIterator(new AsciiLineReader(inputStream));
            this.vcfHeader = (VCFHeader) this.codec.readActualHeader(this.lineIterator);
        }

        @Override
        public VCFHeader getFileHeader() {
            return this.vcfHeader;
        }

        @Override
        protected VariantContext advance() {
            return this.lineIterator.hasNext() ?
                    this.codec.decode(this.lineIterator.next()) :
                    null;
        }

        @Override
        public void close() {
            CloserUtil.close(this.lineIterator);
            CloserUtil.close(this.inputStream);
        }
    }

    /** implementation of VCFIterator, reading BCF */
    private static class BCFInputStreamIterator 
            extends AbstractIterator<VariantContext>
            implements VCFIterator {
        /** the underlying input stream */
        private final PositionalBufferedStream inputStream;
        /** BCF codec */
        private final BCF2Codec codec = new BCF2Codec();
        /** the VCF header */
        private final VCFHeader vcfHeader;

        BCFInputStreamIterator(final InputStream inputStream) {
            this.inputStream = new PositionalBufferedStream(inputStream);
            this.vcfHeader = (VCFHeader) this.codec.readHeader(this.inputStream).getHeaderValue();
        }

        @Override
        public VCFHeader getFileHeader() {
            return this.vcfHeader;
        }

        @Override
        protected VariantContext advance() {
            return this.codec.isDone(this.inputStream) ?
                    null :
                    this.codec.decode(this.inputStream);
        }

        @Override
        public void close() {
            this.inputStream.close();
        }
    }

}
