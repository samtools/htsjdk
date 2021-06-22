package htsjdk.beta.plugin;

import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.Bundle;

/**
 * Base interface implemented by all {@link htsjdk.beta.plugin} codecs.
 * <H3>Codecs</H3>
 * <p>
 *     Each version of a file format supported by the HTSJDK plugin framework is represented by a class
 *     that implements HtsCodec. These classes are discovered dynamically at runtime, and instantiated
 *     and registered in the {@link htsjdk.beta.plugin.registry.HtsCodecRegistry}. The objects are lightweight
 *     and long-lived, and are interrogated by the framework to resolve requests to find an encoder or decoder
 *     to match a given resource.
 * <p>
 *     The main responsibility of an HtsCodec is to satisfy these requests by the framework, and to instantiate
 *     and return an appropriate {@link HtsEncoder} or {@link HtsDecoder} on request from the framework once
 *     it has determined that a match is found.
 * <p>
 *     There are 4 different types of codecs, enumerated by the values in {@link HtsCodecType}:
 * <ul>
 *     <li> {@link HtsCodecType#ALIGNED_READS} </li>
 *     <li> {@link HtsCodecType#HAPLOID_REFERENCE} </li>
 *     <li> {@link HtsCodecType#VARIANT_CONTEXTS} </li>
 *     <li> {@link HtsCodecType#FEATURES} </li>
 * </ul>
 * <p>
 *     Each codec type has a corresponding set of interfaces that are specific to that type that
 *     are derived from common interfaces defined by the plugin framework. These interfaces must
 *     be implemented by either the codec itself, or by the codec's decoder or encoder components.
 *     Each implementation of a given codec type exposes the same interfaces, but over a different
 *     combination of file format and version.
 * <p>
 * The base interfaces that are common to all HtsCodecs, from which the type-specific interfaces derive are:
 * <ul>
 *     <li> {@link HtsCodec}: the base codec </li>
 *     <li> {@link HtsEncoder}: the encoder interface for this codec type </li>
 *     <li> {@link HtsEncoderOptions}: encoder options specific to the codec's type </li>
 *     <li> {@link HtsDecoder}: decoder options specific to the codec's type </li>
 *     <li> {@link HtsDecoderOptions}: decoder options specific to the codec's type </li>
 *     <li> {@link java.lang.Enum}: an enum with values for each supported file format for that type </li>
 *     <li> (optional) {@link htsjdk.beta.plugin.bundle.Bundle}: a type-specific
 *     {@link htsjdk.beta.plugin.bundle.Bundle} for that codec type </li>
 * </ul>
 * <p>
 *     As an example, an codec of type {@link HtsCodecType#ALIGNED_READS} implements and consumes the following
 *     specialized interfaces that are derived from the common base interfaces, and defined in the
 *     {@link htsjdk.beta.plugin.reads} package:
 * <ul>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsCodec}: the base reads codec interface, extends the common interface {@link HtsCodec} </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoder}: reads encoder options, extends the common interface {@link HtsEncoder} </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoderOptions}: reads encoder options, extends the common interface {@link HtsDecoderOptions} </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoder}: reads decoder interface, extends the common interface {@link HtsDecoder} </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoderOptions}: reads decoder options, extends the common interface {@link HtsDecoderOptions} </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsFormat}: an enum with values for each possible
 *     supported reads file format </li>
 * </ul>
 * <p>
 * The packages containing the definitions of the common interfaces that are defined for each of the 4 different
 * codec types are:
 * <ul>
 *     <li> For {@link HtsCodecType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsCodecType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsCodecType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsCodecType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * <H3>Codec Resolution Protocol</H3>
 * <p>
 *     The plugin framework uses a series of probes to inspect input and output resources in order to determine
 *     which file format/version, and thus which codec, to use to obtain an encoder or decoder for that resource.
 *     Much of this work is delegated to registered codecs by calling methods implemented in HtsCodec.
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
 *     See the {@link htsjdk.beta.plugin.registry.HtsCodecResolver} methods for more detail:
 *     <ul>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForDecoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle, HtsVersion)} </li>
 * </ul>
 * <H3>Custom Protocol Schemes</H3>
 * <p>
 *     Most codecs are agnostic about the IOPath protocol scheme used for resources that are to be decoded or
 *     encoded, and assume that file contents can be accessed via {@link java.nio} file system providers. However
 *     some file formats depend on a specific, well known URI protocol scheme, often used to access a
 *     remote or otherwise specially-formatted resource. These codecs use specialized code that bypasses file
 *     system access in order to encode or decode resources.
 * <p>
 *     For example, the {@link htsjdk.beta.codecs.reads.bam.bamV1_0.BAMCodecV1_0} assumes that underlying
 *     input and output IOPath resources can be accessed as a file via either the "file://" protocol, or
 *     other protocols such gs:// or hdfs:// that have {@link java.nio} file system providers, and is
 *     agnostic about URI scheme. In contrast, the
 *     {@link htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2.HtsgetBAMCodecV1_2} codec only handles resources
 *     that are accessible via the htsget protocol, identified via the "http://" protocol. It uses http to
 *     access the underlying resource, and bypasses file system access.
 * <p>
 *     Codecs that use a custom resource protocol scheme such as htsget must recognize their own protocol
 *     scheme, and return true from the {@link #ownsURI(IOPath)} method when presented with a
 *     protocol-conforming URI.
 * </p>
 * @param <F> an {@link java.lang.Enum} with a value for each file format handled by this codec
 * @param <D> decoder options for this codec
 * @param <E> encoder options for this codec
 */
public interface HtsCodec<
        F extends Enum<F>,
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
     * Returning true from this method indicates that the resource URI contains a URI that this
     * codec "owns", and that the input resource should not be handed to any protocol-agnostic codecs because
     * the resource maya require use of a custom, non-NIO access mechanism.
     *
     * A codec "owns" the URI only if it explicitly recognizes and handles the protocol scheme
     * in the <code>resource</code> URI, and recognizes the rest of the URI as being well-formed for that protocol
     * (including, for example, the file extension if appropriate, and any query parameters).
     * <p>
     * Returning true from this method will short-circuit the stream-oriented signature probing that is used
     * by the framework to resolve inputs to a codec handler. During codec resolution, if any
     * registered codec returns true for this method on this URI, the signature probing protocol will:
     * <ol>
     *  <li> immediately prune the list of candidate codecs to only those that return true for this method </li>
     *  <li> not attempt to obtain an "InputStream" on the IOPath, on the assumption that custom protocol
     *  handlers must perform special handling to access the underlying scheme (i.e., htsget codec would
     *  claim an "http://" uri if the rest of the URI conforms to the expected format for that codec's
     *  protocol). </li>
     * </ol>
     * <p>
     * Any codec that returns true for a given IOPath must also return true from {@link #canDecodeURI}
     * when given the same IOPath.
     *
     * @param resource the resource to inspect
     * @return true if the URI represents a custom URI that this codec handles
     */
    default boolean ownsURI(final IOPath resource) { return false; }

    /**
     * Return true if the URI in <code>resource</code> appears to conform to the expected URI for this codec.
     * Most implementations only look at the file extension. If the file format implemented by this codec does not
     * use a specific file extension, it is safe to return true, in which case the framework will make a subsequent
     * call to this codec's {@link #canDecodeStreamSignature} method, during which the codec can do a more detailed
     * inspection of the stream signature.
     * <p>
     * Implementations should generally not inspect the protocol scheme unless the file format supported by the
     * codec uses a specific protocol scheme, in which case it should also return true for the same <code>resource</code>
     * when {@link #ownsURI} is called.
     * <p>
     * It is not save to inspect the underlying stream for <code>resource</code> directly in this method. If the
     * stream needs to be inspected, it should be done in the {@link #canDecodeStreamSignature} method.
     *
     * @param resource to be decoded
     * @return true if the codec can provide a decoder to provide this URL
     */
    boolean canDecodeURI(final IOPath resource);

    /**
     * Determine if codec can decode the input stream by inspecting any signature embedded
     * within the stream. The probingInputStream stream contains only the first bytes f the actual input
     * stream. Specifically, it is guaranteed to contain the lesser of:
     * <p>
     * <ol>
     *     <li> the number of bytes {@link #getSignatureProbeSize} returns for this codec or </li>
     *     <li> the entire input stream </li>
     * </ol>
     * <p>
     * It is permissible to consume as much of the stream as necessary in order to determine the
     * return value, and also to close the stream before returning control.
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
     * The number of bytes of input required by this codec to determine if it can supply an decoder for the stream.
     *
     * @return the number of bytes this codec must consume from a stream in order to determine whether
     * it can decode that stream. This number may differ from the size of the actual signature size
     * as returned by {@link #getSignatureLength()} for compressed or encrypted streams, since they may
     * require a larger and more semantically meaningful input fragment (such as an entire encrypted or
     * compressed block) in order to inspect the plaintext signature.
     * <p>
     * Note that  signatureProbeSize should be expressed in "compressed/encrypted" space rather than
     * "plaintext" space. For example, a raw signature may be {@code N} bytes of raw ASCII, but the codec
     * may need to consume an entire encrypted GZIP block in order to inspect those {@code N} bytes, so the
     * signaturePrefixSize should be specified based on the compressed block size.
     *
     * The size returned from this method is used by the framework to construct the
     * {@link SignatureProbingInputStream} that is subsequently passed to
     * {@link #canDecodeStreamSignature(SignatureProbingInputStream, String)}.
     * <p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI}), should
     * always return 0 from this method.
     * </p>
     */
    default int getSignatureProbeSize() { return getSignatureLength(); }

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
