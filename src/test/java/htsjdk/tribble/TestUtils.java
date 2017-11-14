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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * User: jacob
 * Date: 2012-Dec-13
 */
public class TestUtils {
    public static String DATA_DIR = "src/test/resources/htsjdk/tribble/";


    public static Path getTribbleFileInJimfs(String vcf, String index, FileSystem fileSystem) throws IOException, URISyntaxException {
        final FileSystem fs = fileSystem;
        final Path root = fs.getPath("/");
        final Path vcfPath = Paths.get(vcf);
        if (index != null) {
            final Path idxPath = Paths.get(index);
            final Path idxDestination = Paths.get(AbstractFeatureReader.isTabix(vcf, index) ? Tribble.tabixIndexFile(vcf) : Tribble.indexFile(vcf));
            Files.copy(idxPath, root.resolve(idxDestination.getFileName().toString()));
        }
        return Files.copy(vcfPath, root.resolve(vcfPath.getFileName().toString()));
    }
}
