package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Interface defined for bundle resource objects that may be included in a {@link Bundle}.
 */
public interface BundleResource {
    /**
     * @return the display name for this resource
     */
    String getDisplayName();

    /**
     * @return the content type for this resource
     */
    String getContentType();

    /**
     * @return the content subtype for this resource, or Optional.empty if not present
     */
    Optional<String> getContentSubType();

    /**
     * @return an IOPath for this resource, or Optional.empty if not present
     */
    Optional<IOPath> getIOPath();

    /**
     * @return an {@link InputStream} for this resource, or Optional.empty if {@link
     * BundleResource#isInput()} is false for this resource
     */
    Optional<InputStream> getInputStream();

    /**
     * @return an {@link OutputStream} for this resource, or Optional.empty if {@link
     * BundleResource#isOutput()} is false for this resource
     */
    Optional<OutputStream> getOutputStream();

    /**
     * @return true if this resource can render a {@link SeekableStream} through {@link #getSeekableStream}
     */
    boolean hasSeekableStream();

    /**
     * @return a {@link SignatureProbingInputStream} stream over the first {@code signatureProbeLength} bytes of this
     * resource that can be used to support signature probing.
     *
     * Once this method is called on a {@link BundleResource} object using a given {@code signatureProbeLength},
     * subsequent calls to the method on the same object must use the same {@code signatureProbeLength} or smaller.
     *
     * Note that for resources for which the underlying stream cannot be reconstructed once it is consumed,
     * this method must be called before any of the underlying stream has been consumed.
     *
     * @param signatureProbeLength signatureProbeLength should be expressed in "compressed(/encrypted)" space
     *                            rather than "plaintext" space. For example, a file format signature may consist
     *                            of {@code n} bytes of ASCII, but the if the file format uses compressed streams,
     *                            the codec may need access to an entire compressed block in order to inspect
     *                            those {@code n} bytes. signatureProbeLength should be specified based on the
     *                            compressed block size, in order to ensure that the signature probing stream
     *                            contains a semantically meaningful fragment of the underlying input.
     *
     * @throws IllegalArgumentException if this method has previously been called on this object with a smaller
     * prefixSize.
     */
    SignatureProbingInputStream getSignatureProbingStream(final int signatureProbeLength);

    /**
     * @return an {@link SeekableStream} for this resource, or Optional.empty if this is
     * an not an {@link BundleResource#isInput()}, or is an InputResource for which
     * no {@link SeekableStream} can be obtained
     */
    Optional<SeekableStream> getSeekableStream();

    /**
     * @return true if an InputStream can be obtained via {@link #getInputStream()} on this resource
     */
    boolean isInput();

    /**
     * @return true if an OutputStream can be obtained via {@link #getOutputStream()} on this resource
     */
    boolean isOutput();
}
