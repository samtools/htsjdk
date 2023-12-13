package htsjdk.io;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import org.apache.commons.lang3.SystemUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.Optional;

public class HtsPathUnitTest extends HtsjdkTest {

    final static String FS_SEPARATOR = FileSystems.getDefault().getSeparator();

    @DataProvider
    public Object[][] validHtsPath() {
        return new Object[][] {
                // HtsPath strings that are syntactically valid as either a file name or a URI and can be
                // represented internally as a URI, but which may fail hasFileSystemProvider or isPath

                // input String, expected resulting URI String, expected hasFileSystemProvider, expected isPath

                //********************************
                // Local (non-URI) file references
                //********************************

                {"localFile.bam",                   "file://" + getCWDAsURIPathString() + "localFile.bam", true, true},
                // absolute reference to a file in the root of the current file system (Windows accepts the "/" as root)
                {"/localFile.bam",                  "file://" + getRootDirectoryAsURIPathString() + "localFile.bam", true, true},
                // absolute reference to a file in the root of the current file system, where root is specified using the
                // default FS separator
                {FS_SEPARATOR + "localFile.bam",  "file://" + getRootDirectoryAsURIPathString() + "localFile.bam", true, true},
                // absolute reference to a file
                {FS_SEPARATOR + joinWithFSSeparator("path", "to", "localFile.bam"),
                        "file://" + getRootDirectoryAsURIPathString() + "path/to/localFile.bam",  true, true},
                // absolute reference to a file that contains a URI excluded character in the path ("#") which without
                // encoding will be treated as a fragment delimiter
                {FS_SEPARATOR + joinWithFSSeparator("project", "gvcf-pcr", "23232_1#1", "1.g.vcf.gz"),
                        "file://" + getRootDirectoryAsURIPathString() + "project/gvcf-pcr/23232_1%231/1.g.vcf.gz", true, true},
                // relative reference to a file on the local file system (relative to the current working directory)
                {joinWithFSSeparator("path", "to", "localFile.bam"),
                        "file://" + getCWDAsURIPathString() + "path/to/localFile.bam", true, true},
                // Windows also accepts "/" as a valid root specifier
                {"/", "file://" + getRootDirectoryAsURIPathString(), true, true},
                {".", "file://" + getCWDAsURIPathString() + "./", true, true},
                {"../.", "file://" + getCWDAsURIPathString() + ".././", true, true},
                // an empty path is equivalent to accessing the current directory of the default file system
                {"", "file://" + getCWDAsURIPathString(), true, true},

                //***********************************************************
                // Local file references using a URI with a "file://" scheme.
                //***********************************************************

                {"file:localFile.bam",              "file:localFile.bam",           true, false}, // absolute, opaque (not hierarchical)
                {"file:/localFile.bam",             "file:/localFile.bam",          true, true},  // absolute, hierarchical
                {"file://localFile.bam",            "file://localFile.bam",         true, false}, // file URLs can't have an authority ("localFile.bam")
                {"file:///localFile.bam",           "file:///localFile.bam",        true, true},  // empty authority
                {"file:path/to/localFile.bam",      "file:path/to/localFile.bam",   true, false},
                {"file:/path/to/localFile.bam",     "file:/path/to/localFile.bam",  true, true},
                // "path" looks like an authority, and will be accepted on Windows since it will be interpreted as a UNC authority
                {"file://path/to/localFile.bam",    "file://path/to/localFile.bam", true, SystemUtils.IS_OS_WINDOWS},
                // "localhost" is accepted as a special case authority for "file://" Paths on Windows; but not Linux
                {"file://localhost/to/localFile.bam","file://localhost/to/localFile.bam", true, SystemUtils.IS_OS_WINDOWS},
                {"file:///path/to/localFile.bam",   "file:///path/to/localFile.bam",    true, true},  // empty authority

                //*****************************************************************************
                // Valid URIs which are NOT valid NIO paths (no installed file system provider)
                //*****************************************************************************

                {"gs://file.bam",                   "gs://file.bam",                    false, false},
                {"gs://bucket/file.bam",            "gs://bucket/file.bam",             false, false},
                {"gs:///bucket/file.bam",           "gs:///bucket/file.bam",            false, false},
                {"gs://auth/bucket/file.bam",       "gs://auth/bucket/file.bam",        false, false},
                {"gs://hellbender/test/resources/", "gs://hellbender/test/resources/",  false, false},
                {"gcs://abucket/bucket",            "gcs://abucket/bucket",             false, false},
                {"gendb://somegdb",                 "gendb://somegdb",                  false, false},
                {"chr1:1-100",                      "chr1:1-100",                       false, false},
                {"ftp://broad.org/file",            "ftp://broad.org/file",             false, false},
                {"ftp://broad.org/with%20space",    "ftp://broad.org/with%20space",     false, false},

                //**********************************************************************************************
                // Valid URIs which ARE valid NIO URIs (there *IS* an installed file system provider), but are
                // not actually resolvable as paths because the scheme-specific part is not valid for one reason
                // or another.
                //**********************************************************************************************

                // uri must have a path: jimfs:file.bam
                {"jimfs:file.bam",      "jimfs:file.bam", true, false},
                // java.lang.AssertionError: java.net.URISyntaxException: Expected scheme-specific part at index 6: jimfs:
                {"jimfs:/file.bam",     "jimfs:/file.bam", true, false},
                // java.lang.AssertionError: uri must have a path: jimfs://file.bam
                {"jimfs://file.bam",    "jimfs://file.bam", true, false},
                // java.lang.AssertionError: java.net.URISyntaxException: Expected scheme-specific part at index 6: jimfs:
                {"jimfs:///file.bam",   "jimfs:///file.bam", true, false},
                // java.nio.file.FileSystemNotFoundException: jimfs://root
                {"jimfs://root/file.bam","jimfs://root/file.bam", true, false},

                //*****************************************************************************************
                // Reference that contain characters that require URI-encoding. If the input string is presented
                // with no scheme, it will be automatically encoded by HtsPath, otherwise it
                // must already be URI-encoded.
                //*****************************************************************************************

                // relative (non-URI) reference to a file on the local file system that contains a URI fragment delimiter
                // is automatically URI-encoded
                {joinWithFSSeparator("project", "gvcf-pcr", "23232_1#1", "1.g.vcf.gz"),
                        "file://" + getCWDAsURIPathString() + "project/gvcf-pcr/23232_1%231/1.g.vcf.gz", true, true},
                // URI references with fragment delimiter is not automatically URI-encoded
                {"file:project/gvcf-pcr/23232_1#1/1.g.vcf.gz",  "file:project/gvcf-pcr/23232_1#1/1.g.vcf.gz", true, false},
                {"file:/project/gvcf-pcr/23232_1#1/1.g.vcf.gz", "file:/project/gvcf-pcr/23232_1#1/1.g.vcf.gz", true, false},
                {"file:///project/gvcf-pcr/23232_1%231/1.g.vcf.g", "file:///project/gvcf-pcr/23232_1%231/1.g.vcf.g", true, true},

                {"http://host/path://", "http://host/path://", false, false}
        };
    }

    @Test(dataProvider = "validHtsPath")
    public void testValidHtsPath(final String referenceString, final String expectedURIString, final boolean hasFileSystemProvider, final boolean isPath) {
        final IOPath IOPath = new HtsPath(referenceString);
        Assert.assertNotNull(IOPath);
        Assert.assertEquals(IOPath.getURI().toString(), expectedURIString);
    }

    @Test(dataProvider = "validHtsPath")
    public void testIsNIO(final String referenceString, final String expectedURIString, final boolean hasFileSystemProvider, final boolean isPath) {
        final IOPath IOPath = new HtsPath(referenceString);
        Assert.assertEquals(IOPath.hasFileSystemProvider(), hasFileSystemProvider);
    }

    @Test(dataProvider = "validHtsPath")
    public void testIsPath(final String referenceString, final String expectedURIString, final boolean hasFileSystemProvider, final boolean isPath) {
        final IOPath IOPath = new HtsPath(referenceString);
        if (isPath) {
            Assert.assertEquals(IOPath.isPath(), isPath, IOPath.getToPathFailureReason());
        } else {
            Assert.assertEquals(IOPath.isPath(), isPath);
        }
    }

    @Test(dataProvider = "validHtsPath")
    public void testToPath(final String referenceString, final String expectedURIString, final boolean hasFileSystemProvider, final boolean isPath) {
        final IOPath IOPath = new HtsPath(referenceString);
        if (isPath) {
            final Path path = IOPath.toPath();
            Assert.assertEquals(path != null, isPath, IOPath.getToPathFailureReason());
        } else {
            Assert.assertEquals(IOPath.isPath(), isPath);
        }
    }

    @DataProvider
    public Object[][] invalidHtsPath() {
        return new Object[][] {
                // the nul character is rejected on all of the supported platforms in both local
                // filenames and URIs, so use it to test HtsPath constructor failure on all platforms
                {"\0"},

                // this has a non-file scheme but isn't encoded properly, best to reject these with
                // a helpful error message than to continue on and treat it as a file:// path
                {"ftp://broad.org/file with space"},

                // if you have a scheme you need something with it
                {"file://"},
                {"http://"}

        };
    }

    @Test(dataProvider = "invalidHtsPath", expectedExceptions = {IllegalArgumentException.class})
    public void testInvalidHtsPath(final String referenceString) {
        new HtsPath(referenceString);
    }

    @DataProvider
    public Object[][] invalidPath() {
        return new Object[][] {
                // valid references that are not valid as a path

                {"file:/project/gvcf-pcr/23232_1#1/1.g.vcf.gz"},    // not encoded
                {"file:project/gvcf-pcr/23232_1#1/1.g.vcf.gz"},     // scheme-specific part is not hierarchical

                // The hadoop file system provider explicitly throws an NPE if no host is specified and HDFS is not
                // the default file system
                //{"hdfs://nonexistent_authority/path/to/file.bam"},  // unknown authority "nonexistent_authority"
                {"hdfs://userinfo@host:80/path/to/file.bam"},           // UnknownHostException "host"

                {"unknownscheme://foobar"},
                {"gendb://adb"},

                {"gcs://abucket/bucket"},

                // URIs with schemes that are backed by an valid NIO provider, but for which the
                // scheme-specific part is not valid.
                {"file://nonexistent_authority/path/to/file.bam"},  // unknown authority "nonexistent_authority"


        };
    }

    @Test(dataProvider = "invalidPath")
    public void testIsPathInvalid(final String invalidPathString) {
        final IOPath ioPath = new HtsPath(invalidPathString);
        Assert.assertFalse(ioPath.isPath());
    }

    @Test(dataProvider = "invalidPath", expectedExceptions = {
            IllegalArgumentException.class, FileSystemNotFoundException.class, ProviderNotFoundException.class})
    public void testToPathInvalid(final String invalidPathString) {
        final IOPath ioPath = new HtsPath(invalidPathString);
        ioPath.toPath();
    }

    @Test
    public void testInstalledNonDefaultFileSystem() throws IOException {
        // create a jimfs file system and round trip through HtsPath/stream
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path outputPath = jimfs.getPath("alternateFileSystemTest.txt");
            doStreamRoundTrip(outputPath.toUri().toString());
        }
    }

    @DataProvider
    public Object[][] inputStreamPaths() {
        return new Object[][]{
                // references that can be resolved to an actual test file that can be read

                //"src/test/resources/org/broadinstitute/hellbender/tools/testTextFile.txt"
                // relative (file) reference to a local file
                {joinWithFSSeparator("src", "test", "resources", "htsjdk", "io", "testTextFile.txt"), "Test file."},

                // absolute reference to a local file
                {getCWDAsFileReference() + FS_SEPARATOR + joinWithFSSeparator("src", "test", "resources", "htsjdk", "io", "testTextFile.txt"), "Test file."},

                // URI reference to a local file, where the path is absolute
                {"file://" + getCWDAsURIPathString() + "src/test/resources/htsjdk/io/testTextFile.txt", "Test file."},

                // reference to a local file with an embedded fragment delimiter ("#") in the name; if the file
                // scheme is included, the rest of the path must already be encoded; if no file scheme is
                // included, the path is encoded by the HtsPath class
                {joinWithFSSeparator("src", "test", "resources", "htsjdk", "io", "testDirWith#InName", "testTextFile.txt"), "Test file."},
                {"file://" + getCWDAsURIPathString() + "src/test/resources/htsjdk/io/testTextFile.txt", "Test file."},
        };
    }

    @Test(dataProvider = "inputStreamPaths")
    public void testGetInputStream(final String referenceString, final String expectedFileContents) throws IOException {
        final IOPath ioPath = new HtsPath(referenceString);

        try (final InputStream is = ioPath.getInputStream();
             final DataInputStream dis = new DataInputStream(is)) {
            final byte[] actualFileContents = new byte[expectedFileContents.length()];
            dis.readFully(actualFileContents);

            Assert.assertEquals(new String(actualFileContents), expectedFileContents);
        }
    }

    @DataProvider
    public Object[][] outputStreamPaths() throws IOException {
        return new Object[][]{
                // output URIs that can be resolved to an actual test file
                {File.createTempFile("testOutputStream", ".txt").toString()},
                {"file://" + getLocalFileAsURIPathString(File.createTempFile("testOutputStream", ".txt").toPath())},
        };
    }

    @Test(dataProvider = "outputStreamPaths")
    public void testGetOutputStream(final String referenceString) throws IOException {
        doStreamRoundTrip(referenceString);
    }

    @Test
    public void testStdIn() throws IOException {
        final IOPath ioPath = new HtsPath(
                SystemUtils.IS_OS_WINDOWS ?
                        "-" :
                        "/dev/stdin");
        try (final InputStream is = ioPath.getInputStream();
             final DataInputStream dis = new DataInputStream(is)) {
            final byte[] actualFileContents = new byte[0];
            dis.readFully(actualFileContents);

            Assert.assertEquals(new String(actualFileContents), "");
        }
    }

    @Test
    public void testStdOut() throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            // stdout is not addressable as a device in the file system namespace on Windows, so skip
            throw new SkipException(("No stdout test on Windows"));
        } else {
            final IOPath ioPath = new HtsPath("/dev/stdout");
            try (final OutputStream os = ioPath.getOutputStream();
                 final DataOutputStream dos = new DataOutputStream(os)) {
                dos.write("some stuff".getBytes());
            }
        }
    }

    @DataProvider(name = "getExtensionTestCases")
    public Object[][] getExtensionTestCases() {
        return new Object[][] {
                // input, extension as returned by getExtension
                {"localFile.bam", ".bam"},
                {"localFile.BAM", ".BAM"},
                {"/localFile.bam", ".bam"},
                {"gs://bucket/aFile.bam", ".bam"},
                {"gs://hellbender/test/resources/aFile.adam", ".adam"},
                {"gs://hellbender/test/resources/aFile.fasta", ".fasta"},
                {"http://bucket/aFile.bam?query=param", ".bam"},

                // getExtension() returns ".gz" (note this case also satisfies hasExtension(".fasta.gz")
                {"aFile.fasta.gz", ".gz"},
                // extension, but basename is ".fasta"!
                {".fasta.gz", ".gz"},
                // extension, but no basename
                { "/.fasta", ".fasta"}
        };
    }

    @Test(dataProvider = "getExtensionTestCases")
    public void testGetExtension(final String pathString, final String expectedExtension) {
        final IOPath ioPath = new HtsPath(pathString);
        final Optional<String> actualExtension = ioPath.getExtension();

        Assert.assertEquals(actualExtension.get(), expectedExtension);
        // also verify that hasExtension(getExtension()) is always true
        Assert.assertTrue(ioPath.hasExtension(actualExtension.get()));
    }

    @DataProvider(name="negativeGetExtensionTestCases")
    public Object[][] negativeGetExtensionTestCases() {
        return new Object[][]{
                // final name component is missing -> empty
                {""},
                {"/"},
                {"."},
                {"gs://hellbender/test/resources/"},
                {"gs://hellbender/test/resources/?query=param"},
                // final component, but no extension -> empty
                {"localFile"},
                {"localFile."},
                {"/localFile."},
                {"gs://hellbender/test/resources/localFile"},
                {"gs://hellbender/test/resources/localFile?query=param"},
        };
    }

    @Test(dataProvider = "negativeGetExtensionTestCases")
    public void testNegativeGetExtension(final String pathString) {
        Assert.assertFalse(new HtsPath(pathString).getExtension().isPresent());
    }

    @DataProvider(name = "hasExtensionTestCases")
    public Object[][] hasExtensionTestCases() {
        return new Object[][]{
                // input, extension that satisfies "hasExtension"
                {"localFile.bam", ".bam" },
                {"localFile.BAM", ".BAM" },
                {"localFile.BAM", ".bam" },
                {"localFile.bam", ".BAM" },
                {"/localFile.bam", ".bam" },
                {"gs://bucket/aFile.bam", ".bam" },
                {"gs://hellbender/test/resources/aFile.adam", ".adam" },
                {"gs://hellbender/test/resources/aFile.fasta", ".fasta" },
                {"http://bucket/aFile.bam?query=param", ".bam" },

                {"aFile.fasta.gz", ".gz" },
                {"aFile.fasta.gz", ".fasta.gz" },
                // basename is ".fasta"!
                {".fasta.gz", ".gz" },
                {".fasta.gz", ".fasta.gz" },
        };
    }

    @Test(dataProvider = "hasExtensionTestCases")
    public void testHasExtension(final String pathString, final String extension) {
        Assert.assertTrue(new HtsPath(pathString).hasExtension(extension));
    }

    @DataProvider(name = "negativeHasExtensionTestCases")
    public Object[][] negativeHasExtensionTestCases() {
        return new Object[][]{
                // no extensions
                {"/", ".ext" },
                {".", ".ext" }, // this gets turned in a URI that ends in "/./"
                {"localFile", ".ext" },
                {"localFile.", ".ext" },
                {"localFile.", ".a" },
                {"localFile", ".a" },
                {"localFile.", ".a" },
                {"gs://hellbender/test/resources", ".fasta" },
                {"gs://hellbender/test/resources?query=param", ".fasta" },
                {"gs://hellbender/test/resources/", ".fasta" },
                {"gs://hellbender/test/resources/?query=param", ".fasta" },
                {"chr1:18502956", ".cram"},
                {"chr1://18502956", ".cram"},
        };
    }

    @Test(dataProvider = "negativeHasExtensionTestCases")
    public void testNegativeHasExtension(final String pathString, final String extension) {
        Assert.assertFalse(new HtsPath(pathString).hasExtension(extension));
    }

    @DataProvider(name = "illegalHasExtensionTestCases")
    public Object[][] illegalHasExtensionTestCases() {
        return new Object[][]{
                {"localFile", "."}, // extension must have length > 0
                {"localFile.", "."},
                {"localFile.ext", "."},
                {"localFile.ext", "a"}, // extension must start with "."
        };
    }

    @Test(dataProvider = "illegalHasExtensionTestCases", expectedExceptions = IllegalArgumentException.class)
    public void testIllegalHasExtension(final String pathString, final String extension) {
        new HtsPath(pathString).hasExtension(extension);
    }

    @DataProvider(name = "getBaseNameTestCases")
    public Object[][] getBaseNameTestCases() {
        return new Object[][] {
                // input, baseName
                {"localFile", "localFile"},
                {"localFile.bam", "localFile"},
                {"localFile.BAM", "localFile"},
                {"localFile.", "localFile"},
                {"/localFile.bam", "localFile"},
                {"/localFile.", "localFile"},
                {"gs://bucket/aFile.bam", "aFile"},
                {"gs://hellbender/test/resources/aFile.adam", "aFile"},
                {"gs://hellbender/test/resources/aFile.fasta", "aFile"},
                {"http://bucket/aFile.bam?query=param", "aFile"},

                // This case satisfies hasExtension(".fasta.gz"), but getExtension() returns ".gz".
                {"aFile.fasta.gz", "aFile.fasta"},
                // basename is ".fasta"!
                {".fasta.gz", ".fasta",},

                {"gs://hellbender/test/somefile", "somefile"},
                {"gs://hellbender/test/somefile?query=param", "somefile"},
                {"aFile.fasta.gz", "aFile.fasta"},
                // basename is ".fasta"!
                {".fasta.gz", ".fasta"},
        };
    }

    @Test(dataProvider = "getBaseNameTestCases")
    public void testGetBaseName(final String pathString, final String baseName) {
        final Optional<String> actualBaseName = new HtsPath(pathString).getBaseName();
        Assert.assertTrue(actualBaseName.isPresent());
        Assert.assertEquals(actualBaseName.get(), baseName);
    }

    @DataProvider(name="negativeGetBaseNameTestCases")
    public Object[][] negativeGetBaseNameTestCases() {
        return new Object[][]{
                // final name component is missing -> empty
                {""},
                {"/"},
                {"."},
                {"gs://hellbender/test/resources/"},
                {"gs://hellbender/test/resources/?query=param"},
                // final component, with extension, but no basename
                { "/.fasta"},
                {"/name/.fasta"},
                {"gs://hellbender/test/resources/.fasta"},
                {"gs://hellbender/test/resources/.fasta?query=param"},
        };
    }

    @Test(dataProvider = "negativeGetBaseNameTestCases")
    public void testNegativeGetBaseName(final String pathString) {
        final Optional<String> actualBaseName = new HtsPath(pathString).getBaseName();
        Assert.assertFalse(actualBaseName.isPresent());
    }

    @DataProvider(name="isFastaTestCases")
    public Object[][] isFastaTestCases() {
        final String twoBitRefURL = "human_g1k_v37.20.21.2bit";
        final String fastaRef = "human_g1k_v37.20.21.fasta";
        return new Object[][] {
                { twoBitRefURL, false },
                { "file:///" + twoBitRefURL, false },
                { fastaRef, true }, // gzipped
                { "file:///" + fastaRef, true }, // gzipped
                // dummy query params at the end to make sure URI.getPath does the right thing
                { "file:///" + fastaRef + "?query=param", true}
        };
    }

    @Test(dataProvider = "isFastaTestCases")
    public void testIsFasta(final String referencePathString, final boolean expectedIsFasta) {
        Assert.assertEquals(new HtsPath(referencePathString).isFasta(), expectedIsFasta);
    }

    @DataProvider(name="isSamTestCases")
    public Object[][] isSamTestCases() {
        return new Object[][] {
                { "my.sam", true },
                { "my.Sam", true },
                { "my.SAM", true },
                {"http://bucket/aFile.sam?query=param", true},

                { "my.bam", false },
                { "my.cram", false },
        };
    }

    @Test(dataProvider = "isSamTestCases")
    public void testIsSam(final String pathString, final boolean expectedMatch) {
        Assert.assertEquals(new HtsPath(pathString).isSam(), expectedMatch);
    }

    @DataProvider(name="isBamTestCases")
    public Object[][] isBamTestCases() {
        return new Object[][] {
                { "my.bam", true },
                { "my.Bam", true },
                { "my.BAM", true },
                {"http://bucket/aFile.bam?query=param", true},

                { "my.sam", false },
                { "my.cram", false },
        };
    }

    @Test(dataProvider = "isBamTestCases")
    public void testIsBam(final String pathString, final boolean expectedMatch) {
        Assert.assertEquals(new HtsPath(pathString).isBam(), expectedMatch);
    }

    @DataProvider(name="isCramTestCases")
    public Object[][] isCramTestCases() {
        return new Object[][] {
                { "my.cram", true },
                { "my.Cram", true },
                { "my.CRAM", true },
                {"http://bucket/aFile.cram?query=param", true},

                { "my.sam", false },
                { "my.bam", false },
        };
    }

    @Test(dataProvider = "isCramTestCases")
    public void testIsCram(final String pathString, final boolean expectedMatch) {
        Assert.assertEquals(new HtsPath(pathString).isCram(), expectedMatch);
    }

    @Test(dataProvider = "validHtsPath")
    public void testEqualsHash(
            final String htsPathString,
            final String unusedExpectedURIString,
            final boolean unusedHasFileSystemProvider,
            final boolean unusedIsPath) {
        final HtsPath originalPath = new HtsPath(htsPathString);
        final HtsPath htsPathCopy = new HtsPath(originalPath);

        Assert.assertEquals(originalPath.getRawInputString(), htsPathCopy.getRawInputString());
        Assert.assertEquals(originalPath.getURI(), htsPathCopy.getURI());

        Assert.assertEquals(originalPath, htsPathCopy);
        Assert.assertEquals(originalPath.hashCode(), htsPathCopy.hashCode());
    }
    
    @Test(dataProvider = "validHtsPath")
    public void testCopyConstructor(
            final String htsPathString,
            final String unusedExpectedURIString,
            final boolean unusedHasFileSystemProvider,
            final boolean unusedIsPath) {
        final HtsPath originalPath = new HtsPath(htsPathString);
        final HtsPath pathCopy = new HtsPath(originalPath);
        Assert.assertEquals(originalPath, pathCopy);
    }

    /**
     * Return the string resulting from joining the individual components using the local default
     * file system separator.
     *
     * This is used to create test inputs that are local file references, as would be presented by a
     * user on the platform on which these tests are running.
     */
    private String joinWithFSSeparator(String... parts) {
        return String.join(FileSystems.getDefault().getSeparator(), parts);
    }

    private void doStreamRoundTrip(final String referenceString) throws IOException {
        final String expectedFileContents = "Test contents";

        final IOPath IOPath = new HtsPath(referenceString);
        try (final OutputStream os = IOPath.getOutputStream();
             final DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(expectedFileContents.getBytes());
        }

        // read it back in and make sure it matches expected contents
        try (final InputStream is = IOPath.getInputStream();
             final DataInputStream dis = new DataInputStream(is)) {
            final byte[] actualFileContents = new byte[expectedFileContents.length()];
            dis.readFully(actualFileContents);

            Assert.assertEquals(new String(actualFileContents), expectedFileContents);
        }
    }

    /**
     * Get an absolute reference to the current working directory using local file system syntax and
     * the local file system separator. Used to construct valid local, absolute file references as test inputs.
     *
     * Returns /Users/user/=
     */
    private static String getCWDAsFileReference() {
        return new File(".").getAbsolutePath();
    }

    /**
     * Get the current working directory as a locally valid, hierarchical URI string. Used to
     * construct expected URI string values for test inputs that are local file references.
     */
    private String getCWDAsURIPathString() {
        return getLocalFileAsURIPathString(Paths.get("."));
    }

    /**
     * Get just the path part of the URI representing the current working directory. Used
     * to construct expected URI string values for test inputs that specify a file in the
     * root of the local file system.
     *
     * This will return a string of the form "/" on *nix and "d:/" on Windows (its a URI string).
     */
    private String getRootDirectoryAsURIPathString() {
        return getLocalFileAsURIPathString(Paths.get(FS_SEPARATOR));
    }

    /**
     * Get just the path part of the URI representing a file on the local file system. This
     * uses java.io.File to get a locally valid file reference, which is then converted to
     * a URI.
     */
    private String getLocalFileAsURIPathString(final Path localPath) {
        return localPath.toUri().normalize().getPath();
    }

    @DataProvider
    public Object[][] getNonProblematic() {
        return new Object[][]{
                // URI is unencoded but no problems with a scheme
                {"file/ name-sare/ :wierd-"},
                {"hello there"},

                //schemes schemes everywheret
                {"file://://"},
                {"file://://://"},
                {"file://something://"},
                {"file://something://somoethingelse"},

                // file scheme is deliberately ignored here since it's handled specially later
                {"file://unencoded file names/ are/ok!"},

                //these aren't invalid URIs so they should never be tested by this method, they'll pass it though

                {"eep/ee:e:e:ep"},
                {"file:///"}
        };
    }
    @Test(dataProvider = "getNonProblematic")
    public void testAssertNoProblematicScheme(String path){
        HtsPath.assertNoProblematicScheme(path, null);
    }

    @DataProvider
    public Object[][] getProblematic(){
        return new Object[][]{

                // This is the primary use case, to detect unencoded uris that were intended to be encoded.
                // Note that assertNoProblematicScheme doesn't check for issues constructing the URI itself
                // it is only called after a URI parsing exception has already occured.
                {"http://forgot.com/to encode"},
                {"ftp://server.com/file name.txt"},

                {"://"},
                {"://://"},
                {"://://://"},

                //this is technically a valid file name, but it seems very unlikely that anyone would do this delierately
                //better to call it out
                {"://forgotmyscheme"},

                //invalid  URI, needs the rest of the path
                {"file://"},
                {"http://"},

                //This gets rejected but it should never reach here because it's not an invalid URI
                {"http://thisIsOkButItwouldNeverGetHere/something?file=thisone#righthere"},
        };
    }

    @Test(dataProvider = "getProblematic", expectedExceptions = IllegalArgumentException.class)
    public void testAssertNoProblematicSchemeRejectedCases(String path){
        HtsPath.assertNoProblematicScheme(path, null);
    }
}
