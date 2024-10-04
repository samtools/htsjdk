package htsjdk.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class DelegatingPath implements Path {
    public static Path of(String first, String... more) {
        return Path.of(first, more);
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystemDelegate;
    }

    @Override
    public boolean isAbsolute() {
        return delegate.isAbsolute();
    }

    @Override
    public Path getRoot() {
        return new DelegatingPath(delegate.getRoot(), fileSystemDelegate);
    }

    @Override
    public Path getFileName() {
        return new DelegatingPath(delegate.getFileName(), fileSystemDelegate);
    }

    @Override
    public Path getParent() {
        return new DelegatingPath(delegate.getParent(), fileSystemDelegate);
    }

    @Override
    public int getNameCount() {
        return delegate.getNameCount();
    }

    @Override
    public Path getName(int index) {
        return new DelegatingPath(delegate.getName(index), fileSystemDelegate);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return new DelegatingPath(delegate.subpath(beginIndex, endIndex), fileSystemDelegate);
    }

    @Override
    public boolean startsWith(Path other) {
        return delegate.startsWith(other);
    }

    @Override
    public boolean startsWith(String other) {
        return delegate.startsWith(other);
    }

    @Override
    public boolean endsWith(Path other) {
        return delegate.endsWith(other);
    }

    @Override
    public boolean endsWith(String other) {
        return delegate.endsWith(other);
    }

    @Override
    public Path normalize() {
        return new DelegatingPath(delegate.normalize(), fileSystemDelegate);
    }

    @Override
    public Path resolve(Path other) {
        return new DelegatingPath(delegate.resolve(other), fileSystemDelegate);
    }

    @Override
    public Path resolve(String other) {
        return new DelegatingPath(delegate.resolve(other), fileSystemDelegate);
    }

    @Override
    public Path resolveSibling(Path other) {
        return new DelegatingPath(delegate.resolveSibling(other), fileSystemDelegate);
    }

    @Override
    public Path resolveSibling(String other) {
        return new DelegatingPath(delegate.resolveSibling(other), fileSystemDelegate);
    }

    @Override
    public Path relativize(Path other) {
        return new DelegatingPath(delegate.relativize(other), fileSystemDelegate);
    }

    @Override
    public URI toUri() {
        return delegate.toUri();
    }

    @Override
    public Path toAbsolutePath() {
        return new DelegatingPath(delegate.toAbsolutePath(), fileSystemDelegate);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return new DelegatingPath(delegate.toRealPath(options), fileSystemDelegate);
    }

    @Override
    public File toFile() {
        return delegate.toFile();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        return delegate.register(watcher, events, modifiers);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        return delegate.register(watcher, events);
    }

    @Override
    public Iterator<Path> iterator() {
        return  StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(delegate.iterator(), Spliterator.ORDERED),
                false)
                .map(path -> new DelegatingPath(path, fileSystemDelegate))
                .map(path -> (Path)path)
                .iterator();
    }

    @Override
    public int compareTo(Path other) {
        return delegate.compareTo(other);
    }

    @Override
    public boolean equals(Object other) {
        return delegate.equals(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void forEach(Consumer<? super Path> action) {
        delegate.forEach(action);
    }

    @Override
    public Spliterator<Path> spliterator() {
        return StreamSupport.stream(delegate.spliterator(), false)
                .map(path -> new DelegatingPath(path, fileSystemDelegate))
                .map(path -> (Path)path)
                .spliterator();
    }

    private final Path delegate;
    private final FileSystem fileSystemDelegate;

    public DelegatingPath(Path delegate, FileSystem fileSystemDelegate) {
        this.delegate = delegate;
        this.fileSystemDelegate = fileSystemDelegate;
    }

}
