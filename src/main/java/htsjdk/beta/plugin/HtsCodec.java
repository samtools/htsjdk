package htsjdk.beta.plugin;

import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.Bundle;

/**
 * Base interface implemented by all {@link htsjdk.beta.plugin} codecs.
 * <H3>Codecs</H3>
 * <p>
 *     Each version of a file format supported by the {@link htsjdk.beta.plugin} framework is
 *     represented by a trio of related components:
 * <ul>
 *     <li>a codec that implements {@link HtsCodec}</li>
 *     <li>an encoder that implements {@link HtsEncoder}</li>
 *     <li>a decoder that implements {@link HtsDecoder}</li>
 * </ul>
 * <p>
 *     Classes that implement {@link HtsCodec} are either discovered, instantiated, and registered dynamically
 *     at runtime via the {@link htsjdk.beta.plugin.registry.HtsCodecRegistry}'s {@link java.util.ServiceLoader}
 *     code, or are manually registered via the
 *     {@link htsjdk.beta.plugin.registry.HtsCodecRegistry#registerCodec(HtsCodec)} method.
 *     {@link HtsCodec} objects are lightweight and long-lived, and are used by the framework
 *     to resolve requests for an {@link HtsEncoder} or {@link HtsDecoder} that matches a given resource.
 *     <p>
 *     The primary responsibility of an {@link HtsCodec} is to participate in the framework's codec resolution
 *     process, and to instantiate and return an appropriate {@link HtsEncoder} or {@link HtsDecoder} on demand
 *     once a match is found.
 * <p>
 * <H3>Codec Types</H3>
 *     The plugin framework supports four different types of codec/encoder/decoder trios, enumerated
 *     by the values in {@link HtsCodecType}:
 * <p>
 * <ul>
 *     <li> {@link HtsCodecType#ALIGNED_READS} </li>
 *     <li> {@link HtsCodecType#HAPLOID_REFERENCE} </li>
 *     <li> {@link HtsCodecType#VARIANT_CONTEXTS} </li>
 *     <li> {@link HtsCodecType#FEATURES} </li>
 * </ul>
 * <p>
 *     Each of these codec types has an associated set of type-specific codec/decoder/encoder interfaces
 *     that are defined by the framework. The type-specific interfaces extend generic base interfaces,
 *     with type instantiations that are appropriate for that codec type. (as an example, see
 *     {@link htsjdk.beta.plugin.reads.ReadsDecoder} which is a specialization of
 *     {@link htsjdk.beta.plugin.HtsDecoder}. The component trios for the various implementations a given type
 *     expose the same type-specific interfaces, but over a different combination of file format/version.
 * <p>
 * The generic, base interfaces that are common to all codecs, encoders, and decoders are:
 * <ul>
 *     <li> {@link HtsCodec}: base codec interface </li>
 *     <li> {@link HtsEncoder}: base encoder interface </li>
 *     <li> {@link HtsEncoderOptions}: base options interface for encoders </li>
 *     <li> {@link HtsDecoder}: base decoder interface </li>
 *     <li> {@link HtsDecoderOptions}: base options interface for decoders </li>
 *     <li> {@link java.lang.Enum}: an enum with values for each supported file format for that type </li>
 *     <li> {@link htsjdk.beta.plugin.bundle.Bundle}: a optional type-specific
 *     {@link htsjdk.beta.plugin.bundle.Bundle} implementation </li>
 * </ul>
 * <p>
 *     The packages containing the definitions of the type-specific interfaces that correspond to
 *     the four different codec types are defined in:
 * <p>
 * <ul>
 *     <li> For {@link HtsCodecType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsCodecType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsCodecType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsCodecType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * <p>
 * <H3>Example Codec Type: Reads Codecs</H3>
 * <p>
 *     As an example, the {@link htsjdk.beta.plugin.reads} package defines the following interfaces
 *     that extend the generic base interfaces, for codecs with type {@link HtsCodecType#ALIGNED_READS}:
 * <ul>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsCodec}: reads codec interface, extends the generic
 *     {@link HtsCodec} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoder}: reads encoder, extends the generic
 *     {@link HtsEncoder} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoderOptions}: reads encoder options, extends the generic
 *     {@link HtsDecoderOptions} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoder}: reads decoder interface, extends the generic
 *     {@link HtsDecoder} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoderOptions}: reads decoder options, extends the generic
 *     {@link HtsDecoderOptions} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsFormat}: an enum with values for each possible
 *     supported reads file format </li>
 * </ul>
 * <H3>Codec Resolution Protocol</H3>
 * <p>
 *     The plugin framework uses a series of probes to inspect input and output resources in order to determine
 *     which file format/version, and thus which codec, should be used to use to obtain an encoder or decoder for
 *     that resource. Much of this work is delegated to the HtsCodec methods to allow them to determine which
 *     codecs recognize the presented resource.
 * <p>
 *     The values returned from these methods are used by the framework to prune the list of candidate codecs.
 * <p>
 *     During codec resolution, the methods are called in the following order:
 * <ol>
 *     <li> {@link #ownsURI(IOPath)} </li>
 *     <li> {@link #canDecodeURI(IOPath)} </li>
 *     <li> {@link #canDecodeStreamSignature(SignatureProbingInputStream, String)} </li>
 * </ol>
 * <p>
 *     See the {@link htsjdk.beta.plugin.registry.HtsCodecResolver} methods for more detail on the resolution
 *     protocol:
 *     <ul>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForDecoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle, HtsVersion)} </li>
 * </ul>
 * <H3>Codecs That Use a Custom URI Format or Protocol Scheme</H3>
 * <p>
 *     Most file formats consist of a single file resides on a file system that is supported by a
 *     {@link java.nio} file system provider. Codecs that support such formats are generally agnostic
 *     about the IOPath/URI protocol scheme used to identify their resources, and assume that file contents
 *     can be accessed directly via a single stream created via a {@link java.nio} file system provider.
 * <p>
 *     However some file formats require a specific, well known URI format or protocol scheme, often
 *     used to identify a remote or otherwise specially-formatted resource, such as a local database format
 *     that is distributed across multiple physical files. These codecs may bypass direct file {@link java.nio}
 *     system access, and instead use specialized code to access their underlying resources.
 * <p>
 *     For example, the {@link htsjdk.beta.codecs.reads.bam.bamV1_0.BAMCodecV1_0} assumes that IOPath
 *     resources can be accessed as a stream on a single file via either the "file://" protocol, or
 *     other protocols such gs:// or hdfs:// that have {@link java.nio} file system providers. It does
 *     not require or assume a particular URI format, and is agnostic about URI scheme.
 *     <p>
 *     In contrast, the {@link htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2.HtsgetBAMCodecV1_2} codec
 *     only handles remote resources that are  accessible via the "http://" protocol. It uses {@code http}
 *     to access the underlying resource, and bypasses direct {@link java.nio} file system access.
 * <p>
 *     Codecs that use such a custom URI format or protocol scheme such as {@code htsget} must be able
 *     to determine if they can decode or encode a resource purely by inspecting the IOPath/URI. Such
 *     codes should follow these guidelines:
 *     <ul>
 *         <li>return true when {@link #ownsURI(IOPath)} is presented with an IOPath with
 *         a conforming URI </li>
 *         <li>return true when {@link #canDecodeURI(IOPath)} is presented with an IOPath
 *         with a conforming URI </li>
 *         <li> ensure that for a given IOPath, {@link #ownsURI(IOPath)} == {@link #canDecodeURI(IOPath)}
 *         <li>always return 0 from the {@link #getSignatureProbeLength()} method</li>
 *         <li>always return 0 from the {@link #getSignatureLength()} method</li>
 *     </ul>
 * </p>
 * @param <F> a {@link java.lang.Enum} that implements {@link HtsFormat}, with a value for each file format
 *          handled by this codec
 * @param <D> decoder options type for this codec
 * @param <E> encoder options type for this codec
 */
public interface HtsCodec<
        F extends Enum<F> & HtsFormat<F>,
        D extends HtsDecoderOptions,
        E extends HtsEncoderOptions>
        extends Upgradeable {

    /**
     * @return the {@link HtsCodecType} for this codec. The {@link HtsCodecType} dictates the interfaces
     * exposed by the codec, as well as the HEADER and RECORD types exposed by the {@link HtsEncoder} and
     * {@link HtsDecoder}s for this codec. Each implementation of the same type exposes the same interfaces,
     * but over a different file format or version. For example, both the BAM and HTSGET_BAM codecs have
     * codec type {@link HtsCodecType#ALIGNED_READS}, and are derived from {@link htsjdk.beta.plugin.reads.ReadsCodec},
     * but the serialized file formats and access mechanisms for the two codecs are different).
     */
    HtsCodecType getCodecType();

    /**
     * The file format supported by this codec. Taken from the values in {@code F}.
     *
     * @return a value taken from the Enum {@code F}, representing the underlying file format handled by this codec
     */
    F getFileFormat();

    /**
     * The version of the file format (returned by {@link #getFileFormat()} supported by this codec.
     *
     * @return the file format version ({@link HtsVersion}) supported by this codec
     */
    HtsVersion getVersion();

    /**
     * A user-friendly display name for instances of this codec. It is recommended that this name minimally
     * include both the supported file format and version.
     *
     * @return a user-friendly display name for instances of this codec
     */
    default String getDisplayName() {
        return String.format("%s/%s/%s", getFileFormat(), getVersion(), getClass().getName());
    }

    /**
     * Returning true from this method indicates that this codec "owns" the URI contained in
     * {@code ioPath} ({@see IOPath#getURI()}).
     * A codec "owns" the URI only if it explicitly recognizes and handles the protocol scheme in the
     * URI, or recognizes the rest of the URI as being well-formed for this file format
     * (including, for example, the file extension if appropriate, and any query parameters).
     * <p>
     * Returning true from this method will cause the framework to bypass the stream-oriented signature
     * probing that is used to resolve inputs to a codec handler. During codec resolution, if any registered
     * codec returns true for this method on {@code ioPath}, the signature probing protocol will instead:
     * <ol>
     * <li> immediately prune the list of candidate codecs to only those that return true for this method
     * on {@code ioPath}</li>
     * <li> not attempt to obtain an InputStream on the IOPath containing the URI, on the assumption that
     * special handling is required in order to access the underlying resource (i.e., htsget
     * codec would claim an "http://" URI if the rest of the URI conforms to the expected format for that
     * codec's protocol). </li>
     * </ol>
     * <p>
     * Any codec that returns true from {@link #ownsURI(IOPath)} for a given IOPath must also return true
     * from {@link #canDecodeURI(IOPath)} for the same IOPath.
     *
     * @param ioPath the ioPath to inspect
     * @return true if the ioPath's URI represents a custom URI that this codec handles
     */
    default boolean ownsURI(final IOPath ioPath) { return false; }

    /**
     * Return true if the URI for <code>ioPath</code> (obtained via {@link IOPath#getURI()} appears to
     * conform to the expected URI format this codec's file format. Most implementations only look at
     * the file extension {@see htsjdk.io.IOPath#hasExtension}. If the file format implemented by this
     * codec does not use a specific file extension, or if the codec cannot determine if it can decode
     * the underlying resource without inspecting the underlying stream, it is safe to return true. In
     * that case the framework will make a subsequent call to this codec's
     * {@link #canDecodeStreamSignature(SignatureProbingInputStream, String)} method, during which time
     * the codec can do a more detailed inspection of the {@link SignatureProbingInputStream}.
     * <p>
     * Implementations should generally not inspect the URI's protocol scheme unless the file format
     * supported by the codec requires the use a specific protocol scheme. For codecs that do own
     * a specific scheme or URI format, any codec that returns true from {@link #ownsURI(IOPath)} for a given
     * IOPath must also return true from {@link #canDecodeURI(IOPath)} for the same IOPath.
     * <p>
     * It is never save to attempt to directly inspect the underlying stream for <code>ioPath</code> in
     * this method. If the stream needs to be inspected, it should be done when the
     * {@link #canDecodeStreamSignature(SignatureProbingInputStream, String)} method is called.
     *
     * @param ioPath to be decoded
     * @return true if the codec can provide a decoder to provide this URI
     */
    boolean canDecodeURI(final IOPath ioPath);

    /**
     * Determine if codec can decode the input stream by inspecting any signature embedded
     * within the stream. The probingInputStream stream will contain only the leading fragment
     * of the actual input stream, the size of which will be the lesser of:
     * <p>
     * <ol>
     *     <li> the number of bytes returned by {@link #getSignatureProbeLength} </li>
     *     <li> the entire input stream, for streams that are smaller than {@link #getSignatureProbeLength}  </li>
     * </ol>
     * <p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI}), should
     * always return false from this method.
     * </p>
     *
     * @param probingInputStream the stream to be used by the codec to inspect the resource's embedded
     *                          signature and version
     * @param sourceName a display name describing the source of the input stream, for use in error messages
     * @return true if this codec recognizes the stream by it's signature, and can provide a decoder to
     * decode the stream
     */
    boolean canDecodeStreamSignature(final SignatureProbingInputStream probingInputStream, final String sourceName);

    /**
     * The number of bytes in the format name and version signature used by the file format supported by
     * this codec.
     *
     * @return if the file format supported by this codecs is not remote, and is accessible
     * via a local file or stream, the size of the unique signature/version for this file format. otherwise 0.
     * <p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI}), should
     * always return 0 from this method.
     */
    int getSignatureLength();

    /**
     * The number of bytes of needed by this codec to probe an input stream for a format/version signature
     * and determine if it can supply an decoder for the stream.
     *
     * @return the number of bytes this codec must consume from a stream in order to determine whether
     * it can decode that stream. This number may differ from the actual signature size
     * as returned by {@link #getSignatureLength()} for codecs that support compressed or encrypted
     * streams, since they may require a larger and more semantically meaningful input fragment
     * (such as an entire encrypted or compressed block) in order to inspect the plaintext signature.
     * <p>
     * Therefore signatureProbeSize should be expressed in "compressed/encrypted" space rather than
     * "plaintext" space. The length returned from this method is used to determine the size of the
     * {@link SignatureProbingInputStream} that is subsequently passed to
     * {@link #canDecodeStreamSignature(SignatureProbingInputStream, String)}.
     * <p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI(IOPath)}),
     * should always return 0 from this method when it is called.
     * </p>
     */
    default int getSignatureProbeLength() { return getSignatureLength(); }

    /**
     * Return an {@link HtsDecoder}-derived object that can decode the provided inputs
     * @param inputBundle input to be decoded
     * @param decoderOptions options for the decoder to use
     * @return an {@link HtsDecoder}-derived object that can decode the provided inputs
     */
    HtsDecoder<F, ?, ? extends HtsRecord> getDecoder(final Bundle inputBundle, final D decoderOptions);

    /**
     * Return an {@link HtsEncoder}-derived object suitable for encoding to the provided outputs
     * @param outputBundle target output for the encoder
     * @param encoderOptions encoder options to use
     * @return an {@link HtsEncoder}-derived object suitable for writing to the provided outputs
     */
    HtsEncoder<F, ?, ? extends HtsRecord> getEncoder(final Bundle outputBundle, final E encoderOptions);

}
