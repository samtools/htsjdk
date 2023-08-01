package htsjdk.samtools;

import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BamFileIoUtils {
    private static final Log LOG = Log.getInstance(BamFileIoUtils.class);

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#BAM} instead.
     */
    @Deprecated
    public static final String BAM_FILE_EXTENSION = FileExtensions.BAM;

    public static boolean isBamFile(final File file) {
        return ((file != null) && SamReader.Type.BAM_TYPE.hasValidFileExtension(file.getName()));
    }

    public static void reheaderBamFile(final SAMFileHeader samFileHeader, final Path inputFile, final Path outputFile) {
        reheaderBamFile(samFileHeader, inputFile, outputFile, true, true);
    }


    /**
     * Support File input types for backward compatibility. Use the same method with Path inputs below.
     */
    @Deprecated
    public static void reheaderBamFile(final SAMFileHeader samFileHeader, final File inputFile, final File outputFile, final boolean createMd5, final boolean createIndex) {
        reheaderBamFile(samFileHeader, inputFile.toPath(), outputFile.toPath(), createMd5, createIndex);
    }

    /**
     * Copy a BAM file but replacing the header
     *
     * @param samFileHeader The header to use in the new file
     * @param inputFile     The BAM file to copy, sans header
     * @param outputFile    The new BAM file, constructed with the new header and the content from inputFile
     * @param createMd5     Whether or not to create an MD5 file for the new BAM
     * @param createIndex   Whether or not to create an index file for the new BAM
     */
    public static void reheaderBamFile(final SAMFileHeader samFileHeader, final Path inputFile, final Path outputFile, final boolean createMd5, final boolean createIndex) {
        IOUtil.assertFileIsReadable(inputFile);
        IOUtil.assertFileIsWritable(outputFile); // tsato: what do I do with this...

        try {
            BlockCompressedInputStream.assertNonDefectivePath(inputFile);
            assertSortOrdersAreEqual(samFileHeader, inputFile);

            final OutputStream outputStream = buildOutputStream(outputFile, createMd5, createIndex);

            BAMFileWriter.writeHeader(outputStream, samFileHeader);
            blockCopyBamFile(inputFile, outputStream, true, false);

            CloserUtil.close(inputFile);
            outputStream.close();
        } catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }

    @Deprecated
    public static void blockCopyBamFile(final File inputFile, final OutputStream outputStream, final boolean skipHeader, final boolean skipTerminator) {
        blockCopyBamFile(inputFile.toPath(), outputStream, skipHeader, skipTerminator);
    }

    /**
     * Copy data from a BAM file to an OutputStream by directly copying the gzip blocks
     *
     * @param inputFile      The file to be copied
     * @param outputStream   The stream to write the copied data to
     * @param skipHeader     If true, the header of the input file will not be copied to the output stream
     * @param skipTerminator If true, the terminator block of the input file will not be written to the output stream
     */
    // tsato: let's do it....this is the path version of blockCopyBamFile. Keeping the File version below, to be deleted.
    public static void blockCopyBamFile(final Path inputFile, final OutputStream outputStream, final boolean skipHeader, final boolean skipTerminator) {
        // FileInputStream in = null;
        try (final CountingInputStream in = new CountingInputStream(Files.newInputStream(inputFile))){
            // in = new FileInputStream(inputFile);

            // check that a) the end of the file is valid and b) if there's a terminator block and not copy it if skipTerminator is true
            final BlockCompressedInputStream.FileTermination term = BlockCompressedInputStream.checkTermination(inputFile);
            if (term == BlockCompressedInputStream.FileTermination.DEFECTIVE)
                throw new SAMException(inputFile + " does not have a valid GZIP block at the end of the file.");

            if (skipHeader) {
                final long vOffsetOfFirstRecord = SAMUtils.findVirtualOffsetOfFirstRecordInBam(inputFile); // tsato: this is where we "seek"
                // tsato: passing an inputStream directly to BlockCompressed... won't make it seekable (which we need, I think, buy why for block copying...)
                // can we just construct a seekable input stream? Is it buffered? Buffering probably not needed. See transferByStream
                final SeekablePathStream seekablePathStream = new SeekablePathStream(inputFile);
                final BlockCompressedInputStream blockIn = new BlockCompressedInputStream(seekablePathStream); // tsato hmmm...
                // final BlockCompressedInputStream blockIn = new BlockCompressedInputStream(IOUtil.openFileForReading(inputFile)); // tsato hmmm...
                blockIn.seek(vOffsetOfFirstRecord); // tsato: if seekablePathStream is not used mFile is null so this throws an error
                final long remainingInBlock = blockIn.available();

                // If we found the end of the header then write the remainder of this block out as a
                // new gzip block and then break out of the while loop
                if (remainingInBlock >= 0) {
                    final BlockCompressedOutputStream blockOut = new BlockCompressedOutputStream(outputStream, (Path)null);
                    IOUtil.transferByStream(blockIn, blockOut, remainingInBlock);
                    blockOut.flush();
                    // Don't close blockOut because closing underlying stream would break everything (tsato: why?)
                }

                long pos = BlockCompressedFilePointerUtil.getBlockAddress(blockIn.getFilePointer());
                blockIn.close();
                while (pos > 0) {
                    pos -= in.skip(pos);
                }
            }

            // Copy remainder of input stream into output stream (tsato: why would there be anything left? Didn't we close the input stream already?)
            final long currentPos = in.getCount();
            // final long length = inputPath.toFile().length(); // tsato: this right? length of the file in bytes..vs size? -- see below
            final long length = Files.size(inputFile); // tsato: rename to size
            final long skipLast = ((term == BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK) && skipTerminator) ?
                    BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK.length : 0;
            final long bytesToWrite = length - skipLast - currentPos;

            IOUtil.transferByStream(in, outputStream, bytesToWrite);
        } catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }
    
    /**
     * Assumes that all inputs and outputs are block compressed VCF files and copies them without decompressing and parsing
     * most of the gzip blocks. Will decompress and parse blocks up to the one containing the end of the header in each file
     * (often the first block) and re-compress any data remaining in that block into a new block in the output file. Subsequent
     * blocks (excluding a terminator block if present) are copied directly from input to output.
     */
    public static void gatherWithBlockCopying(final List<File> bams, final File output, final boolean createIndex, final boolean createMd5) {
        try {
            OutputStream out = new FileOutputStream(output);
            if (createMd5) out = new Md5CalculatingOutputStream(out, new File(output.getAbsolutePath() + ".md5"));
            File indexFile = null;
            if (createIndex) {
                indexFile = new File(output.getParentFile(), IOUtil.basename(output) + FileExtensions.BAI_INDEX);
                out = new StreamInflatingIndexingOutputStream(out, indexFile);
            }

            boolean isFirstFile = true;

            for (final File f : bams) {
                LOG.info(String.format("Block copying %s ...", f.getAbsolutePath()));
                blockCopyBamFile(f.toPath(), out, !isFirstFile, true);
                isFirstFile = false;
            }

            // And lastly add the Terminator block and close up
            out.write(BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK);
            out.close();

            // It is possible that the modified time on the index file is ever so slightly older than the original BAM file
            // and this makes ValidateSamFile unhappy.
            if (createIndex && (output.lastModified() > indexFile.lastModified())) {
                final boolean success = indexFile.setLastModified(System.currentTimeMillis());
                if (!success) {
                    System.err.print(String.format("Index file is older than BAM file for %s and unable to resolve this", output.getAbsolutePath()));
                }
            }
        } catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }

    // tsato: consolidate as needed....
    private static OutputStream buildOutputStream(final Path outputFile, final boolean createMd5, final boolean createIndex) throws IOException {
        OutputStream outputStream = Files.newOutputStream(outputFile);
        if (createMd5) {
            outputStream = new Md5CalculatingOutputStream(outputStream, Paths.get(outputFile + ".md")); // tsato: is this right?
        }
        if (createIndex) {
            outputStream = new StreamInflatingIndexingOutputStream(outputStream, Paths.get(IOUtil.basename(outputFile.toFile()) + FileExtensions.BAI_INDEX)); // tsato: what happens when I run toFile on a cloud file...
        }
        return outputStream;
    }

//    private static OutputStream buildOutputStream(final File outputFile, final boolean createMd5, final boolean createIndex) throws IOException {
//        OutputStream outputStream = new FileOutputStream(outputFile);
//        if (createMd5) {
//            outputStream = new Md5CalculatingOutputStream(outputStream, new File(outputFile.getAbsolutePath() + ".md5"));
//        }
//        if (createIndex) {
//            outputStream = new StreamInflatingIndexingOutputStream(outputStream, new File(outputFile.getParentFile(), IOUtil.basename(outputFile) + FileExtensions.BAI_INDEX));
//        }
//        return outputStream;
//    }

    private static void assertSortOrdersAreEqual(final SAMFileHeader newHeader, final Path inputFile) throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().open(inputFile);
        final SAMFileHeader origHeader = reader.getFileHeader();
        final SAMFileHeader.SortOrder newSortOrder = newHeader.getSortOrder();
        if (newSortOrder != SAMFileHeader.SortOrder.unsorted && newSortOrder != origHeader.getSortOrder()) {
            throw new SAMException("Sort order of new header does not match the original file, needs to be " + origHeader.getSortOrder());
        }
        reader.close();
    }
}
