/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;

import htsjdk.samtools.SAMException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IoUtilTest extends HtsjdkTest {


    private static final File TEST_DATA_DIR = new File ("src/test/resources/htsjdk/samtools/io/");
    private static final File SLURP_TEST_FILE = new File(TEST_DATA_DIR,"slurptest.txt");
    private static final File EMPTY_FILE = new File(TEST_DATA_DIR,"empty.txt");
    private static final File FIVE_SPACES_THEN_A_NEWLINE_THEN_FIVE_SPACES_FILE = new File(TEST_DATA_DIR,"5newline5.txt");
    private static final List<String> SLURP_TEST_LINES = Arrays.asList("bacon   and rice   ", "for breakfast  ", "wont you join me");
    private static final String SLURP_TEST_LINE_SEPARATOR = "\n";
    private static final String TEST_FILE_PREFIX = "htsjdk-IOUtilTest";
    private static final String TEST_FILE_EXTENSIONS[] = {".txt", ".txt.gz"};
    private static final String TEST_STRING = "bar!";
    private File existingTempFile;
    private String systemUser;
    private String systemTempDir;

    private FileSystem inMemoryfileSystem;

    @BeforeClass
    public void setUp() throws IOException {
        existingTempFile = File.createTempFile("FiletypeTest.", ".tmp");
        existingTempFile.deleteOnExit();
        systemTempDir = System.getProperty("java.io.tmpdir");
        final File tmpDir = new File(systemTempDir);
        inMemoryfileSystem = Jimfs.newFileSystem(Configuration.unix());;
        if (!tmpDir.isDirectory()) tmpDir.mkdir();
        if (!tmpDir.isDirectory())
            throw new RuntimeException("java.io.tmpdir (" + systemTempDir + ") is not a directory");
        systemUser = System.getProperty("user.name");
    }

    @AfterClass
    public void tearDown() throws IOException {
        // reset java properties to original
        System.setProperty("java.io.tmpdir", systemTempDir);
        System.setProperty("user.name", systemUser);
        inMemoryfileSystem.close();
    }

    @Test
    public void testFileReadingAndWriting() throws IOException {
        String randomizedTestString = TEST_STRING + System.currentTimeMillis();
        for (String ext : TEST_FILE_EXTENSIONS) {
            File f = File.createTempFile(TEST_FILE_PREFIX, ext);
            f.deleteOnExit();

            OutputStream os = IOUtil.openFileForWriting(f);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(randomizedTestString);
            writer.close();

            InputStream is = IOUtil.openFileForReading(f);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            Assert.assertEquals(randomizedTestString, line);
        }
    }

    @Test(groups = {"unix"})
    public void testGetCanonicalPath() throws IOException {
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name");

        if (tmpPath.endsWith(userName)) {
            tmpPath = tmpPath.substring(0, tmpPath.length() - userName.length());
        }

        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        File actual = new File(tmpDir, "actual.txt");
        ProcessExecutor.execute(new String[]{"touch", actual.getAbsolutePath()});
        File symlink = new File(tmpDir, "symlink.txt");
        ProcessExecutor.execute(new String[]{"ln", "-s", actual.getAbsolutePath(), symlink.getAbsolutePath()});
        File lnDir = new File(tmpDir, "symLinkDir");
        ProcessExecutor.execute(new String[]{"ln", "-s", tmpDir.getAbsolutePath(), lnDir.getAbsolutePath()});
        File lnToActual = new File(lnDir, "actual.txt");
        File lnToSymlink = new File(lnDir, "symlink.txt");


        File files[] = {actual, symlink, lnToActual, lnToSymlink};
        for (File f : files) {
            Assert.assertEquals(IOUtil.getFullCanonicalPath(f), actual.getCanonicalPath());
        }

        actual.delete();
        symlink.delete();
        lnToActual.delete();
        lnToSymlink.delete();
        lnDir.delete();
        tmpDir.delete();
    }

    @Test
    public void testUtfWriting() throws IOException {
        final String utf8 = new StringWriter().append((char) 168).append((char) 197).toString();
        for (String ext : TEST_FILE_EXTENSIONS) {
            final File f = File.createTempFile(TEST_FILE_PREFIX, ext);
            f.deleteOnExit();

            final BufferedWriter writer = IOUtil.openFileForBufferedUtf8Writing(f);
            writer.write(utf8);
            CloserUtil.close(writer);

            final BufferedReader reader = IOUtil.openFileForBufferedUtf8Reading(f);
            final String line = reader.readLine();
            Assert.assertEquals(utf8, line, f.getAbsolutePath());

            CloserUtil.close(reader);

        }
    }

    @Test
    public void slurpLinesTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurpLines(SLURP_TEST_FILE), SLURP_TEST_LINES);
    }

    @Test
    public void slurpWhitespaceOnlyFileTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(FIVE_SPACES_THEN_A_NEWLINE_THEN_FIVE_SPACES_FILE), "     \n     ");
    }

    @Test
    public void slurpEmptyFileTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(EMPTY_FILE), "");
    }

    @Test
    public void slurpTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(SLURP_TEST_FILE), CollectionUtil.join(SLURP_TEST_LINES, SLURP_TEST_LINE_SEPARATOR));
    }

    @Test(dataProvider = "fileTypeTestCases")
    public void testFileType(final String path, boolean expectedIsRegularFile) {
        final File file = new File(path);
        Assert.assertEquals(IOUtil.isRegularPath(file), expectedIsRegularFile);
        if (null != file) {
            Assert.assertEquals(IOUtil.isRegularPath(file.toPath()), expectedIsRegularFile);
        }
    }

    @Test(dataProvider = "unixFileTypeTestCases", groups = {"unix"})
    public void testFileTypeUnix(final String path, boolean expectedIsRegularFile) {
        final File file = new File(path);
        Assert.assertEquals(IOUtil.isRegularPath(file), expectedIsRegularFile);
        if (null != file) {
            Assert.assertEquals(IOUtil.isRegularPath(file.toPath()), expectedIsRegularFile);
        }
    }

    @Test
    public void testAddExtension() throws IOException {
        Path p = IOUtil.getPath("/folder/file");
        List<FileSystemProvider> fileSystemProviders = FileSystemProvider.installedProviders();
        Assert.assertEquals(IOUtil.addExtension(p, ".ext"), IOUtil.getPath("/folder/file.ext"));
        p = IOUtil.getPath("folder/file");
        Assert.assertEquals(IOUtil.addExtension(p, ".ext"), IOUtil.getPath("folder/file.ext"));
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            p = jimfs.getPath("folder/sub/file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("folder/sub/file.ext"));
            p = jimfs.getPath("folder/file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("folder/file.ext"));
            p = jimfs.getPath("file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("file.ext"));
        }
    }

    @DataProvider(name = "fileTypeTestCases")
    private Object[][] fileTypeTestCases() {
        return new Object[][]{
                {existingTempFile.getAbsolutePath(), Boolean.TRUE},
                {systemTempDir, Boolean.FALSE}

        };
    }

    @DataProvider(name = "unixFileTypeTestCases")
    private Object[][] unixFileTypeTestCases() {
        return new Object[][]{
                {"/dev/null", Boolean.FALSE},
                {"/dev/stdout", Boolean.FALSE},
                {"/non/existent/file", Boolean.TRUE},
        };
    }

    @DataProvider(name = "fileNamesForDelete")
    public Object[][] fileNamesForDelete() {
        return new Object[][] {
                {Collections.emptyList()},
                {Collections.singletonList("file1")},
                {Arrays.asList("file1", "file2")}
        };
    }

    @Test
    public void testGetDefaultTmpDirPath() throws Exception {
        try {
            Path testPath = IOUtil.getDefaultTmpDirPath();
            Assert.assertEquals(testPath.toFile().getAbsolutePath(), new File(systemTempDir).getAbsolutePath() + "/" + systemUser);

            // change the properties to test others
            final String newTempPath = Files.createTempDirectory("testGetDefaultTmpDirPath").toString();
            final String newUser = "my_user";
            System.setProperty("java.io.tmpdir", newTempPath);
            System.setProperty("user.name", newUser);
            testPath = IOUtil.getDefaultTmpDirPath();
            Assert.assertEquals(testPath.toFile().getAbsolutePath(), new File(newTempPath).getAbsolutePath() + "/" + newUser);

        } finally {
            // reset system properties
            System.setProperty("java.io.tmpdir", systemTempDir);
            System.setProperty("user.name", systemUser);
        }
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeletePathLocal(final List<String> fileNames) throws Exception {
        final File tmpDir = IOUtil.createTempDir("testDeletePath", "");
        final List<Path> paths = createLocalFiles(tmpDir, fileNames);
        testDeletePath(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeletePathJims(final List<String> fileNames) throws Exception {
        final List<Path> paths = createJimsFiles("testDeletePath", fileNames);
        testDeletePath(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeleteArrayPathLocal(final List<String> fileNames) throws Exception {
        final File tmpDir = IOUtil.createTempDir("testDeletePath", "");
        final List<Path> paths = createLocalFiles(tmpDir, fileNames);
        testDeletePathArray(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeleteArrayPathJims(final List<String> fileNames) throws Exception {
        final List<Path> paths = createJimsFiles("testDeletePath", fileNames);
        testDeletePathArray(paths);
    }

    private static void testDeletePath(final List<Path> paths) {
        paths.forEach(p -> Assert.assertTrue(Files.exists(p)));
        IOUtil.deletePaths(paths);
        paths.forEach(p -> Assert.assertFalse(Files.exists(p)));
    }

    private static void testDeletePathArray(final List<Path> paths) {
        paths.forEach(p -> Assert.assertTrue(Files.exists(p)));
        IOUtil.deletePaths(paths.toArray(new Path[paths.size()]));
        paths.forEach(p -> Assert.assertFalse(Files.exists(p)));
    }

    private static List<Path> createLocalFiles(final File tmpDir, final List<String> fileNames) throws Exception {
        final List<Path> paths = new ArrayList<>(fileNames.size());
        for (final String f: fileNames) {
            final File file = new File(tmpDir, f);
            file.createNewFile();
            paths.add(file.toPath());
        }
        return paths;
    }

    private List<Path> createJimsFiles(final String folderName, final List<String> fileNames) throws Exception {
        final List<Path> paths = new ArrayList<>(fileNames.size());
        final Path folder = inMemoryfileSystem.getPath(folderName);
        if (Files.notExists(folder)) Files.createDirectory(folder);

        for (final String f: fileNames) {
            final Path p = inMemoryfileSystem.getPath(folderName, f);
            Files.createFile(p);
            paths.add(p);
        }

        return paths;
    }

    @DataProvider
    public Object[][] pathsForDeletePathThread() throws Exception {
        return new Object[][] {
                {File.createTempFile("testDeletePathThread", "file").toPath()},
                {Files.createFile(inMemoryfileSystem.getPath("testDeletePathThread"))}
        };
    }

    @Test(dataProvider = "pathsForDeletePathThread")
    public void testDeletePathThread(final Path path) throws Exception {
        Assert.assertTrue(Files.exists(path));
        new IOUtil.DeletePathThread(path).run();
        Assert.assertFalse(Files.exists(path));
    }

    @DataProvider
    public Object[][] pathsForWritableDirectory() throws Exception {
        return new Object[][] {
                // non existent
                {inMemoryfileSystem.getPath("no_exists"), false},
                // non directory
                {Files.createFile(inMemoryfileSystem.getPath("testAssertDirectoryIsWritable_file")), false},
                // TODO - how to do in inMemmoryFileSystem a non-writable directory?
                // writable directory
                {Files.createDirectory(inMemoryfileSystem.getPath("testAssertDirectoryIsWritable_directory")), true}
        };
    }

    @Test(dataProvider = "pathsForWritableDirectory")
    public void testAssertDirectoryIsWritablePath(final Path path, final boolean writable) {
        try {
            IOUtil.assertDirectoryIsWritable(path);
        } catch (SAMException e) {
            if (writable) {
                Assert.fail(e.getMessage());
            }
        }
    }

    @DataProvider
    public Object[][] filesForWritableDirectory() throws Exception {
        final File nonWritableFile = new File(systemTempDir, "testAssertDirectoryIsWritable_non_writable_dir");
        nonWritableFile.mkdir();
        nonWritableFile.setWritable(false);

        return new Object[][] {
                // non existent
                {new File("no_exists"), false},
                // non directory
                {existingTempFile, false},
                // non-writable directory
                {nonWritableFile, false},
                // writable directory
                {new File(systemTempDir), true},
        };
    }

    @Test(dataProvider = "filesForWritableDirectory")
    public void testAssertDirectoryIsWritableFile(final File file, final boolean writable) {
        try {
            IOUtil.assertDirectoryIsWritable(file);
        } catch (SAMException e) {
            if (writable) {
                Assert.fail(e.getMessage());
            }
        }
    }
    @DataProvider
    public Object[][] fofnData() throws IOException {
        Path fofnPath1 = inMemoryfileSystem.getPath("Level1.fofn");
        Files.copy(new File(TEST_DATA_DIR.getAbsolutePath(),"Level1.fofn").toPath(), fofnPath1);

        Path fofnPath2 = inMemoryfileSystem.getPath("Level2.fofn");
        Files.copy(new File(TEST_DATA_DIR.getAbsolutePath(),"Level2.fofn").toPath(), fofnPath2);

        return new Object[][]{
                {TEST_DATA_DIR.getAbsolutePath() + "/Level1.fofn", new String[]{".vcf", ".vcf.gz"}, 2},
                {TEST_DATA_DIR.getAbsolutePath() + "/Level2.fofn", new String[]{".vcf", ".vcf.gz"}, 4},
                {fofnPath1.toUri().toString(), new String[]{".vcf", ".vcf.gz"}, 2},
                {fofnPath2.toUri().toString(), new String[]{".vcf", ".vcf.gz"}, 4}
        };
    }

    @Test(dataProvider = "fofnData")
    public void testUnrollPaths(final String pathUri, final String[] extensions, final int expectedNumberOfUnrolledPaths) throws IOException {
        Path p = IOUtil.getPath(pathUri);
        List<Path> paths = IOUtil.unrollPaths(Collections.singleton(p), extensions);

        Assert.assertEquals(paths.size(), expectedNumberOfUnrolledPaths);
    }
}
