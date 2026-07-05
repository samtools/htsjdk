/*
 * Copyright (c) 2010, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.tribble.example;

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.linear.LinearIndex;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class IndexToTable {

    /**
     * this class:
     *  1) checks to see that the feature file exists
     *  2) loads an index from disk, if one doesn't exist, it creates it and writes it to disk
     *  3) Converts the index to a human readable table
     *  @see htsjdk.tribble.index.linear.LinearIndex#writeTable(java.io.PrintStream)
     *
     * @param args 2 parameters:
     *             1) The path of the file to index
     *             2) The output file path
     */
    public static void main(String[] args) {

        // check yourself before you wreck yourself - we require one arg, the input file
        if (args.length != 2) printUsage();

        try {
            // LinearIndex.enableAdaptiveIndexing = false;
            // Resolve the input in a scheme-aware way so file/http/https/ftp/gcs/custom NIO SPI
            // inputs all work; loadIndex(String) does the actual scheme-aware stream opening. Local
            // (default filesystem) inputs are made absolute to preserve the prior File behavior,
            // while remote inputs are passed through unchanged.
            final Path inputPath = IOUtil.getPath(args[0]);
            final String indexSource = inputPath.getFileSystem() == FileSystems.getDefault()
                    ? inputPath.toAbsolutePath().toString()
                    : args[0];
            LinearIndex idx = (LinearIndex) IndexFactory.loadIndex(indexSource);

            final Path outputPath = IOUtil.getPath(args[1]);
            idx.writeTable(new PrintStream(Files.newOutputStream(outputPath)));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * print usage information
     */
    public static void printUsage() {
        System.err.println("Usage: java -jar IndexToTable.jar index.file output.table");
        System.exit(1);
    }
}
