/** This software and associated documentation files (the "Software"), to deal
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

package htsjdk.tribble;

import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * User: jacob
 * Date: 2012-Dec-13
 */
public class TestUtils {
    public static String DATA_DIR = "src/test/resources/htsjdk/tribble/";

    /**
     * A utility method for copying a tribble file (and possibly its index) into a Jimfs-like FileSystem
     *
     * @param vcf The string pointing to the Tribble file
     * @param index a (nullable) string pointing to the index
     * @param fileSystem a (JimFs-like) Filesystem into which the tribble file will be copied
     * @return the {@link Path} to the copied file inside fileSystem
     *
     * @throws IOException if there an error with copying into the FileSystem
     * @throws URISyntaxException if the provided strings cannot be understoos as Uris.
     */

    public static Path getTribbleFileInJimfs(String vcf, Function<SeekableByteChannel, SeekableByteChannel> wrapper, String index, FileSystem fileSystem) throws IOException, URISyntaxException {
        final FileSystem fs = fileSystem;
        final Path root = fs.getPath("/");
        final Path vcfPath = Paths.get(vcf);

        final Path vcfDestination = root.resolve(vcfPath.getFileName().toString());
        if (index != null) {
            final Path idxPath = Paths.get(index);
            final Path idxDestination = isTabix(vcf, index) ? Tribble.tabixIndexPath(vcfDestination) : Tribble.indexPath(vcfDestination);
            Files.copy(idxPath, idxDestination);
        }
        return Files.copy(vcfPath, vcfDestination);
    }

    private static boolean isTabix(String resourcePath, String indexPath) throws IOException {
        if (indexPath == null) {
            indexPath = ParsingUtils.appendToPath(resourcePath, TabixUtils.STANDARD_INDEX_EXTENSION);
        }
        return AbstractFeatureReader.hasBlockCompressedExtension(resourcePath) && ParsingUtils.resourceExists(indexPath);
    }
}
