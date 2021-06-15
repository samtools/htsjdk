package htsjdk.beta.plugin.registry;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.bundle.InputStreamResource;
import htsjdk.beta.plugin.bundle.OutputStreamResource;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodec;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodecFormat;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

//TODO: test/rules for how to resolve input index
//TODO: need a concept of "default" encoder (and decoder?) for the case where there is nothing to go on
//      i.e. /dev/stdout
//TODO: test SeekableStreamInputs
//TODO: test large number of inputs where the caller asserts the content type, content subtype, version
//TODO: add a test where the expected file extension isn't present (what are the rules for canDecode* ?)
//TODO test /dev/stdin

//TODO: Output Bundles:
//TODO: should there be a default "content subtype" for when no content subtype is requested ?
//TODO: handle *writable* custom protocol schemes (though we don't have any ? genomicsDB ?)
//TODO test /dev/stdout

// Tests for resolving codec (encoder/decoder) requests given a set of input/output resources and a list
// of registered codecs.
public class HtsCodecsByFormatVersionTest extends HtsjdkTest {
    // all of the test codecs created here have the same codec (content) type, with each codec varying by
    // content sub type, version, or protocol scheme
    final static String TEST_CODEC_CONTENT_TYPE = "TEST_CODEC_CONTENT_TYPE";

    // parameters for file format 1
    final static String FORMAT_1_CONTENT_SUBTYPE = HtsTestCodecFormat.FILE_FORMAT_1.toString();
    final static String FORMAT_1_STREAM_SIGNATURE = "FORMAT_1_STREAM_SIGNATURE";
    final static String FORMAT_1_FILE_EXTENSION = ".f1";

    // parameters for file format 2
    final static String FORMAT_2_CONTENT_SUBTYPE = HtsTestCodecFormat.FILE_FORMAT_2.toString();
    final static String FORMAT_2_STREAM_SIGNATURE = "FORMAT_2_STREAM_SIGNATURE";
    final static String FORMAT_2_FILE_EXTENSION = ".f2";

    // parameters for file format 3, which uses a custom protocol scheme
    final static String FORMAT_3_CONTENT_SUBTYPE = HtsTestCodecFormat.FILE_FORMAT_3.toString();
    final static String FORMAT_3_STREAM_SIGNATURE = "FORMAT_3_STREAM_SIGNATURE";
    final static String FORMAT_3_FILE_EXTENSION = ".f3";
    final static String FORMAT_3_PROTOCOL_SCHEME = "ps3";

    final static HtsVersion V1_0 = new HtsVersion(1, 0, 0);
    final static HtsVersion V1_1 = new HtsVersion(1, 1, 0);
    final static HtsVersion V2_0 = new HtsVersion(2, 0, 0);

    // file format 1, v1.0
    final static HtsTestCodec FORMAT_1_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V1_0,
            FORMAT_1_CONTENT_SUBTYPE,
            FORMAT_1_FILE_EXTENSION,
            FORMAT_1_STREAM_SIGNATURE,
            null,
            false);
    // file format 1, v1.1
    final static HtsTestCodec FORMAT_1_V1_1 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V1_1,
            FORMAT_1_CONTENT_SUBTYPE,
            FORMAT_1_FILE_EXTENSION,
            FORMAT_1_STREAM_SIGNATURE,
            null,
            false);
    // file format 1, v2.0
    final static HtsTestCodec FORMAT_1_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V2_0,
            FORMAT_1_CONTENT_SUBTYPE,
            FORMAT_1_FILE_EXTENSION,
            FORMAT_1_STREAM_SIGNATURE,
            null,
            false);

    // file format 2, v1.0
    final static HtsTestCodec FORMAT_2_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V1_0,
            FORMAT_2_CONTENT_SUBTYPE,
            FORMAT_2_FILE_EXTENSION,
            FORMAT_2_STREAM_SIGNATURE,
            null,
            false);
    // file format 2, v1.1
    final static HtsTestCodec FORMAT_2_V1_1 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V1_1,
            FORMAT_2_CONTENT_SUBTYPE,
            FORMAT_2_FILE_EXTENSION,
            FORMAT_2_STREAM_SIGNATURE,
            null,
            false);
    // file format 2, v2.0, uses gzipped inputs
    final static HtsTestCodec FORMAT_2_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V2_0,
            FORMAT_2_CONTENT_SUBTYPE,
            FORMAT_2_FILE_EXTENSION,
            FORMAT_2_STREAM_SIGNATURE,
            null,
            true);  // expect gzipped inputs

    // file format 3, v1.0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_3,
            V1_0,
            FORMAT_3_CONTENT_SUBTYPE,
            FORMAT_3_FILE_EXTENSION,
            FORMAT_3_STREAM_SIGNATURE,
            FORMAT_3_PROTOCOL_SCHEME,   // custom protocol scheme
            false);

    // file format 3, v2.0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_3,
            V2_0,
            FORMAT_3_CONTENT_SUBTYPE,
            FORMAT_3_FILE_EXTENSION,
            FORMAT_3_STREAM_SIGNATURE,
            FORMAT_3_PROTOCOL_SCHEME,   // custom protocol scheme
            false);

    @DataProvider(name="resolveCodecForDecodingSucceeds")
    public Object[][] resolveCodecForDecodingSucceeds() {
        return new Object[][]{
                // array of codecs to register, resource bundle, expected display name of resolved codec

                // input IOPath is FORMAT_1, V1_0, resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },

                // input IOPath is FORMAT_1, V1_1, resolve to FORMAT_1_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION, FORMAT_1_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_1_V1_1.getDisplayName() },

                // input IOPath is FORMAT_1, V2_0, resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION, FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_1, V_2_0, with FORMAT_1 content subtype specified in the bundle,
                // resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_0, resolve to FORMAT_2_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_2_V1_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_1, resolve to FORMAT_2_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_2_V1_1.getDisplayName() },

                // input IOPath is FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                true),  // FORMAT_2_V2_0 uses gzipped inputs
                        FORMAT_2_V2_0.getDisplayName() },

                // input is a *input stream* that is FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to  FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                true),    // use gzipped inputs
                        FORMAT_2_V2_0.getDisplayName() },

                // input IOPath is FORMAT_3 custom protocol scheme, resolve to newest version FORMAT_3_V1_0,
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        FORMAT_3_V1_0.getDisplayName() },

                // input is a raw input stream that is FORMAT_1, V1_0, resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingSucceeds")
    public void testResolveCodecForDecodingSucceeds(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final String expectedCodecDisplayName) {
        final HtsCodecsByFormatVersion<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>> testCodecs =
                new HtsCodecsByFormatVersion<>(HtsTestCodecFormat::formatFromContentSubType);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<HtsTestCodecFormat, ?, ?> resolvedCodec = testCodecs.resolveCodecForDecoding(
                bundle,
                TEST_CODEC_CONTENT_TYPE);
        Assert.assertEquals(resolvedCodec.getDisplayName(), expectedCodecDisplayName);
    }

    @DataProvider(name="resolveCodecForDecodingFails")
    public Object[][] resolveCodecForDecodingFails() {
        return new Object[][]{
                // array of codecs to register, a resource bundle, expected exception message fragment

                // input is FORMAT_1, V_2_0, but no codecs registered ...at all
                { Collections.emptyList(),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // input is FORMAT_1, V_2_0, no matching codec for V_2_0 of FORMAT_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // input is FORMAT_2, V2_0, no matching codec for any version of file format FORMAT_2
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // no resource in the bundle has the required content type ("TEST_CONTENT_TYPE")
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                "BOGUS_CONTENT_TYPE",
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        "No resource found in bundle" },

                // the resource in the bundle claims to be the correct content type, for which registered
                // codecs exist, but the file stream signature doesn't match the signature any such codec
                // expects
                //TODO: this should have a better error message, or at least we should issue a warning.
                // that the content type in the bundle resource doesn't match whats in the underlying stream,
                // since thats likely user or programming error.
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                "BOGUS_SIGNATURE" + V1_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // input is FORMAT_2, V_2_0, but with *FORMAT_1* content subtype specified in the bundle;
                // this prunes based on content subtype FORMAT_1, but none of the resulting codecs claim it
                // due to the stream signature being "FORMAT_1"
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // the resource in the bundle claims to be a content type for which a registered codec
                // exists, but the version in the file stream doesn't match the version for any registered
                // codec of that format
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // the resource in the bundle specifies a content subtype that doesn't correspond to any
                // format for this content type
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                "BOGUS_SUB_CONTENT_TYPE",
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        "does not correspond to any known subtype for content type" },

                // the resource of the required content type is an output (OutputStream) resource, not a
                // (readable) input resource
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, null),
                        "cannot be used as an input resource"},

                // input is a raw input stream that is FORMAT_1, V1_0, but the bundle content subtype is
                //TODO: this should have a better error message, or at least we should issue a warning.
                // that the content type in the bundle resource doesn't match whats in the underlying stream,
                // since its likely user or programming error.
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_2_CONTENT_SUBTYPE,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForDecodingFails(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final String expectedMessage) {
        final HtsCodecsByFormatVersion<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>> testCodecs =
                new HtsCodecsByFormatVersion<>(HtsTestCodecFormat::formatFromContentSubType);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        try {
            testCodecs.resolveCodecForDecoding(bundle, TEST_CODEC_CONTENT_TYPE);
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            throw e;
        }
    }

    @DataProvider(name="resolveCodecForEncodingSucceeds")
    public Object[][] resolveCodecForEncodingSucceeds() {
        // Note: unlike inputs, for outputs a version must either be explicitly requested
        // by the caller, or else default to the newest version registered for the format.
        // The format (content subtype) may also need to be explicitly requested, since it may not
        // be discoverable from the output (i.e., if the output is to a raw stream).
        return new Object[][]{
                // array of codecs to register, resource bundle, requested version (or null), expected display name of resolved codec

                // no specific version requested, so resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        null, // no specific requested version
                        FORMAT_1_V1_0.getDisplayName() },

                // no specific version requested, so resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        null, // no specific requested version
                        FORMAT_1_V1_1.getDisplayName() },

                // no specific version requested, so resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        null, // no specific requested version
                        FORMAT_1_V2_0.getDisplayName() },

                // no specific version requested, so resolve to the newest version registered for FORMAT_1, which is
                // FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        null, // no specific requested version
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_0, so resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        FORMAT_1_V1_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_1, so resolve to FORMAT_1_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V1_1,
                        FORMAT_1_V1_1.getDisplayName() },

                // FORMAT_1 file extension, request V2_0, so resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V2_0,
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_2 file extension, request V2_0, so resolve to FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_2 file extension, content subtype FORMAT_2 specified in the bundle, request V2_0,
                // resolve to FORMAT_2_V2_0;
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        getIOPathBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_2_CONTENT_SUBTYPE, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3_V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        null,
                        FORMAT_3_V1_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3_V2_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        null,
                        FORMAT_3_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, version FORMAT_3_V1_0 specified, resolve to FORMAT_3_V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        V1_0,
                        FORMAT_3_V1_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingSucceeds")
    public void testResolveCodecForEncoding(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersionRequested,
            final String expectedCodecDisplayName) {
        final HtsCodecsByFormatVersion<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>> testCodecs =
                new HtsCodecsByFormatVersion<>(HtsTestCodecFormat::formatFromContentSubType);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<HtsTestCodecFormat, ?, ?> resolvedCodec = testCodecs.resolveCodecForEncoding(
                bundle,
                TEST_CODEC_CONTENT_TYPE,
                Optional.ofNullable(htsVersionRequested));
        Assert.assertEquals(resolvedCodec.getDisplayName(), expectedCodecDisplayName);
    }

    @DataProvider(name="resolveCodecForEncodingFails")
    public Object[][] resolveCodecForEncodingFails() {
        return new Object[][]{
                // array of codecs to register, a resource bundle, optional version, expected exception message fragment

                // no codecs registered at all, no content type or version specified
                { Collections.emptyList(),
                        getIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION),
                        null,
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // no codecs registered at all, content type and version specified
                { Collections.emptyList(),
                        getIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // no content subtype specified for IOPath
                //TODO: there should either be an error message that says that no content subtype was specified,
                // or there should be a default content subtype
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        getIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // no content subtype specified for OutputStream (should there be a default for content type?)
                //TODO: there should either be an error message that says that no content subtype was specified,
                // or there should be a default content subtype
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        getOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, null),
                        V1_0,
                        HtsCodecsByFormatVersion.MULTIPLE_SUPPORTING_CODECS_ERROR},

                // content subtype specified for OutputStream (should there be a default for content type?), but
                // requested version isn't registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        getOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_1_CONTENT_SUBTYPE),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // content subtype specified, but specified version not registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        getIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecsByFormatVersion.NO_SUPPORTING_CODEC_ERROR},

                // bundle contains an INPUT stream resource, not an output stream
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        getInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                "irrelevant content",
                                false),
                        V1_0,
                        "cannot be used as an output resource" },
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForEncodingFails(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersion,
            final String expectedMessage) {
        final HtsCodecsByFormatVersion<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>> testCodecs =
                new HtsCodecsByFormatVersion<>(HtsTestCodecFormat::formatFromContentSubType);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        try {
                testCodecs.resolveCodecForEncoding(
                        bundle,
                        TEST_CODEC_CONTENT_TYPE,
                        Optional.ofNullable(htsVersion));
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            throw e;
        }
    }

    @Test(expectedExceptions={RuntimeException.class})
    public void testResolveCodecForEncodingMultipleCodecs() {
        // this test is somewhat contrived since in that its using two codecs that should never
        // really resolve to the same input, but it doesnt matter since the important thing is
        // to exercise the code path to ensure that it works
        try {
            HtsCodecsByFormatVersion.getOneOrThrow(
                    Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                    () -> "test input");
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(HtsCodecsByFormatVersion.MULTIPLE_SUPPORTING_CODECS_ERROR));
            throw e;
        }
    }

    //TODO: make this accept a bytearray instead of string and move to IOUtil once this is
    // branch is rebased on the bundle PR
    private IOPath createTempFileWithContents(final String contents, final String extension, final boolean gzipOutput) {
        Assert.assertTrue(extension.startsWith("."));
        final IOPath tempFile = IOUtils.createTempPath("codecResolution", extension);
        IOUtils.writeStringToPath(tempFile, contents, gzipOutput);
        return tempFile;
    }

    private Bundle getIOPathBundleWithContent(
            final String contentType,
            final String contentSubType,
            final String fileExtension,
            final String contents,
            final boolean gzipOutput) {
        final IOPath tempPath = createTempFileWithContents(contents, fileExtension, gzipOutput);
        final IOPathResource ioPathResource = contentSubType == null ?
                new IOPathResource(tempPath, contentType) :
                new IOPathResource(tempPath, contentType, contentSubType);
        return new BundleBuilder().addPrimary(ioPathResource).build();
    }

    private Bundle getIOPathBundle(
            final String contentType,
            final String contentSubType,
            final String fileExtension) {
        final IOPath tempPath = IOUtils.createTempPath("codecResolution", fileExtension);
        if (contentSubType == null) {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType))
                    .build();
        } else {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType, contentSubType))
                    .build();
        }

    }

    private Bundle getInputStreamBundleWithContent(
            final String contentType,
            final String contentSubType,
            final String contents,
            final boolean gzipInput) {
        final String displayName = "testDisplayName";
        try (final InputStream bis =
                     gzipInput == true ?
                             getInputStreamOnGzippedContent(contents) :
                             new ByteArrayInputStream(contents.getBytes())) {
            final InputStreamResource isr = contentSubType == null ?
                    new InputStreamResource(bis, displayName, contentType) :
                    new InputStreamResource(bis, displayName, contentType, contentSubType);
            return new BundleBuilder().addPrimary(isr).build();
        } catch (final IOException e) {
             throw new RuntimeIOException(e);
        }
    }

    final InputStream getInputStreamOnGzippedContent(final String contents) {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final BlockCompressedOutputStream bcs = new BlockCompressedOutputStream(bos, (File) null)) {
                bcs.write(contents.getBytes());
            }
            final byte array[] = bos.toByteArray();
            return new ByteArrayInputStream(array);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public Bundle getOutputStreamBundle(final String contentType, final String contentSubType) {
        final OutputStream os = new ByteArrayOutputStream();
        return new BundleBuilder()
                .addPrimary(new OutputStreamResource(os, "test stream", contentType, contentSubType))
                .build();
    }

}
