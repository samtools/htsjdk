/*
* Copyright (c) 2014 The Broad Institute
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

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndexCreator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.EnumSet;

/*
 * Created with IntelliJ IDEA.
 * User: thibault
 * Date: 3/7/14
 * Time: 2:07 PM
 */
/**
 * @author thibault
 * 
 * <p>
 * Provides methods for creating <code>VariantContextWriter</code>s using the Builder pattern.
 * Replaces <code>VariantContextWriterFactory</code>.
 * </p>
 * <p>
 * The caller must choose an output file or an output stream for the <code>VariantContextWriter</code> to write to.
 * When a file is chosen, the output stream is created implicitly based on Defaults and options passed to the builder.
 * When a stream is chosen, it is passed unchanged to the <code>VariantContextWriter</code>.
 * </p>
 * <p>
 * Example: Create a series of files with buffering and indexing on the fly.
 * Determine the appropriate file type based on filename.
 * </p>

   <pre>
   VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
       .setReferenceDictionary(refDict)
       .setOption(Options.INDEX_ON_THE_FLY)
       .setBuffer(8192);
 
   VariantContextWriter sample1_writer = builder
       .setOutputFile("sample1.vcf")
       .build();
   VariantContextWriter sample2_writer = builder
       .setOutputFile("sample2.bcf")
       .build();
   VariantContextWriter sample3_writer = builder
       .setOutputFile("sample3.vcf.bgzf")
       .build();
   </pre>
   
   <p>
 * Example: Explicitly turn off buffering and explicitly set the file type
 * </p>
 * 
 * <pre>
   VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
       .setReferenceDictionary(refDict)
       .setOption(Options.INDEX_ON_THE_FLY)
       .unsetBuffering();
 
   VariantContextWriter sample1_writer = builder
       .setOutputFile("sample1.custom_extension")
       .setOutputFileType(OutputType.VCF)
       .build();
   VariantContextWriter sample2_writer = builder
       .setOutputFile("sample2.custom_extension")
       .setOutputFileType(OutputType.BLOCK_COMPRESSED_VCF)
       .build();
   </pre>
 */
public class VariantContextWriterBuilder {
    public static final EnumSet<Options> DEFAULT_OPTIONS = EnumSet.of(Options.INDEX_ON_THE_FLY);
    public static final EnumSet<Options> NO_OPTIONS = EnumSet.noneOf(Options.class);

    public enum OutputType {
        UNSPECIFIED,
        VCF,
        BCF,
        BLOCK_COMPRESSED_VCF,
        VCF_STREAM,
        BCF_STREAM
    }

    public static final EnumSet<OutputType> FILE_TYPES = EnumSet.of(OutputType.VCF, OutputType.BCF, OutputType.BLOCK_COMPRESSED_VCF);
    public static final EnumSet<OutputType> STREAM_TYPES = EnumSet.of(OutputType.VCF_STREAM, OutputType.BCF_STREAM);

    private SAMSequenceDictionary refDict = null;
    private OutputType outType = OutputType.UNSPECIFIED;
    private File outFile = null;
    private OutputStream outStream = null;
    private IndexCreator idxCreator = null;
    private int bufferSize = Defaults.BUFFER_SIZE;
    private boolean createMD5 = Defaults.CREATE_MD5;
    protected EnumSet<Options> options = DEFAULT_OPTIONS.clone();

    /**
     * Default constructor.  Adds <code>USE_ASYNC_IO</code> to the Options if it is present in Defaults.
     */
    public VariantContextWriterBuilder() {
        if (Defaults.USE_ASYNC_IO_WRITE_FOR_TRIBBLE) {
            options.add(Options.USE_ASYNC_IO);
        }
    }

    /**
     * Set the reference dictionary to be used by <code>VariantContextWriter</code>s created by this builder.
     *
     * @param refDict the reference dictionary
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setReferenceDictionary(final SAMSequenceDictionary refDict) {
        this.refDict = refDict;
        return this;
    }

    /**
     * Set the output file for the next <code>VariantContextWriter</code> created by this builder.
     * Determines file type implicitly from the filename.
     *
     * @param outFile the file the <code>VariantContextWriter</code> will write to
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOutputFile(final File outFile) {
        this.outFile = outFile;
        this.outStream = null;
        this.outType = determineOutputTypeFromFile(outFile);
        return this;
    }

    /**
     * Set the output file for the next <code>VariantContextWriter</code> created by this builder.
     * Determines file type implicitly from the filename.
     *
     * @param outFile the file the <code>VariantContextWriter</code> will write to
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOutputFile(final String outFile) {
        return setOutputFile(new File(outFile));
    }

    /**
     * Set the output file type for the next <code>VariantContextWriter</code> created by this builder.
     *
     * @param outType the type of file the <code>VariantContextWriter</code> will write to
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOutputFileType(final OutputType outType) {
        if (!FILE_TYPES.contains(outType))
            throw new IllegalArgumentException("Must choose a file type, not other output types.");

        if (this.outFile == null || this.outStream != null)
            throw new IllegalArgumentException("Cannot set a file type if the output is not to a file.");

        this.outType = outType;
        return this;
    }

    /**
     * Set the output VCF stream for the next <code>VariantContextWriter</code> created by this builder.
     * If buffered writing is desired, caller must provide some kind of buffered <code>OutputStream</code>.
     *
     * @param outStream the output stream to write to
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOutputVCFStream(final OutputStream outStream) {
        this.outStream = outStream;
        this.outFile = null;
        this.outType = OutputType.VCF_STREAM;
        return this;
    }

    /**
     * Set the output BCF stream for the next <code>VariantContextWriter</code> created by this builder.
     * If buffered writing is desired, caller must provide some kind of buffered <code>OutputStream</code>.
     *
     * @param outStream the output stream to write to
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOutputBCFStream(final OutputStream outStream) {
        this.outStream = outStream;
        this.outFile = null;
        this.outType = OutputType.BCF_STREAM;
        return this;
    }

    /**
     * Set the output stream (VCF, by default) for the next <code>VariantContextWriter</code> created by this builder.
     * If buffered writing is desired, caller must provide some kind of buffered <code>OutputStream</code>.
     *
     * @param outStream the output stream to write to
     * @return this VariantContextWriterBuilder
     */
    public VariantContextWriterBuilder setOutputStream(final OutputStream outStream) {
        return setOutputVCFStream(outStream);
    }

    /**
     * Set an IndexCreator for the next <code>VariantContextWriter</code> created by this builder.
     *
     * @param idxCreator the <code>IndexCreator</code> to use
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setIndexCreator(final IndexCreator idxCreator) {
        this.idxCreator = idxCreator;
        return this;
    }

    /**
     * Do not pass an <code>IndexCreator</code> to the next <code>VariantContextWriter</code> created by this builder.
     *
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder clearIndexCreator() {
        this.idxCreator = null;
        return this;
    }

    /**
     * Set a buffer size for the file output stream passed to the next <code>VariantContextWriter</code> created by this builder.
     * Set to 0 for no buffering.
     * Does not affect OutputStreams passed directly to <code>VariantContextWriterBuilder</code>.
     *
     * @param bufferSize the buffer size to use
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setBuffer(final int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * Do not use buffering in the next <code>VariantContextWriter</code> created by this builder.
     * Does not affect <code>OutputStream</code>s passed directly to <code>VariantContextWriterBuilder</code>.
     *
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder unsetBuffering() {
        this.bufferSize = 0;
        return this;
    }

    /**
     * Choose whether to also create an MD5 digest file for the next <code>VariantContextWriter</code> created by this builder.
     *
     * @param createMD5 boolean, <code>true</code> to create an MD5 digest
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setCreateMD5(final boolean createMD5) {
        this.createMD5 = createMD5;
        return this;
    }

    /**
     * Create an MD5 digest file for the next <code>VariantContextWriter</code> created by this builder.
     *
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setCreateMD5() {
        return setCreateMD5(true);
    }

    /**
     * Don't create an MD5 digest file for the next <code>VariantContextWriter</code> created by this builder.
     *
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder unsetCreateMD5() {
        return setCreateMD5(false);
    }

    /**
     * Replace the set of <code>Options</code> for the <code>VariantContextWriterBuilder</code> with a new set.
     *
     * @param options the complete set of options to use
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOptions(final EnumSet<Options> options) {
        this.options = options;
        return this;
    }

    /**
     * Add one option to the set of <code>Options</code> for the <code>VariantContextWriterBuilder</code>, if it's not already present.
     *
     * @param option the option to set
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder setOption(final Options option) {
        this.options.add(option);
        return this;
    }

    /**
     * Remove one option from the set of <code>Options</code> for the <code>VariantContextWriterBuilder</code>, if it's present.
     *
     * @param option the option to unset
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder unsetOption(final Options option) {
        this.options.remove(option);
        return this;
    }

    /**
     * Set or unset option depending on the boolean given
     * @param option the option to modify
     * @param setIt true to set the option, false to unset it.
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public VariantContextWriterBuilder modifyOption(final Options option, final boolean setIt) {
        return (setIt) ? this.setOption(option) : this.unsetOption(option);
    }

    /**
     * Add one option to the set of default <code>Options</code> that will be used as the initial set of options
     * for all VariantContextWriterBuilders created after this call.
     *
     * @param option the option to set
     */
    public static void setDefaultOption(final Options option) {
        VariantContextWriterBuilder.DEFAULT_OPTIONS.add(option);
    }

    /**
     * Remove an option from the set of default <code>Options</code> that will be used as the initial set of options
     * for all VariantContextWriterBuilders created after this call.
     *
     * @param option the option to unset
     * @return this <code>VariantContextWriterBuilder</code>
     */
    public static void unsetDefaultOption(final Options option) {
        VariantContextWriterBuilder.DEFAULT_OPTIONS.remove(option);
    }

    /**
     * Remove all options from the set of <code>Options</code> for the <code>VariantContextWriterBuilder</code>.
     *
     * @return this VariantContextWriterBuilder
     */
    public VariantContextWriterBuilder clearOptions() {
        this.options = NO_OPTIONS.clone();
        return this;
    }

    /**
     * Used for testing; tests if the option is set
     * @param option the option to test
     * @return true if the option is set, false otherwise.
     */
    boolean isOptionSet(final Options option) {
        return this.options.contains(option);
    }

    /**
     * Validate and build the <code>VariantContextWriter</code>.
     *
     * @return the <code>VariantContextWriter</code> as specified by previous method calls
     * @throws RuntimeIOException if the writer is configured to write to a file, and the corresponding path does not exist.
     * @throws IllegalArgumentException if no output file or stream is specified.
     * @throws IllegalArgumentException if <code>Options.INDEX_ON_THE_FLY</code> is specified and no reference dictionary is provided.
     * @throws IllegalArgumentException if <code>Options.INDEX_ON_THE_FLY</code> is specified and a stream output is specified.
     */
    public VariantContextWriter build() {
        VariantContextWriter writer = null;

        // don't allow FORCE_BCF to modify the outType state
        OutputType typeToBuild = this.outType;

        if (this.options.contains(Options.FORCE_BCF)) {
            if (FILE_TYPES.contains(this.outType))
                typeToBuild = OutputType.BCF;
            else if (STREAM_TYPES.contains(this.outType))
                typeToBuild = OutputType.BCF_STREAM;
        }

        OutputStream outStreamFromFile = this.outStream;
        if (FILE_TYPES.contains(this.outType)) {
            try {
                outStreamFromFile = IOUtil.maybeBufferOutputStream(new FileOutputStream(outFile), bufferSize);
            } catch (final FileNotFoundException e) {
                throw new RuntimeIOException("File not found: " + outFile, e);
            }

            if (createMD5)
                outStreamFromFile = new Md5CalculatingOutputStream(outStreamFromFile, new File(outFile.getAbsolutePath() + ".md5"));
        }

        switch (typeToBuild) {
            case UNSPECIFIED:
                throw new IllegalArgumentException("Must specify file or stream output type.");
            case VCF:
                if ((refDict == null) && (options.contains(Options.INDEX_ON_THE_FLY)))
                    throw new IllegalArgumentException("A reference dictionary is required for creating Tribble indices on the fly");

                writer = createVCFWriter(outFile, outStreamFromFile);
                break;
            case BLOCK_COMPRESSED_VCF:
                if (refDict == null)
                    idxCreator = new TabixIndexCreator(TabixFormat.VCF);
                else
                    idxCreator = new TabixIndexCreator(refDict, TabixFormat.VCF);

                writer = createVCFWriter(outFile, new BlockCompressedOutputStream(outStreamFromFile, outFile));
                break;
            case BCF:
                if ((refDict == null) && (options.contains(Options.INDEX_ON_THE_FLY)))
                    throw new IllegalArgumentException("A reference dictionary is required for creating Tribble indices on the fly");

                writer = createBCFWriter(outFile, outStreamFromFile);
                break;
            case VCF_STREAM:
                if (options.contains(Options.INDEX_ON_THE_FLY))
                    throw new IllegalArgumentException("VCF index creation not supported for stream output.");

                writer = createVCFWriter(null, outStream);
                break;
            case BCF_STREAM:
                if (options.contains(Options.INDEX_ON_THE_FLY))
                    throw new IllegalArgumentException("BCF index creation not supported for stream output.");

                writer = createBCFWriter(null, outStream);
                break;
        }

        if (this.options.contains(Options.USE_ASYNC_IO))
            writer = new AsyncVariantContextWriter(writer, AsyncVariantContextWriter.DEFAULT_QUEUE_SIZE);

        return writer;
     }

    /**
     * Attempts to determine the type of file/data to write based on the File path being
     * written to. Will attempt to determine using the logical filename; if that fails it will
     * attempt to resolve any symlinks and try again.  If that fails, and the output file exists
     * but is neither a file or directory then VCF_STREAM is returned.
     */
    protected static OutputType determineOutputTypeFromFile(final File f) {
        if (isBCF(f)) {
            return OutputType.BCF;
        } else if (isCompressedVCF(f)) {
            return OutputType.BLOCK_COMPRESSED_VCF;
        } else if (isVCF(f)) {
            return OutputType.VCF;
        }
        else {
            // See if we have a special file (device, named pipe, etc.)
            final File canonical = new File(IOUtil.getFullCanonicalPath(f));
            if (!canonical.equals(f)) {
                return determineOutputTypeFromFile(canonical);
            }
            else if (f.exists() && !f.isFile() && !f.isDirectory()) {
                return OutputType.VCF_STREAM;
            } else {
                return OutputType.UNSPECIFIED;
            }
        }
    }

    private static boolean isVCF(final File outFile) {
        return outFile != null && outFile.getName().endsWith(".vcf");
    }

    private static boolean isBCF(final File outFile) {
        return outFile != null && outFile.getName().endsWith(".bcf");
    }

    private static boolean isCompressedVCF(final File outFile) {
        if (outFile == null)
            return false;

        return AbstractFeatureReader.hasBlockCompressedExtension(outFile);
    }

    private VariantContextWriter createVCFWriter(final File writerFile, final OutputStream writerStream) {
        if (idxCreator == null) {
            return new VCFWriter(writerFile, writerStream, refDict,
                    options.contains(Options.INDEX_ON_THE_FLY),
                    options.contains(Options.DO_NOT_WRITE_GENOTYPES),
                    options.contains(Options.ALLOW_MISSING_FIELDS_IN_HEADER),
                    options.contains(Options.WRITE_FULL_FORMAT_FIELD));
        }
        else {
            return new VCFWriter(writerFile, writerStream, refDict, idxCreator,
                    options.contains(Options.INDEX_ON_THE_FLY),
                    options.contains(Options.DO_NOT_WRITE_GENOTYPES),
                    options.contains(Options.ALLOW_MISSING_FIELDS_IN_HEADER),
                    options.contains(Options.WRITE_FULL_FORMAT_FIELD));
        }
    }

    private VariantContextWriter createBCFWriter(final File writerFile, final OutputStream writerStream) {
        if (idxCreator == null) {
            return new BCF2Writer(writerFile, writerStream, refDict,
                    options.contains(Options.INDEX_ON_THE_FLY),
                    options.contains(Options.DO_NOT_WRITE_GENOTYPES));
        }
        else {
            return new BCF2Writer(writerFile, writerStream, refDict, idxCreator,
                    options.contains(Options.INDEX_ON_THE_FLY),
                    options.contains(Options.DO_NOT_WRITE_GENOTYPES));
        }
    }
}
