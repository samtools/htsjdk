package htsjdk.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class PipeSafeFileSystemProvider extends FileSystemProvider {
    static {
        System.setProperty("java.nio.file.spi.DefaultFileSystemProvider", "htsjdk.io.PipeSafeFileSystemProvider");
    }

    private final FileSystemProvider defaultProvider;

    public PipeSafeFileSystemProvider(FileSystemProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    @Override
    public String getScheme() {
        return defaultProvider.getScheme();
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        return defaultProvider.newFileSystem(uri, env);
    }

    @Override
    public  FileSystem getFileSystem(URI uri) {
        return defaultProvider.getFileSystem(uri);
    }

    @Override
    public Path getPath(URI uri) {
        return defaultProvider.getPath(uri);
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        return new DelegatingFileSystem(defaultProvider.newFileSystem(path, env), this);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        System.out.println("We've stolen your stream");
        return defaultProvider.newInputStream(path, options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return defaultProvider.newOutputStream(path, options);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return defaultProvider.newFileChannel(path, options, attrs);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options, ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
        return defaultProvider.newAsynchronousFileChannel(path, options, executor, attrs);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return defaultProvider.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return defaultProvider.newDirectoryStream(dir, filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        defaultProvider.createDirectory(dir, attrs);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        defaultProvider.createSymbolicLink(link, target, attrs);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        defaultProvider.createLink(link, existing);
    }

    @Override
    public void delete(Path path) throws IOException {
        defaultProvider.delete(path);
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return defaultProvider.deleteIfExists(path);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        return defaultProvider.readSymbolicLink(link);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        defaultProvider.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        defaultProvider.move(source, target, options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return defaultProvider.isSameFile(path, path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return defaultProvider.isHidden(path);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return defaultProvider.getFileStore(path);
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        defaultProvider.checkAccess(path, modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return defaultProvider.getFileAttributeView(path, type, options);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        return defaultProvider.readAttributes(path, type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return defaultProvider.readAttributes(path, attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        defaultProvider.setAttribute(path, attribute, value, options);
    }
}
