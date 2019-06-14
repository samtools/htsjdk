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
import htsjdk.samtools.SAMException;
import htsjdk.samtools.seekablestream.SeekableBufferedStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableHTTPStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.nio.DeleteOnExitPathHook;
import htsjdk.tribble.Tribble;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * Miscellaneous stateless static IO-oriented methods.
 *  Also used for utility methods that wrap or aggregate functionality in Java IO.
 */
public class IOUtil {
    /**
     * @deprecated Use {@link Defaults#NON_ZERO_BUFFER_SIZE} instead.
     */
    @Deprecated
    public static final int STANDARD_BUFFER_SIZE = Defaults.NON_ZERO_BUFFER_SIZE;

    public static final long ONE_GB = 1024 * 1024 * 1024;
    public static final long TWO_GBS = 2 * ONE_GB;
    public static final long FIVE_GBS = 5 * ONE_GB;

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#VCF} instead.
     */
    @Deprecated
    public static final String VCF_FILE_EXTENSION = FileExtensions.VCF;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#VCF_INDEX} instead.
     */
    @Deprecated
    public static final String VCF_INDEX_EXTENSION = FileExtensions.VCF_INDEX;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#BCF} instead.
     */
    @Deprecated
    public static final String BCF_FILE_EXTENSION = FileExtensions.BCF;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#COMPRESSED_VCF} instead.
     */
    @Deprecated
    public static final String COMPRESSED_VCF_FILE_EXTENSION = FileExtensions.COMPRESSED_VCF;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#COMPRESSED_VCF_INDEX} instead.
     */
    @Deprecated
    public static final String COMPRESSED_VCF_INDEX_EXTENSION = FileExtensions.COMPRESSED_VCF_INDEX;

    /** Possible extensions for VCF files and related formats. */
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#VCF_LIST} instead.
     */
    @Deprecated
    public static final List<String> VCF_EXTENSIONS_LIST = FileExtensions.VCF_LIST;

    /**
     * Possible extensions for VCF files and related formats.
     * @deprecated since June 2019 Use {@link FileExtensions#VCF_ARRAY} instead.
     */
    @Deprecated
    public static final String[] VCF_EXTENSIONS = FileExtensions.VCF_ARRAY;

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#INTERVAL_LIST} instead.
     */
    @Deprecated
    public static final String INTERVAL_LIST_FILE_EXTENSION = FileExtensions.INTERVAL_LIST;

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#SAM} instead.
     */
    @Deprecated
    public static final String SAM_FILE_EXTENSION = FileExtensions.SAM;

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#DICT} instead.
     */
    @Deprecated
    public static final String DICT_FILE_EXTENSION = FileExtensions.DICT;

    /**
     * @deprecated Use since June 2019 {@link FileExtensions#BLOCK_COMPRESSED} instead.
     */
    @Deprecated
    public static final Set<String> BLOCK_COMPRESSED_EXTENSIONS = FileExtensions.BLOCK_COMPRESSED;

    /** number of bytes that will be read for the GZIP-header in the function {@link #isGZIPInputStream(InputStream)} */
    public static final int GZIP_HEADER_READ_LENGTH = 8000;

    private static final OpenOption[] EMPTY_OPEN_OPTIONS = new OpenOption[0];

    private static int compressionLevel = Defaults.COMPRESSION_LEVEL;
    /**
     * Sets the GZip compression level for subsequent GZIPOutputStream object creation.
     * @param compressionLevel 0 <= compressionLevel <= 9
     */
    public static void setCompressionLevel(final int compressionLevel) {
        if (compressionLevel < Deflater.NO_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("Invalid compression level: " + compressionLevel);
        }
        IOUtil.compressionLevel = compressionLevel;
    }

    public static int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Wrap the given stream in a BufferedInputStream, if it isn't already wrapper
     *
     * @param stream stream to be wrapped
     * @return A BufferedInputStream wrapping stream, or stream itself if stream instanceof BufferedInputStream.
     */
    public static BufferedInputStream toBufferedStream(final InputStream stream) {
        if (stream instanceof BufferedInputStream) {
            return (BufferedInputStream) stream;
        } else {
            return new BufferedInputStream(stream, Defaults.NON_ZERO_BUFFER_SIZE);
        }
    }

    /**
     * Transfers from the input stream to the output stream using stream operations and a buffer.
     */
    public static void transferByStream(final InputStream in, final OutputStream out, final long bytes) {
        final byte[] buffer = new byte[Defaults.NON_ZERO_BUFFER_SIZE];
        long remaining = bytes;

        try {
            while (remaining > 0) {
                final int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
        catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }

    /**
     * @return If Defaults.BUFFER_SIZE > 0, wrap os in BufferedOutputStream, else return os itself.
     */
    public static OutputStream maybeBufferOutputStream(final OutputStream os) {
        return maybeBufferOutputStream(os, Defaults.BUFFER_SIZE);
    }

    /**
     * @return If bufferSize > 0, wrap os in BufferedOutputStream, else return os itself.
     */
    public static OutputStream maybeBufferOutputStream(final OutputStream os, final int bufferSize) {
        if (bufferSize > 0) return new BufferedOutputStream(os, bufferSize);
        else return os;
    }

    public static SeekableStream maybeBufferedSeekableStream(final SeekableStream stream, final int bufferSize) {
        return bufferSize > 0 ? new SeekableBufferedStream(stream, bufferSize) : stream; 
    }
    
    public static SeekableStream maybeBufferedSeekableStream(final SeekableStream stream) {
        return maybeBufferedSeekableStream(stream, Defaults.BUFFER_SIZE);
    }
    
    public static SeekableStream maybeBufferedSeekableStream(final File file) {
        try {
            return maybeBufferedSeekableStream(new SeekableFileStream(file));
        } catch (final FileNotFoundException e) {
            throw new RuntimeIOException(e);
        }
    }

    public static SeekableStream maybeBufferedSeekableStream(final URL url) {
        return maybeBufferedSeekableStream(new SeekableHTTPStream(url));
    }

    /**
     * @return If Defaults.BUFFER_SIZE > 0, wrap is in BufferedInputStream, else return is itself.
     */
    public static InputStream maybeBufferInputStream(final InputStream is) {
        return maybeBufferInputStream(is, Defaults.BUFFER_SIZE);
    }

    /**
     * @return If bufferSize > 0, wrap is in BufferedInputStream, else return is itself.
     */
    public static InputStream maybeBufferInputStream(final InputStream is, final int bufferSize) {
        if (bufferSize > 0) return new BufferedInputStream(is, bufferSize);
        else return is;
    }

    public static Reader maybeBufferReader(Reader reader, final int bufferSize) {
        if (bufferSize > 0) reader = new BufferedReader(reader, bufferSize);
        return reader;
    }

    public static Reader maybeBufferReader(final Reader reader) {
        return maybeBufferReader(reader, Defaults.BUFFER_SIZE);
    }

    public static Writer maybeBufferWriter(Writer writer, final int bufferSize) {
        if (bufferSize > 0) writer = new BufferedWriter(writer, bufferSize);
        return writer;
    }

    public static Writer maybeBufferWriter(final Writer writer) {
        return maybeBufferWriter(writer, Defaults.BUFFER_SIZE);
    }


    /**
     * Delete a list of files, and write a warning message if one could not be deleted.
     *
     * @param files Files to be deleted.
     */
    public static void deleteFiles(final File... files) {
        for (final File f : files) {
            if (!f.delete()) {
                System.err.println("Could not delete file " + f);
            }
        }
    }

    public static void deleteFiles(final Iterable<File> files) {
        for (final File f : files) {
            if (!f.delete()) {
                System.err.println("Could not delete file " + f);
            }
        }
    }

    public static void deletePaths(final Path... paths) {
        deletePaths(Arrays.asList(paths));
    }

    public static void deletePaths(final Iterable<Path> paths) {
        for (final Path p : paths) {
            try {
                Files.delete(p);
            } catch (IOException e) {
                System.err.println("Could not delete file " + p);
            }
        }
    }

    /**
     * @return true if the path is not a device (e.g. /dev/null or /dev/stdin), and is not
     * an existing directory.  I.e. is is a regular path that may correspond to an existing
     * file, or a path that could be a regular output file.
     */
    public static boolean isRegularPath(final File file) {
        return !file.exists() || file.isFile();
    }

    /**
     * @return true if the path is not a device (e.g. /dev/null or /dev/stdin), and is not
     * an existing directory.  I.e. is is a regular path that may correspond to an existing
     * file, or a path that could be a regular output file.
     */
    public static boolean isRegularPath(final Path path) {
        return !Files.exists(path) || Files.isRegularFile(path);
    }

    /**
     * Creates a new tmp file on one of the available temp filesystems, registers it for deletion
     * on JVM exit and then returns it.
     */
    public static File newTempFile(final String prefix, final String suffix,
                                   final File[] tmpDirs, final long minBytesFree) throws IOException {
        File f = null;

        for (int i = 0; i < tmpDirs.length; ++i) {
            if (i == tmpDirs.length - 1 || tmpDirs[i].getUsableSpace() > minBytesFree) {
                f = File.createTempFile(prefix, suffix, tmpDirs[i]);
                f.deleteOnExit();
                break;
            }
        }

        return f;
    }

    /** Creates a new tmp file on one of the potential filesystems that has at least 5GB free. */
    public static File newTempFile(final String prefix, final String suffix,
                                   final File[] tmpDirs) throws IOException {
        return newTempFile(prefix, suffix, tmpDirs, FIVE_GBS);
    }

    /** Returns a default tmp directory. */
    public static File getDefaultTmpDir() {
        final String user = System.getProperty("user.name");
        final String tmp = System.getProperty("java.io.tmpdir");

        if (tmp.endsWith(File.separatorChar + user)) return new File(tmp);
        else return new File(tmp, user);
    }

    /**
     * Creates a new tmp path on one of the available temp filesystems, registers it for deletion
     * on JVM exit and then returns it.
     */
    public static Path newTempPath(final String prefix, final String suffix,
            final Path[] tmpDirs, final long minBytesFree) throws IOException {
        Path p = null;

        for (int i = 0; i < tmpDirs.length; ++i) {
            if (i == tmpDirs.length - 1 || Files.getFileStore(tmpDirs[i]).getUsableSpace() > minBytesFree) {
                p = Files.createTempFile(tmpDirs[i], prefix, suffix);
                deleteOnExit(p);
                break;
            }
        }

        return p;
    }

    /** Creates a new tmp file on one of the potential filesystems that has at least 5GB free. */
    public static Path newTempPath(final String prefix, final String suffix,
            final Path[] tmpDirs) throws IOException {
        return newTempPath(prefix, suffix, tmpDirs, FIVE_GBS);
    }

    /** Returns a default tmp directory as a Path. */
    public static Path getDefaultTmpDirPath() {
        try {
            final String user = System.getProperty("user.name");
            final String tmp = System.getProperty("java.io.tmpdir");

            final Path tmpParent = getPath(tmp);
            if (tmpParent.endsWith(tmpParent.getFileSystem().getSeparator() + user)) {
                return tmpParent;
            } else {
                return tmpParent.resolve(user);
            }
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Register a {@link Path} for deletion on JVM exit.
     *
     * @see DeleteOnExitPathHook
     */
    public static void deleteOnExit(final Path path) {
        DeleteOnExitPathHook.add(path);
    }

    /** Returns the name of the file minus the extension (i.e. text after the last "." in the filename). */
    public static String basename(final File f) {
        final String full = f.getName();
        final int index = full.lastIndexOf('.');
        if (index > 0  && index > full.lastIndexOf(File.separator)) {
            return full.substring(0, index);
        }
        else {
            return full;
        }
    }
    
    /**
     * Checks that an input is  is non-null, a URL or a file, exists, 
     * and if its a file then it is not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param input the input to check for validity
     */
    public static void assertInputIsValid(final String input) {
      if (input == null) {
        throw new IllegalArgumentException("Cannot check validity of null input.");
      }
      if (!isUrl(input)) {
        assertFileIsReadable(new File(input));
      }
    }
    
    /** 
     * Returns true iff the string is a url. 
     * Helps distinguish url inputs form file path inputs.
     */
    public static boolean isUrl(final String input) {
      try {
        new URL(input);
        return true;
      } catch (MalformedURLException e) {
        return false;
      }
    }

    /**
     * Checks that a file is non-null, exists, is not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param file the file to check for readability
     */
    public static void assertFileIsReadable(final File file) {
        assertFileIsReadable(toPath(file));
    }

    /**
     * Checks that a file is non-null, exists, is not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param path the file to check for readability
     */
    public static void assertFileIsReadable(final Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Cannot check readability of null file.");
        } else if (!Files.exists(path)) {
            throw new SAMException("Cannot read non-existent file: " + path.toUri().toString());
        }
        else if (Files.isDirectory(path)) {
            throw new SAMException("Cannot read file because it is a directory: " + path.toUri().toString());
        }
        else if (!Files.isReadable(path)) {
            throw new SAMException("File exists but is not readable: " + path.toUri().toString());
        }
    }

    /**
     * Checks that each file is non-null, exists, is not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param files the list of files to check for readability
     */
    public static void assertFilesAreReadable(final List<File> files) {
        for (final File file : files) assertFileIsReadable(file);
    }

    /**
     * Checks that each path is non-null, exists, is not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param paths the list of paths to check for readability
     */
    public static void assertPathsAreReadable(final List<Path> paths) {
        for (final Path path: paths) assertFileIsReadable(path);
    }


    /**
     * Checks that each string is non-null, exists or is a URL, 
     * and if it is a file then not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param inputs the list of files to check for readability
     */
    public static void assertInputsAreValid(final List<String> inputs) {
        for (final String input : inputs) assertInputIsValid(input);
    }

    /**
     * Checks that a file is non-null, and is either extent and writable, or non-existent but
     * that the parent directory exists and is writable. If any
     * condition is false then a runtime exception is thrown.
     *
     * @param file the file to check for writability
     */
    public static void assertFileIsWritable(final File file) {
        if (file == null) {
            throw new IllegalArgumentException("Cannot check readability of null file.");
        } else if (!file.exists()) {
            // If the file doesn't exist, check that it's parent directory does and is writable
            final File parent = file.getAbsoluteFile().getParentFile();
            if (!parent.exists()) {
                throw new SAMException("Cannot write file: " + file.getAbsolutePath() + ". " +
                        "Neither file nor parent directory exist.");
            }
            else if (!parent.isDirectory()) {
                throw new SAMException("Cannot write file: " + file.getAbsolutePath() + ". " +
                        "File does not exist and parent is not a directory.");
            }
            else if (!parent.canWrite()) {
                throw new SAMException("Cannot write file: " + file.getAbsolutePath() + ". " +
                        "File does not exist and parent directory is not writable..");
            }
        }
        else if (file.isDirectory()) {
            throw new SAMException("Cannot write file because it is a directory: " + file.getAbsolutePath());
        }
        else if (!file.canWrite()) {
            throw new SAMException("File exists but is not writable: " + file.getAbsolutePath());
        }
    }

    /**
     * Checks that each file is non-null, and is either extent and writable, or non-existent but
     * that the parent directory exists and is writable. If any
     * condition is false then a runtime exception is thrown.
     *
     * @param files the list of files to check for writability
     */
    public static void assertFilesAreWritable(final List<File> files) {
        for (final File file : files) assertFileIsWritable(file);
    }

    /**
     * Checks that a directory is non-null, extent, writable and a directory
     * otherwise a runtime exception is thrown.
     *
     * @param dir the dir to check for writability
     */
    public static void assertDirectoryIsWritable(final File dir) {
        final Path asPath = IOUtil.toPath(dir);
        assertDirectoryIsWritable(asPath);
    }

    /**
     * Checks that a directory is non-null, extent, writable and a directory
     * otherwise a runtime exception is thrown.
     *
     * @param dir the dir to check for writability
     */
    public static void assertDirectoryIsWritable(final Path dir) {
        if (dir == null) {
            throw new IllegalArgumentException("Cannot check readability of null file.");
        }
        else if (!Files.exists(dir)) {
            throw new SAMException("Directory does not exist: " + dir.toUri().toString());
        }
        else if (!Files.isDirectory(dir)) {
            throw new SAMException("Cannot write to directory because it is not a directory: " + dir.toUri().toString());
        }
        else if (!Files.isWritable(dir)) {
            throw new SAMException("Directory exists but is not writable: " + dir.toUri().toString());
        }
    }

    /**
     * Checks that a directory is non-null, extent, readable and a directory
     * otherwise a runtime exception is thrown.
     *
     * @param dir the dir to check for writability
     */
    public static void assertDirectoryIsReadable(final File dir) {
        if (dir == null) {
            throw new IllegalArgumentException("Cannot check readability of null file.");
        }
        else if (!dir.exists()) {
            throw new SAMException("Directory does not exist: " + dir.getAbsolutePath());
        }
        else if (!dir.isDirectory()) {
            throw new SAMException("Cannot read from directory because it is not a directory: " + dir.getAbsolutePath());
        }
        else if (!dir.canRead()) {
            throw new SAMException("Directory exists but is not readable: " + dir.getAbsolutePath());
        }
    }

    /**
     * Checks that the two files are the same length, and have the same content, otherwise throws a runtime exception.
     */
    public static void assertFilesEqual(final File f1, final File f2) {
        if (f1.length() != f2.length()) {
            throw new SAMException("File " + f1 + " is " + f1.length() + " bytes but file " + f2 + " is " + f2.length() + " bytes.");
        }
        try (
            final FileInputStream s1 = new FileInputStream(f1);
            final FileInputStream s2 = new FileInputStream(f2);
            ) {
            final byte[] buf1 = new byte[1024 * 1024];
            final byte[] buf2 = new byte[1024 * 1024];
            int len1;
            while ((len1 = s1.read(buf1)) != -1) {
                final int len2 = s2.read(buf2);
                if (len1 != len2) {
                    throw new SAMException("Unexpected EOF comparing files that are supposed to be the same length.");
                }
                if (!Arrays.equals(buf1, buf2)) {
                    throw new SAMException("Files " + f1 + " and " + f2 + " differ.");
                }
            }
        } catch (final IOException e) {
            throw new SAMException("Exception comparing files " + f1 + " and " + f2, e);
        }
    }

    /**
     * Checks that a file is of non-zero length
     */
    public static void assertFileSizeNonZero(final File file) {
        if (file.length() == 0) {
            throw new SAMException(file.getAbsolutePath() + " has length 0");
        }
    }

    /**
     * Opens a file for reading, decompressing it if necessary
     *
     * @param file  The file to open
     * @return the input stream to read from
     */
    public static InputStream openFileForReading(final File file) {
        return openFileForReading(toPath(file));
    }

    /**
     * Opens a file for reading, decompressing it if necessary
     *
     * @param path  The file to open
     * @return the input stream to read from
     */
    public static InputStream openFileForReading(final Path path) {

        try {
            if (hasGzipFileExtension(path))  {
                return openGzipFileForReading(path);
            }
            else {
                return Files.newInputStream(path);
            }
        }
        catch (IOException ioe) {
            throw new SAMException("Error opening file: " + path, ioe);
        }

    }

    /**
     * Opens a GZIP-encoded file for reading, decompressing it if necessary
     *
     * @param file  The file to open
     * @return the input stream to read from
     */
    public static InputStream openGzipFileForReading(final File file) {
        return openGzipFileForReading(toPath(file));
    }

    /**
     * Opens a GZIP-encoded file for reading, decompressing it if necessary
     *
     * @param path  The file to open
     * @return the input stream to read from
     */
    public static InputStream openGzipFileForReading(final Path path) {

        try {
            return new GZIPInputStream(Files.newInputStream(path));
        }
        catch (IOException ioe) {
            throw new SAMException("Error opening file: " + path, ioe);
        }
    }

    /**
     * Opens a file for writing, overwriting the file if it already exists
     *
     * @param file  the file to write to
     * @return the output stream to write to
     */
    public static OutputStream openFileForWriting(final File file) {
        return openFileForWriting(toPath(file));
    }

    /**
     * Opens a file for writing, gzip it if it ends with ".gz" or "bfq"
     *
     * @param file  the file to write to
     * @param append    whether to append to the file if it already exists (we overwrite it if false)
     * @return the output stream to write to
     */
    public static OutputStream openFileForWriting(final File file, final boolean append) {
        return openFileForWriting(toPath(file), getAppendOpenOption(append));
    }

    /**
     * Opens a file for writing, gzip it if it ends with ".gz" or "bfq"
     *
     * @param path  the file to write to
     * @param openOptions options to use when opening the file
     * @return the output stream to write to
     */
    public static OutputStream openFileForWriting(final Path path, OpenOption... openOptions) {
        try {
            if (hasGzipFileExtension(path)) {
                return openGzipFileForWriting(path, openOptions);
            } else {
                return Files.newOutputStream(path, openOptions);
            }
        } catch (final IOException ioe) {
            throw new SAMException("Error opening file for writing: " + path.toUri().toString(), ioe);
        }
    }

    /**
     * check if the file name ends with .gz, .gzip, or .bfq
     */
    public static boolean hasGzipFileExtension(Path path) {
        final List<String> gzippedEndings = Arrays.asList(".gz", ".gzip", ".bfq");
        final String fileName = path.getFileName().toString();
        return gzippedEndings.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedWriting(final File file, final boolean append) {
        return new BufferedWriter(new OutputStreamWriter(openFileForWriting(file, append)), Defaults.NON_ZERO_BUFFER_SIZE);
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedWriting(final Path path, final OpenOption ... openOptions) {
        return new BufferedWriter(new OutputStreamWriter(openFileForWriting(path, openOptions)), Defaults.NON_ZERO_BUFFER_SIZE);
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedWriting(final File file) {
        return openFileForBufferedWriting(IOUtil.toPath(file));
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedUtf8Writing(final File file) {
        return openFileForBufferedUtf8Writing(IOUtil.toPath(file));
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedUtf8Writing(final Path path) {
        return new BufferedWriter(new OutputStreamWriter(openFileForWriting(path), Charset.forName("UTF-8")), Defaults.NON_ZERO_BUFFER_SIZE);
    }

    /**
     * Opens a file for reading, decompressing it if necessary
     *
     * @param file  The file to open
     * @return the input stream to read from
     */
    public static BufferedReader openFileForBufferedUtf8Reading(final File file) {
        return new BufferedReader(new InputStreamReader(openFileForReading(file), Charset.forName("UTF-8")));
    }

    /**
     * Opens a GZIP encoded file for writing
     *
     * @param file  the file to write to
     * @param append    whether to append to the file if it already exists (we overwrite it if false)
     * @return the output stream to write to
     */
    public static OutputStream openGzipFileForWriting(final File file, final boolean append) {
        return openGzipFileForWriting(IOUtil.toPath(file), getAppendOpenOption(append));
    }

    /**
     * converts a boolean into an array containing either the append option or nothing
     */
    private static OpenOption[] getAppendOpenOption(boolean append) {
        return append ? new OpenOption[]{StandardOpenOption.APPEND} : EMPTY_OPEN_OPTIONS;
    }

    /**
     * Opens a GZIP encoded file for writing
     *
     * @param path the file to write to
     * @param openOptions options to control how the file is opened
     * @return the output stream to write to
     */
    public static OutputStream openGzipFileForWriting(final Path path, final OpenOption ... openOptions) {
        try {
            final OutputStream out = Files.newOutputStream(path, openOptions);
            if (Defaults.BUFFER_SIZE > 0) {
                return new CustomGzipOutputStream(out, Defaults.BUFFER_SIZE, compressionLevel);
            } else {
                return new CustomGzipOutputStream(out, compressionLevel);
            }
        } catch (final IOException ioe) {
            throw new SAMException("Error opening file for writing: " + path.toUri().toString(), ioe);
        }
    }

    public static OutputStream openFileForMd5CalculatingWriting(final File file) {
        return openFileForMd5CalculatingWriting(toPath(file));
    }

    public static OutputStream openFileForMd5CalculatingWriting(final Path file) {
        return new Md5CalculatingOutputStream(IOUtil.openFileForWriting(file), file.resolve(".md5"));
    }

    /**
     * Utility method to copy the contents of input to output. The caller is responsible for
     * opening and closing both streams.
     *
     * @param input contents to be copied
     * @param output destination
     */
    public static void copyStream(final InputStream input, final OutputStream output) {
        try {
            final byte[] buffer = new byte[Defaults.NON_ZERO_BUFFER_SIZE];
            int bytesRead = 0;
            while((bytesRead = input.read(buffer)) > 0) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new SAMException("Exception copying stream", e);
        }
    }

    /**
     * Copy input to output, overwriting output if it already exists.
     */
    public static void copyFile(final File input, final File output) {
        try {
            final InputStream is = new FileInputStream(input);
            final OutputStream os = new FileOutputStream(output);
            copyStream(is, os);
            os.close();
            is.close();
        } catch (IOException e) {
            throw new SAMException("Error copying " + input + " to " + output, e);
        }
    }

    /**
     *
     * @param directory
     * @param regexp
     * @return list of files matching regexp.
     */
    public static File[] getFilesMatchingRegexp(final File directory, final String regexp) {
        final Pattern pattern = Pattern.compile(regexp);
        return getFilesMatchingRegexp(directory, pattern);
    }

    public static File[] getFilesMatchingRegexp(final File directory, final Pattern regexp) {
        return directory.listFiles( new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return regexp.matcher(name).matches();
            }
        });
    }

    /**
     * Delete the given file or directory.  If a directory, all enclosing files and subdirs are also deleted.
     */
    public static boolean deleteDirectoryTree(final File fileOrDirectory) {
        boolean success = true;

        if (fileOrDirectory.isDirectory()) {
            for (final File child : fileOrDirectory.listFiles()) {
                success = success && deleteDirectoryTree(child);
            }
        }

        success = success && fileOrDirectory.delete();
        return success;
    }

    /**
     * Returns the size (in bytes) of the file or directory and all it's children.
     */
    public static long sizeOfTree(final File fileOrDirectory) {
        long total = fileOrDirectory.length();
        if (fileOrDirectory.isDirectory()) {
            for (final File f : fileOrDirectory.listFiles()) {
                total += sizeOfTree(f);
            }
        }

        return total;
    }

    /**
     *
     * Copies a directory tree (all subdirectories and files) recursively to a destination
     */
    public static void copyDirectoryTree(final File fileOrDirectory, final File destination) {
        if (fileOrDirectory.isDirectory()) {
            destination.mkdir();
            for(final File f : fileOrDirectory.listFiles()) {
                final File destinationFileOrDirectory =  new File(destination.getPath(),f.getName());
                if (f.isDirectory()){
                    copyDirectoryTree(f,destinationFileOrDirectory);
                }
                else {
                    copyFile(f,destinationFileOrDirectory);
                }
            }
        }
    }

    /**
     * Create a temporary subdirectory in the default temporary-file directory, using the given prefix and suffix to generate the name.
     * Note that this method is not completely safe, because it create a temporary file, deletes it, and then creates
     * a directory with the same name as the file.  Should be good enough.
     *
     * @param prefix The prefix string to be used in generating the file's name; must be at least three characters long
     * @param suffix The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used
     * @return File object for new directory
     */
    public static File createTempDir(final String prefix, final String suffix) {
        try {
            final File tmp = File.createTempFile(prefix, suffix);
            if (!tmp.delete()) {
                throw new SAMException("Could not delete temporary file " + tmp);
            }
            if (!tmp.mkdir()) {
                throw new SAMException("Could not create temporary directory " + tmp);
            }
            return tmp;
        } catch (IOException e) {
            throw new SAMException("Exception creating temporary directory.", e);
        }
    }

    /** Checks that a file exists and is readable, and then returns a buffered reader for it. */
    public static BufferedReader openFileForBufferedReading(final File file) {
        return openFileForBufferedReading(toPath(file));
    }

    /** Checks that a path exists and is readable, and then returns a buffered reader for it. */
    public static BufferedReader openFileForBufferedReading(final Path path) {
        return new BufferedReader(new InputStreamReader(openFileForReading(path)), Defaults.NON_ZERO_BUFFER_SIZE);
    }

    /** Takes a string and replaces any characters that are not safe for filenames with an underscore */
    public static String makeFileNameSafe(final String str) {
        return str.trim().replaceAll("[\\s!\"#$%&'()*/:;<=>?@\\[\\]\\\\^`{|}~]", "_");
    }

    /** Returns the name of the file extension (i.e. text after the last "." in the filename) including the . */
    public static String fileSuffix(final File f) {
        final String full = f.getName();
        final int index = full.lastIndexOf('.');
        if (index > 0 && index > full.lastIndexOf(File.separator)) {
            return full.substring(index);
        } else {
            return null;
        }
    }

    /** Returns the full path to the file with all symbolic links resolved **/
    public static String getFullCanonicalPath(final File file) {
        try {
            File f = file.getCanonicalFile();
            String canonicalPath = "";
            while (f != null  && !f.getName().equals("")) {
                canonicalPath = "/" + f.getName() + canonicalPath;
                f = f.getParentFile();
                if (f != null) f = f.getCanonicalFile();
            }
            return canonicalPath;
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error getting full canonical path for " +
                    file + ": " + ioe.getMessage(), ioe);
        }
   }

    /**
     * Reads everything from an input stream as characters and returns a single String.
     */
    public static String readFully(final InputStream in) {
        try {
            final BufferedReader r = new BufferedReader(new InputStreamReader(in), Defaults.NON_ZERO_BUFFER_SIZE);
            final StringBuilder builder = new StringBuilder(512);
            String line = null;

            while ((line = r.readLine()) != null) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(line);
            }

            return builder.toString();
        }
        catch (final IOException ioe) {
            throw new RuntimeIOException("Error reading stream", ioe);
        }
    }

    /**
     * Returns an iterator over the lines in a text file. The underlying resources are automatically
     * closed when the iterator hits the end of the input, or manually by calling close().
     *
     * @param f a file that is to be read in as text
     * @return an iterator over the lines in the text file
     */
    public static IterableOnceIterator<String> readLines(final File f) {
        try {
            final BufferedReader in = IOUtil.openFileForBufferedReading(f);

            return new IterableOnceIterator<String>() {
                private String next = in.readLine();

                /** Returns true if there is another line to read or false otherwise. */
                @Override public boolean hasNext() { return next != null; }

                /** Returns the next line in the file or null if there are no more lines. */
                @Override public String next() {
                    try {
                        final String tmp = next;
                        next = in.readLine();
                        if (next == null) in.close();
                        return tmp;
                    }
                    catch (final IOException ioe) { throw new RuntimeIOException(ioe); }
                }

                /** Closes the underlying input stream. Not required if end of stream has already been hit. */
                @Override public void close() throws IOException { CloserUtil.close(in); }
            };
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /** Returns all of the untrimmed lines in the provided file. */
    public static List<String> slurpLines(final File file) throws FileNotFoundException {
        return slurpLines(new FileInputStream(file));
    }

    public static List<String> slurpLines(final InputStream is) throws FileNotFoundException {
        /** See {@link java.util.Scanner} source for origin of delimiter used here.  */
        return tokenSlurp(is, Charset.defaultCharset(), "\r\n|[\n\r\u2028\u2029\u0085]");
    }

    /** Convenience overload for {@link #slurp(java.io.InputStream, java.nio.charset.Charset)} using the default charset {@link java.nio.charset.Charset#defaultCharset()}. */
    public static String slurp(final File file) throws FileNotFoundException {
        return slurp(new FileInputStream(file));
    }

    /** Convenience overload for {@link #slurp(java.io.InputStream, java.nio.charset.Charset)} using the default charset {@link java.nio.charset.Charset#defaultCharset()}. */
    public static String slurp(final InputStream is) {
        return slurp(is, Charset.defaultCharset());
    }

    /** Reads all of the stream into a String, decoding with the provided {@link java.nio.charset.Charset} then closes the stream quietly. */
    public static String slurp(final InputStream is, final Charset charSet) {
        final List<String> tokenOrEmpty = tokenSlurp(is, charSet, "\\A");
        return tokenOrEmpty.isEmpty() ? StringUtil.EMPTY_STRING : CollectionUtil.getSoleElement(tokenOrEmpty);
    }

    /** Tokenizes the provided input stream into memory using the given delimiter. */
    private static List<String> tokenSlurp(final InputStream is, final Charset charSet, final String delimiterPattern) {
        try {
            final Scanner s = new Scanner(is, charSet.toString()).useDelimiter(delimiterPattern);
            final LinkedList<String> tokens = new LinkedList<>();
            while (s.hasNext()) {
                tokens.add(s.next());
            }
            return tokens;
        } finally {
            CloserUtil.close(is);
        }
    }

    /**
     * Go through the files provided and if they have one of the provided file extensions pass the file into the output
     * otherwise assume that file is a list of filenames and unfold it into the output.
     */
    public static List<File> unrollFiles(final Collection<File> inputs, final String... extensions) {
        Collection<Path> paths = unrollPaths(filesToPaths(inputs), extensions);
        return paths.stream().map(Path::toFile).collect(Collectors.toList());
    }

    /**
     * Go through the files provided and if they have one of the provided file extensions pass the file to the output
     * otherwise assume that file is a list of filenames and unfold it into the output (recursively).
     */
    public static List<Path> unrollPaths(final Collection<Path> inputs, final String... extensions) {
        if (extensions.length < 1) throw new IllegalArgumentException("Must provide at least one extension.");

        final Stack<Path> stack = new Stack<>();
        final List<Path> output = new ArrayList<>();
        stack.addAll(inputs);

        while (!stack.empty()) {
            final Path p = stack.pop();
            final String name = p.toString();
            boolean matched = false;

            for (final String ext : extensions) {
                if (!matched && name.endsWith(ext)) {
                    output.add(p);
                    matched = true;
                }
            }

            // If the file didn't match a given extension, treat it as a list of files
            if (!matched) {
                try {
                    Files.lines(p)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(s -> {
                                        final Path innerPath;
                                        try {
                                            innerPath = getPath(s);
                                            stack.push(innerPath);
                                        } catch (IOException e) {
                                            throw new IllegalArgumentException("cannot convert " + s + " to a Path.", e);
                                        }
                                    }
                            );

                } catch (IOException e) {
                    throw new IllegalArgumentException("had trouble reading from " + p.toUri().toString(), e);
                }
            }
        }

        // Preserve input order (since we're using a stack above) for things that care
        Collections.reverse(output);

        return output;
    }


    /**
     * Check if the given URI has a scheme.
     *
     * @param uriString the URI to check
     * @return <code>true</code> if the given URI has a scheme, <code>false</code> if
     * not, or if the URI is malformed.
     */
    public static boolean hasScheme(String uriString) {
        try {
            return new URI(uriString).getScheme() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Converts the given URI to a {@link Path} object. If the filesystem cannot be found in the usual way, then attempt
     * to load the filesystem provider using the thread context classloader. This is needed when the filesystem
     * provider is loaded using a URL classloader (e.g. in spark-submit).
     *
     * @param uriString the URI to convert
     * @return the resulting {@code Path}
     * @throws IOException an I/O error occurs creating the file system
     */
    public static Path getPath(String uriString) throws IOException {
        URI uri = URI.create(uriString);
        try {
            // if the URI has no scheme, then treat as a local file, otherwise use the scheme to determine the filesystem to use
            return uri.getScheme() == null ? Paths.get(uriString) : Paths.get(uri);
        } catch (FileSystemNotFoundException e) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                throw e;
            }
            return FileSystems.newFileSystem(uri, new HashMap<>(), cl).provider().getPath(uri);
        }
    }

    public static List<Path> getPaths(List<String> uriStrings) throws RuntimeIOException {
        return uriStrings.stream().map(s -> {
            try {
                return IOUtil.getPath(s);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }).collect(Collectors.toList());
    }

    /*
     * Converts the File to a Path, preserving nullness.
     *
     * @param fileOrNull a File, or null
     * @return           the corresponding Path (or null)
     */
    public static Path toPath(File fileOrNull) {
        return (null == fileOrNull ? null : fileOrNull.toPath());
    }

    /** Takes a list of Files and converts them to a list of Paths
     * Runs .toPath() on the contents of the input.
     *
     * @param files a {@link List} of {@link File}s to convert to {@link Path}s
     * @return a new List containing the results of running toPath on the elements of the input
     */
    public static List<Path> filesToPaths(Collection<File> files){
        return files.stream().map(File::toPath).collect(Collectors.toList());
    }

    /**
     * Test whether a input stream looks like a GZIP input.
     * This identifies both gzip and bgzip streams as being GZIP.
     * @param stream the input stream.
     * @return true if `stream` starts with a gzip signature.
     * @throws IllegalArgumentException if `stream` cannot mark or reset the stream
     */
    public static boolean isGZIPInputStream(final InputStream stream) {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("isGZIPInputStream() : Cannot test a stream that doesn't support marking.");
        }
        stream.mark(GZIP_HEADER_READ_LENGTH);

        try {
            final GZIPInputStream gunzip = new GZIPInputStream(stream);
            final int ch = gunzip.read();
            return true;
        } catch (final IOException ioe) {
            return false;
        } finally {
            try {
                stream.reset();
            } catch (final IOException ioe) {
                throw new IllegalStateException("isGZIPInputStream(): Could not reset stream.");
            }
        }
    }

    /**
     * Adds the extension to the given path.
     *
     * @param path       the path to start from, eg. "/folder/file.jpg"
     * @param extension  the extension to add, eg. ".bak"
     * @return           "/folder/file.jpg.bak"
     */
    public static Path addExtension(Path path, String extension) {
        return path.resolveSibling(path.getFileName() + extension);
    }

    /**
     * Checks if the provided path is block-compressed.
     *
     * <p>Note that using {@code checkExtension=true} would avoid the cost of opening the file, but
     * if {@link #hasBlockCompressedExtension(String)} returns {@code false} this would not detect
     * block-compressed files such BAM.
     *
     * @param path file to check if it is block-compressed.
     * @param checkExtension if {@code true}, checks the extension before opening the file.
     * @return {@code true} if the file is block-compressed; {@code false} otherwise.
     * @throws IOException if there is an I/O error.
     */
    public static boolean isBlockCompressed(final Path path, final boolean checkExtension) throws IOException {
        if (checkExtension && !hasBlockCompressedExtension(path)) {
            return false;
        }
        try (final InputStream stream = new BufferedInputStream(Files.newInputStream(path), Math.max(Defaults.BUFFER_SIZE, BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE))) {
            return BlockCompressedInputStream.isValidFile(stream);
        }
    }

    /**
     * Checks if the provided path is block-compressed (including extension).
     *
     * <p>Note that block-compressed file extensions {@link FileExtensions#BLOCK_COMPRESSED} are not
     * checked by this method.
     *
     * @param path file to check if it is block-compressed.
     * @return {@code true} if the file is block-compressed; {@code false} otherwise.
     * @throws IOException if there is an I/O error.
     */
    public static boolean isBlockCompressed(final Path path) throws IOException {
        return isBlockCompressed(path, false);
    }

    /**
     * Checks if a file ends in one of the {@link FileExtensions#BLOCK_COMPRESSED}.
     *
     * @param fileName string name for the file. May be an HTTP/S url.
     *
     * @return {@code true} if the file has a block-compressed extension; {@code false} otherwise.
     */
    public static boolean hasBlockCompressedExtension (final String fileName) {
        String cleanedPath = stripQueryStringIfPathIsAnHttpUrl(fileName);
        for (final String extension : FileExtensions.BLOCK_COMPRESSED) {
            if (cleanedPath.toLowerCase().endsWith(extension))
                return true;
        }
        return false;
    }

    /**
     * Checks if a path ends in one of the {@link FileExtensions#BLOCK_COMPRESSED}.
     *
     * @param path object to extract the name from.
     *
     * @return {@code true} if the path has a block-compressed extension; {@code false} otherwise.
     */
    public static boolean hasBlockCompressedExtension(final Path path) {
        return hasBlockCompressedExtension(path.getFileName().toString());
    }

    /**
     * Checks if a file ends in one of the {@link FileExtensions#BLOCK_COMPRESSED}.
     *
     * @param file object to extract the name from.
     *
     * @return {@code true} if the file has a block-compressed extension; {@code false} otherwise.
     */
    public static boolean hasBlockCompressedExtension (final File file) {
        return hasBlockCompressedExtension(file.getName());
    }

    /**
     * Checks if a file ends in one of the {@link FileExtensions#BLOCK_COMPRESSED}.
     *
     * @param uri file as an URI.
     *
     * @return {@code true} if the file has a block-compressed extension; {@code false} otherwise.
     */
    public static boolean hasBlockCompressedExtension (final URI uri) {
        String path = uri.getPath();
        return hasBlockCompressedExtension(path);
    }

    /**
     * Remove http query before checking extension
     * Path might be a local file, in which case a '?' is a legal part of the filename.
     * @param path a string representing some sort of path, potentially an http url
     * @return path with no trailing queryString (ex: http://something.com/path.vcf?stuff=something => http://something.com/path.vcf)
     */
    private static String stripQueryStringIfPathIsAnHttpUrl(String path) {
        if(path.startsWith("http://") || path.startsWith("https://")) {
            int qIdx = path.indexOf('?');
            if (qIdx > 0) {
                return path.substring(0, qIdx);
            }
        }
        return path;
    }

    /**
     * Delete a directory and all files in it.
     *
     * @param directory The directory to be deleted (along with its subdirectories)
     */
    public static void recursiveDelete(final Path directory) {
        
        final SimpleFileVisitor<Path> simpleFileVisitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                super.visitFile(file, attrs);
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                super.postVisitDirectory(dir, exc);
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        };

        try {
            Files.walkFileTree(directory, simpleFileVisitor);
        } catch (final IOException e){
            throw new RuntimeIOException(e);
        }
    }
}
