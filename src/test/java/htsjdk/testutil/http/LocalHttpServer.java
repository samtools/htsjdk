package htsjdk.testutil.http;

import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;

/**
 * A Jetty server on a free loopback port, serving the files a test adds from a temporary directory. It answers GET and
 * HEAD, single byte ranges with 206 (clamping an end past the end of the file, and 416 for a start at or after it),
 * gives each file an ETag, and answers 404 for a missing file.
 */
public final class LocalHttpServer implements AutoCloseable {
    private static final String LOOPBACK = "127.0.0.1";

    private final Path root;
    private final Server server;
    private final int port;

    public LocalHttpServer() throws IOException {
        // the real path, since Jetty treats a file reached through a symlink (macOS's /var) as an alias
        root = Files.createTempDirectory("LocalHttpServer").toRealPath();
        // daemon threads so a server a test forgets to close can't keep the JVM alive
        final QueuedThreadPool threads = new QueuedThreadPool();
        threads.setDaemon(true);
        server = new Server(threads, new ScheduledExecutorScheduler(null, true), null);
        final ServerConnector connector = new ServerConnector(server, 1, 1);
        connector.setHost(LOOPBACK);
        connector.setPort(0);
        server.addConnector(connector);

        final ResourceHandler files = new ResourceHandler();
        files.setBaseResource(ResourceFactory.of(server).newResource(root));
        files.setDirAllowed(false);
        files.setEtags(true);
        server.setHandler(files);
        try {
            server.start();
        } catch (final Exception e) {
            IOUtil.recursiveDelete(root);
            throw new IOException("Could not start an HTTP server", e);
        }
        port = connector.getLocalPort();
    }

    /** Serves {@code contents} at {@code path}, an absolute path such as {@code /data/reads.bam}. */
    public LocalHttpServer addFile(final String path, final byte[] contents) throws IOException {
        Files.write(fileAt(path), contents);
        return this;
    }

    /** Serves a copy of {@code source} at {@code path}, an absolute path such as {@code /data/reads.bam}. */
    public LocalHttpServer addFile(final String path, final Path source) throws IOException {
        Files.copy(source, fileAt(path), StandardCopyOption.REPLACE_EXISTING);
        return this;
    }

    private Path fileAt(final String path) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("A path to serve must start with '/': " + path);
        }
        final Path file = root.resolve(path.substring(1));
        Files.createDirectories(file.getParent());
        return file;
    }

    /** The port the server listens on. */
    public int getPort() {
        return port;
    }

    /**
     * The {@code http} URL of {@code path} on this server, percent-encoding any characters a URL can't hold. A
     * {@code path} may end in a query string ({@code /test?file=my.bam}), which the server ignores.
     */
    public URL url(final String path) {
        final int question = path.indexOf('?');
        final String filePath = question < 0 ? path : path.substring(0, question);
        final String query = question < 0 ? null : path.substring(question + 1);
        try {
            return new URI("http", null, LOOPBACK, port, filePath, query, null).toURL();
        } catch (final URISyntaxException | IOException e) {
            throw new IllegalArgumentException("Can't make a URL of " + path, e);
        }
    }

    /** Stops the server and deletes its files. */
    @Override
    public void close() {
        try {
            server.stop();
        } catch (final Exception e) {
            throw new IllegalStateException("Could not stop the HTTP server", e);
        } finally {
            IOUtil.recursiveDelete(root);
        }
    }
}
