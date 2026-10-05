package htsjdk.testutil.ftp;

import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.impl.DefaultFtpServer;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;

/**
 * An Apache FtpServer on a free loopback port, serving the files a test adds from a temporary directory. It accepts
 * anonymous logins and {@link #USER} with {@link #PASSWORD}.
 */
public final class LocalFtpServer implements AutoCloseable {
    public static final String USER = "reader";
    public static final String PASSWORD = "secret";
    private static final String LOOPBACK = "127.0.0.1";

    private final Path root;
    private final FtpServer server;
    private final int port;

    public LocalFtpServer() throws IOException {
        root = Files.createTempDirectory("LocalFtpServer");
        try {
            final UserManager users = new PropertiesUserManagerFactory().createUserManager();
            users.save(user("anonymous", null));
            users.save(user(USER, PASSWORD));

            final ListenerFactory listener = new ListenerFactory();
            listener.setServerAddress(LOOPBACK);
            listener.setPort(0);

            final FtpServerFactory factory = new FtpServerFactory();
            factory.setUserManager(users);
            factory.addListener("default", listener.createListener());
            server = factory.createServer();
            server.start();
            port = ((DefaultFtpServer) server).getListener("default").getPort();
        } catch (final FtpException e) {
            IOUtil.recursiveDelete(root);
            throw new IOException("Could not start an FTP server", e);
        }
    }

    private BaseUser user(final String name, final String password) {
        final BaseUser user = new BaseUser();
        user.setName(name);
        user.setPassword(password);
        user.setHomeDirectory(root.toString());
        return user;
    }

    /** Serves {@code contents} at {@code path}, an absolute path such as {@code /pub/data/reads.bam}. */
    public LocalFtpServer addFile(final String path, final byte[] contents) throws IOException {
        final Path file = root.resolve(path.substring(1));
        Files.createDirectories(file.getParent());
        Files.write(file, contents);
        return this;
    }

    /** The port the server listens on. */
    public int getPort() {
        return port;
    }

    /** The {@code ftp} URL of {@code path} on this server, percent-encoding any characters a URL can't hold. */
    public URL url(final String path) {
        try {
            return new URI("ftp", null, LOOPBACK, port, path, null, null).toURL();
        } catch (final URISyntaxException | IOException e) {
            throw new IllegalArgumentException("Can't make a URL of " + path, e);
        }
    }

    /** Stops the server and deletes its files. */
    @Override
    public void close() {
        server.stop();
        IOUtil.recursiveDelete(root);
    }
}
