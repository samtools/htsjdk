package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableHTTPStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Lazy;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

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
    public static SamInputResource of(final File file) { return new SamInputResource(new FileInputResource(file)); }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final InputStream inputStream) { return new SamInputResource(new InputStreamInputResource(inputStream)); }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final URL url) { return new SamInputResource(new UrlInputResource(url)); }

    /** Creates a {@link SamInputResource} reading from the provided resource, with no index. */
    public static SamInputResource of(final SeekableStream seekableStream) { return new SamInputResource(new SeekableStreamInputResource(seekableStream)); }

    /** Updates the index to point at the provided resource, then returns itself. */
    public SamInputResource index(final File file) {
        this.index = new FileInputResource(file);
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
        FILE, URL, SEEKABLE_STREAM, INPUT_STREAM
    }

    private final Type type;

    final Type type() {
        return type;
    }

    /** Returns null if this resource cannot be represented as a {@link File}. */
    abstract File asFile();

    /** Returns null if this resource cannot be represented as a {@link URL}. */
    abstract URL asUrl();

    /** Returns null if this resource cannot be represented as a {@link htsjdk.samtools.seekablestream.SeekableStream}. */
    abstract SeekableStream asUnbufferedSeekableStream();

    /** All resource types support {@link java.io.InputStream} generation. */
    abstract InputStream asUnbufferedInputStream();

    @Override
    public String toString() {
        final String childToString;
        switch (type()) {
            case FILE:
                childToString = asFile().toString();
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
            default:
                throw new IllegalStateException();
        }
        return String.format("%s:%s", type(), childToString);
    }
}

class FileInputResource extends InputResource {

    final File fileResource;
    final Lazy<SeekableStream> lazySeekableStream = new Lazy<SeekableStream>(new Lazy.LazyInitializer<SeekableStream>() {
        @Override
        public SeekableStream make() {
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
    public URL asUrl() {
        return null;
    }

    @Override
    public SeekableStream asUnbufferedSeekableStream() {
        return lazySeekableStream.get();
    }

    @Override
    public InputStream asUnbufferedInputStream() {
        return asUnbufferedSeekableStream();
    }
}

class UrlInputResource extends InputResource {

    final URL urlResource;
    final Lazy<SeekableStream> lazySeekableStream = new Lazy<SeekableStream>(new Lazy.LazyInitializer<SeekableStream>() {
        @Override
        public SeekableStream make() {
            return new SeekableHTTPStream(urlResource);
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
}
