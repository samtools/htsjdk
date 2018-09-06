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

package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.sra.SRAAccession;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Lazy;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Describes a SAM-like resource, including its data (where the records are), and optionally an index.
 * <p/>
 * A data or index source may originate from a {@link java.io.File}, {@link java.io.InputStream}, {@link URL}, or
 * {@link htsjdk.samtools.seekablestream.SeekableStream}; look for the appropriate overload for
 * {@code htsjdk.samtools.SamInputResource#of()}.
 *
 * @author mccowan
 */
public class SamInputResource {
    private final InputResource source;
    private InputResource index;

    SamInputResource(final InputResource data) {
        this(data, null);
    }

    SamInputResource(final InputResource source, final InputResource index) {
        if (source == null) throw new NullPointerException("source");
        this.source = source;
        this.index = index;
    }

    /** The resource that is the SAM data (e.g., records) */
    InputResource data() {
        return source;
    }

    /**
     * The resource that is the SAM index
     *
     * @return null, if no index is defined for this resource
     */
    InputResource indexMaybe() {
        return index;
    }

    @Override
    public String toString() {
        return String.format("data=%s;index=%s", source, index);
    }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final File file) {
        return new SamInputResource(new FileInputResource(file));
    }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final Path path) {

        // in the case of named pipes and other non-seekable paths there's a bug in the implementation of
        // java's GZIPInputStream which inappropriately uses .available() and then gets confused with the result
        // of 0. For reference see:
        // https://bugs.java.com/view_bug.do?bug_id=7036144
        // https://github.com/samtools/htsjdk/pull/1077
        // https://github.com/samtools/htsjdk/issues/898

        // This still doesn't support the case where someone is creating a named pipe in a non-default
        // file system and then using it as input and passing a GZIPed into the other end of the pipe.

        // To work around this bug, we fall back to using a FileInputResource rather than a PathInputResource
        // when we encounter a non-regular file using the default NIO filesystem (file://)
        if (path.getFileSystem() == FileSystems.getDefault() && !Files.isRegularFile(path)) {
            return of(path.toFile());
        } else {
            return new SamInputResource(new PathInputResource(path));
        }
    }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index,
     *  and with a wrapper to apply to the SeekableByteChannel for custom prefetching/buffering. */
    public static SamInputResource of(final Path path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) {
        return new SamInputResource(new PathInputResource(path, wrapper));
    }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final InputStream inputStream) { return new SamInputResource(new InputStreamInputResource(inputStream)); }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final URL url) { return new SamInputResource(new UrlInputResource(url)); }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final SeekableStream seekableStream) { return new SamInputResource(new SeekableStreamInputResource(seekableStream)); }

    public static SamInputResource of(final SRAAccession acc) { return new SamInputResource(new SRAInputResource(acc)); }

    /** Creates a {@link SamInputResource} from a string specifying *either* a url or a file path */
    public static SamInputResource of(final String string) { 
      try {
        URL url = new URL(string);    // this will throw if its not a url
        return of(url); 
      } catch (MalformedURLException e) {
       // ignore
      }
      return of(new File(string));
    }
    
    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final File file) {
        this.index = new FileInputResource(file);
        return this;
    }

    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final Path path) {
        this.index = new PathInputResource(path);
        return this;
    }

    /** Updates the index to point at the provided resource, with the provided wrapper, then returns itself. */
    public SamInputResource index(final Path path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) {
        this.index = new PathInputResource(path, wrapper);
        return this;
    }

    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final InputStream inputStream) {
        this.index = new InputStreamInputResource(inputStream);
        return this;
    }

    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final URL url) {
        this.index = new UrlInputResource(url);
        return this;
    }

    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final SeekableStream seekableStream) {
        this.index = new SeekableStreamInputResource(seekableStream);
        return this;
    }

}

/**
 * Describes an arbitrary input source, which is something that can be accessed as either a
 * {@link htsjdk.samtools.seekablestream.SeekableStream} or {@link java.io.InputStream}.  A concrete implementation of this class exists for
 * each of {@link InputResource.Type}.
 */
abstract class InputResource {
    protected InputResource(final Type type) {this.type = type;}

    enum Type {
        FILE, PATH, URL, SEEKABLE_STREAM, INPUT_STREAM, SRA_ACCESSION
    }

    private final Type type;

    final Type type() {
        return type;
    }

    /** Returns null if this resource cannot be represented as a {@link File}. */
    abstract File asFile();

    /** Returns null if this resource cannot be represented as a {@link Path}. */
    abstract Path asPath();

    /** Returns null if this resource cannot be represented as a {@link URL}. */
    abstract URL asUrl();

    /** Returns null if this resource cannot be represented as a {@link htsjdk.samtools.seekablestream.SeekableStream}. */
    abstract SeekableStream asUnbufferedSeekableStream();

    /** All resource types support {@link java.io.InputStream} generation. */
    abstract InputStream asUnbufferedInputStream();

    /** SRA archive resource */
    abstract SRAAccession asSRAAccession();

    @Override
    public String toString() {
        final String childToString;
        switch (type()) {
            case FILE:
                childToString = asFile().toString();
                break;
            case PATH:
                childToString = asPath().toString();
                break;
            case INPUT_STREAM:
                childToString = asUnbufferedInputStream().toString();
                break;
            case SEEKABLE_STREAM:
                childToString = asUnbufferedSeekableStream().toString();
                break;
            case URL:
                childToString = asUrl().toString();
                break;
            case SRA_ACCESSION:
                childToString = asSRAAccession().toString();
                break;
            default:
                throw new IllegalStateException();
        }
        return String.format("%s:%s", type(), childToString);
    }
}

class FileInputResource extends InputResource {

    final File fileResource;
    final Lazy<SeekableStream> lazySeekableStream = new Lazy<>(new Supplier<SeekableStream>() {
        @Override
        public SeekableStream get() {
            try {
                return new SeekableFileStream(fileResource);
            } catch (final FileNotFoundException e) {
                throw new RuntimeIOException(e);
            }
        }
    });


    FileInputResource(final File fileResource) {
        super(Type.FILE);
        this.fileResource = fileResource;
    }

    @Override
    public File asFile() {
        return fileResource;
    }

    @Override
    public Path asPath() {
        return IOUtil.toPath(fileResource);
    }

    @Override
    public URL asUrl() {
        try {
            return asPath().toUri().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public SeekableStream asUnbufferedSeekableStream() {
        //if the file doesn't exist, the try to open the stream anyway because users might be expecting the exception
        //if it not a regular file than we won't be able to seek on it, so return null
        if (!fileResource.exists() || fileResource.isFile()) {
            return lazySeekableStream.get();
        } else {
            return null;
        }
    }

    @Override
    public InputStream asUnbufferedInputStream() {
        final SeekableStream seekableStream = asUnbufferedSeekableStream();
        if (seekableStream != null) {
            return seekableStream;
        } else {
            try {
                return new FileInputStream(fileResource);
            } catch (FileNotFoundException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

    @Override
    public SRAAccession asSRAAccession() {
        return null;
    }
}

class PathInputResource extends InputResource {

    final Path pathResource;
    final Function<SeekableByteChannel, SeekableByteChannel> wrapper;
    final Lazy<SeekableStream> lazySeekableStream = new Lazy<>(new Supplier<SeekableStream>() {
        @Override
        public SeekableStream get() {
            try {
                return new SeekablePathStream(pathResource, wrapper);
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    });


    PathInputResource(final Path pathResource) {
        this(pathResource, Function.identity());
    }

    //  wrapper applies to the SeekableByteChannel for custom prefetching/buffering.
    PathInputResource(final Path pathResource, Function<SeekableByteChannel, SeekableByteChannel> wrapper) {
        super(Type.PATH);
        this.pathResource = pathResource;
        this.wrapper = wrapper;
    }

    @Override
    public File asFile() {
        try {
            return asPath().toFile();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    @Override
    public Path asPath() {
        return pathResource;
    }

    @Override
    public URL asUrl() {
        try {
            return asPath().toUri().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public SeekableStream asUnbufferedSeekableStream() {
        return lazySeekableStream.get();
    }

    @Override
    public InputStream asUnbufferedInputStream() {
        return asUnbufferedSeekableStream();
    }

    @Override
    public SRAAccession asSRAAccession() {
        return null;
    }
}

class UrlInputResource extends InputResource {

    final URL urlResource;
    final Lazy<SeekableStream> lazySeekableStream = new Lazy<>(new Supplier<SeekableStream>() {
        @Override
        public SeekableStream get() {
            try { return SeekableStreamFactory.getInstance().getStreamFor(urlResource); }
            catch (final IOException ioe) { throw new RuntimeIOException(ioe); }
        }
    });

    UrlInputResource(final URL urlResource) {
        super(Type.URL);
        this.urlResource = urlResource;
    }

    @Override
    public File asFile() {
        return null;
    }

    @Override
    public Path asPath() {
        try {
            return IOUtil.getPath(urlResource.toExternalForm());
        } catch (IOException | IllegalArgumentException |
            FileSystemNotFoundException | SecurityException e) {
            return null;
        }
    }

    @Override
    public URL asUrl() {
        return urlResource;
    }

    @Override
    public SeekableStream asUnbufferedSeekableStream() {
        return lazySeekableStream.get();
    }

    @Override
    public InputStream asUnbufferedInputStream() {
        return asUnbufferedSeekableStream();
    }

    @Override
    public SRAAccession asSRAAccession() {
        return null;
    }
}

class SeekableStreamInputResource extends InputResource {

    final SeekableStream seekableStreamResource;

    SeekableStreamInputResource(final SeekableStream seekableStreamResource) {
        super(Type.SEEKABLE_STREAM);
        this.seekableStreamResource = seekableStreamResource;
    }

    @Override
    File asFile() {
        return null;
    }

    @Override
    Path asPath() {
        return null;
    }

    @Override
    URL asUrl() {
        return null;
    }

    @Override
    SeekableStream asUnbufferedSeekableStream() {
        return seekableStreamResource;
    }

    @Override
    InputStream asUnbufferedInputStream() {
        return asUnbufferedSeekableStream();
    }

    @Override
    public SRAAccession asSRAAccession() {
        return null;
    }
}

class InputStreamInputResource extends InputResource {

    final InputStream inputStreamResource;

    InputStreamInputResource(final InputStream inputStreamResource) {
        super(Type.INPUT_STREAM);
        this.inputStreamResource = inputStreamResource;
    }

    @Override
    File asFile() {
        return null;
    }

    @Override
    Path asPath() {
        return null;
    }

    @Override
    URL asUrl() {
        return null;
    }

    @Override
    SeekableStream asUnbufferedSeekableStream() {
        return null;
    }

    @Override
    InputStream asUnbufferedInputStream() {
        return inputStreamResource;
    }

    @Override
    public SRAAccession asSRAAccession() {
        return null;
    }
}

class SRAInputResource extends InputResource {

    final SRAAccession accession;

    SRAInputResource(final SRAAccession accession) {
        super(Type.SRA_ACCESSION);
        this.accession = accession;
    }

    @Override
    File asFile() {
        return null;
    }

    @Override
    Path asPath() {
        return null;
    }

    @Override
    URL asUrl() {
        return null;
    }

    @Override
    SeekableStream asUnbufferedSeekableStream() {
        return null;
    }

    @Override
    InputStream asUnbufferedInputStream() {
        return null;
    }

    @Override
    public SRAAccession asSRAAccession() {
        return accession;
    }
}
