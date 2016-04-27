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
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Miscellaneous stateless static IO-oriented methods.
 *  Also used for utility methods that wrap or aggregate functionality in Java IO.
 */
public class IOUtil {
    /**
     * @deprecated Use Defaults.NON_ZERO_BUFFER_SIZE instead.
     */
    @Deprecated
    public static final int STANDARD_BUFFER_SIZE = Defaults.NON_ZERO_BUFFER_SIZE;

    public static final long ONE_GB = 1024 * 1024 * 1024;
    public static final long TWO_GBS = 2 * ONE_GB;
    public static final long FIVE_GBS = 5 * ONE_GB;

    /** Possible extensions for VCF files and related formats. */
    public static final String[] VCF_EXTENSIONS = new String[] {".vcf", ".vcf.gz", ".bcf"};

    public static final String INTERVAL_LIST_FILE_EXTENSION = IntervalList.INTERVAL_LIST_FILE_EXTENSION;

    public static final String SAM_FILE_EXTENSION = ".sam";

    public static final String DICT_FILE_EXTENSION = ".dict";

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


    /**
     * @return true if the path is not a device (e.g. /dev/null or /dev/stdin), and is not
     * an existing directory.  I.e. is is a regular path that may correspond to an existing
     * file, or a path that could be a regular output file.
     */
    public static boolean isRegularPath(final File file) {
        return !file.exists() || file.isFile();
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
        assertFileIsReadable(file == null ? null : file.toPath());
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
            throw new SAMException("Cannot read non-existent file: " + path.toAbsolutePath());
        }
        else if (Files.isDirectory(path)) {
            throw new SAMException("Cannot read file because it is a directory: " + path.toAbsolutePath());
        }
        else if (!Files.isReadable(path)) {
            throw new SAMException("File exists but is not readable: " + path.toAbsolutePath());
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
     * Checks that each string is non-null, exists or is a URL, 
     * and if it is a file then not a directory and is readable.  If any
     * condition is false then a runtime exception is thrown.
     *
     * @param files the list of files to check for readability
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
        if (dir == null) {
            throw new IllegalArgumentException("Cannot check readability of null file.");
        }
        else if (!dir.exists()) {
            throw new SAMException("Directory does not exist: " + dir.getAbsolutePath());
        }
        else if (!dir.isDirectory()) {
            throw new SAMException("Cannot write to directory because it is not a directory: " + dir.getAbsolutePath());
        }
        else if (!dir.canWrite()) {
            throw new SAMException("Directory exists but is not writable: " + dir.getAbsolutePath());
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
        try {
            if (f1.length() != f2.length()) {
                throw new SAMException("Files " + f1 + " and " + f2 + " are different lengths.");
            }
            final FileInputStream s1 = new FileInputStream(f1);
            final FileInputStream s2 = new FileInputStream(f2);
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
            s1.close();
            s2.close();
        } catch (IOException e) {
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
        return openFileForReading(file.toPath());
    }

    /**
     * Opens a file for reading, decompressing it if necessary
     *
     * @param path  The file to open
     * @return the input stream to read from
     */
    public static InputStream openFileForReading(final Path path) {

        try {
            if (path.getFileName().toString().endsWith(".gz") ||
                path.getFileName().toString().endsWith(".bfq"))  {
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
        return openGzipFileForReading(file.toPath());
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
        return openFileForWriting(file, false);
    }

    /**
     * Opens a file for writing
     *
     * @param file  the file to write to
     * @param append    whether to append to the file if it already exists (we overwrite it if false)
     * @return the output stream to write to
     */
    public static OutputStream openFileForWriting(final File file, final boolean append) {

        try {
            if (file.getName().endsWith(".gz") ||
                file.getName().endsWith(".bfq")) {
                return openGzipFileForWriting(file, append);
            }
            else {
                return new FileOutputStream(file, append);
            }
        }
        catch (IOException ioe) {
            throw new SAMException("Error opening file for writing: " + file.getName(), ioe);
        }
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
    public static BufferedWriter openFileForBufferedWriting(final File file) {
        return openFileForBufferedWriting(file, false);
    }

    /**
     * Preferred over PrintStream and PrintWriter because an exception is thrown on I/O error
     */
    public static BufferedWriter openFileForBufferedUtf8Writing(final File file) {
        return new BufferedWriter(new OutputStreamWriter(openFileForWriting(file), Charset.forName("UTF-8")),
                Defaults.NON_ZERO_BUFFER_SIZE);
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

        try {
            if (Defaults.BUFFER_SIZE > 0) {
            return new CustomGzipOutputStream(new FileOutputStream(file, append),
                                              Defaults.BUFFER_SIZE,
                                              compressionLevel);
            } else {
                return new CustomGzipOutputStream(new FileOutputStream(file, append), compressionLevel);
            }
        }
        catch (IOException ioe) {
            throw new SAMException("Error opening file for writing: " + file.getName(), ioe);
        }
    }

    public static OutputStream openFileForMd5CalculatingWriting(final File file) {
        return new Md5CalculatingOutputStream(IOUtil.openFileForWriting(file), new File(file.getAbsolutePath() + ".md5"));
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
        return new BufferedReader(new InputStreamReader(openFileForReading(file)), Defaults.NON_ZERO_BUFFER_SIZE);
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
            final LinkedList<String> tokens = new LinkedList<String>();
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
        if (extensions.length < 1) throw new IllegalArgumentException("Must provide at least one extension.");

        final Stack<File> stack = new Stack<File>();
        final List<File> output = new ArrayList<File>();
        stack.addAll(inputs);

        while (!stack.empty()) {
            final File f = stack.pop();
            final String name = f.getName();
            boolean matched = false;

            for (final String ext : extensions) {
                if (!matched && name.endsWith(ext)) {
                    output.add(f);
                    matched = true;
                }
            }

            // If the file didn't match a given extension, treat it as a list of files
            if (!matched) {
                IOUtil.assertFileIsReadable(f);

                for (final String s : IOUtil.readLines(f)) {
                    if (!s.trim().isEmpty()) stack.push(new File(s.trim()));
                }
            }
        }

        // Preserve input order (since we're using a stack above) for things that care
        Collections.reverse(output);

        return output;
    }
}


/**
 * Hacky little class used to allow us to set the compression level on a GZIP output stream which, for some
 * bizarre reason, is not exposed in the standard API.
 *
 * @author Tim Fennell
 */
class CustomGzipOutputStream extends GZIPOutputStream {
    CustomGzipOutputStream(final OutputStream outputStream, final int bufferSize, final int compressionLevel) throws IOException {
        super(outputStream, bufferSize);
        this.def.setLevel(compressionLevel);
    }

    CustomGzipOutputStream(final OutputStream outputStream, final int compressionLevel) throws IOException {
        super(outputStream);
        this.def.setLevel(compressionLevel);
    }
}
