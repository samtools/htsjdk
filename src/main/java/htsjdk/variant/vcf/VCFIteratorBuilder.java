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
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.util.AbstractIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * A Class building {@link htsjdk.variant.vcf.VCFIterator}
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
 * @see htsjdk.variant.vcf.VCFIterator
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
    @SuppressWarnings("static-method")
    public VCFIterator open(final InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("input stream is null");
            }
        // wrap the input stream into a BufferedInputStream to reset/read a BCFHeader or a GZIP
        // buffer must be large enough to contain the BCF header and/or GZIP signature
        BufferedInputStream  bufferedinput = new BufferedInputStream(in, Math.max(BCF2Codec.SIZEOF_BCF_HEADER, IOUtil.GZIP_HEADER_READ_LENGTH));
        // test for gzipped inputstream
        if(IOUtil.isGZIPInputStream(bufferedinput)) {
            // this is a gzipped input stream, wrap it into GZIPInputStream
            // and re-wrap it into BufferedInputStream so we can test for the BCF header
            bufferedinput = new BufferedInputStream(new GZIPInputStream(bufferedinput), BCF2Codec.SIZEOF_BCF_HEADER);
        }

        // try to read a BCF header
        final BCFVersion bcfVersion = BCF2Codec.tryReadBCFVersion(bufferedinput);

        if (bcfVersion != null) {
            //this is BCF
            return new BCFInputStreamIterator(bufferedinput);
        } else {
            //this is VCF
            return new VCFReaderIterator(bufferedinput);
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
        return open(path, null);
    }

    /**
     * creates a VCF iterator from a Path
     *
     * @param path the path
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final Path path) throws IOException {
       return open(path, null);
    }

    /**
     * creates a VCF iterator from a Path
     *
     * @param path the file path
     * @param wrapper wrapper for {@link htsjdk.samtools.seekablestream.SeekablePathStream}. Can be null.
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final Path path, final Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
        return open(new SeekablePathStream(path, wrapper));
    }

    /**
     * creates a VCF iterator from a URI It detects if the stream is a BCF
     * stream or a GZipped stream.
     *
     * @param path the Path
     * @param wrapper wrapper for {@link htsjdk.samtools.seekablestream.SeekablePathStream}. Can be null.
     * @return the VCFIterator
     * @throws IOException
     */
    public VCFIterator open(final String path, final Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
        return open(ParsingUtils.openInputStream(path, wrapper));
    }

    /**
     * creates a VCF iterator from a File
     *
     * @param file the file (can be bcf, vcf, vcf.gz)
     * @return the VCFIterator
     * @throws IOException
     */
    @SuppressWarnings("static-method")
    public VCFIterator open(final File file) throws IOException {
        return this.open(file.toPath());
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
        private final LineIterator lineIterator;

        VCFReaderIterator(final InputStream inputStream) {
            this.inputStream = inputStream;
            this.lineIterator = this.codec.makeSourceFromStream(this.inputStream);
            this.vcfHeader = (VCFHeader) this.codec.readActualHeader(this.lineIterator);
        }

        @Override
        public VCFHeader getHeader() {
            return this.vcfHeader;
        }

        @Override
        protected VariantContext advance() {
            return this.lineIterator.hasNext() ? this.codec.decode(this.lineIterator.next()) : null;
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
            this.inputStream = this.codec.makeSourceFromStream(inputStream);
            this.vcfHeader = (VCFHeader) this.codec.readHeader(this.inputStream).getHeaderValue();
        }

        @Override
        public VCFHeader getHeader() {
            return this.vcfHeader;
        }

        @Override
        protected VariantContext advance() {
            return this.codec.isDone(this.inputStream) ? null : this.codec.decode(this.inputStream);
        }

        @Override
        public void close() {
            this.inputStream.close();
        }
    }

}
