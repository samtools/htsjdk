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
     * BundleResource#isInput()} is false for this resource.
     *
     * The stream returned by this method may use a read-ahead/buffering layer over the underlying
     * resource, which may not be suitable for some applications that need absolute control over the
     * offsets of the underlying stream, such as index creation.
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
     * @return a {@link SignatureProbingStream} over the first {@code signatureProbeLength} bytes of this
     * resource, for use with signature probing for codec resolution.
     *
     * Note that this method requires access to the first {@code signatureProbeLength} bytes of the underlying
     * resource. {@link BundleResource} implementations that are backed by raw streams that can only be consumed
     * once, such as {@link InputStreamResource}, may consume and buffer a portion of the underlying resource's
     * stream in order to allow subsequent callers of the {@link #getInputStream()}) method to be presented with
     * the entire stream, including the signature. Calls to this method may the have the side effect of changing
     * or resetting the current position of the underlying stream; serial calls to
     * {@link #getSignatureProbingStream} on the same object are not necessarily idempotent; and implementations
     * are free to throw to prevent serial calls to this method.
     *
     * @param signatureProbeLength the number of bytes of the underlying resource to include in the probing stream.
     *                             {@code signatureProbeLength} should be expressed in "compressed(/encrypted)" space
     *                             rather than "plaintext" space. For example, a file format signature may consist
     *                             of {@code n} bytes of ASCII, but for formats that use compressed streams,
     *                             the codec may need access to an entire compressed block in order to inspect
     *                             those {@code n} bytes. {@code signatureProbeLength} should use the compressed
     *                             block size, in order to ensure that the signature probing stream contains a
     *                             semantically meaningful fragment of the underlying input.
     *
     * @throws IllegalArgumentException if this method has previously been called on this resource
     */
    SignatureProbingStream getSignatureProbingStream(final int signatureProbeLength);

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
