/*
* Copyright (c) 2017 The Broad Institute
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
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package htsjdk.variant.vcf;


import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.annotations.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class VCFFileReaderUnitTest {

    @Test
    public void testModifyHeader() throws IOException, NoSuchFieldException, IllegalAccessException {
        final String fileName = "src/test/resources/htsjdk/variant/breakpoint.vcf"; //"src/test/resources/htsjdk/variant/HiSeq.10000.vcf";
        final FileInputStream stream = new FileInputStream(fileName);
        final Field fdField = FileDescriptor.class.getDeclaredField("fd");
        fdField.setAccessible(true);
        final File inputStream = new File("/dev/fd/" + fdField.getInt(stream.getFD()));
        //final File input = new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader fileReader = new VCFFileReader(inputStream, false);
        final VCFHeader header = fileReader.getFileHeader();

        final CloseableIterator<VariantContext> iterator = fileReader.iterator();
        final List<VariantContext> list = new ArrayList<VariantContext>();
        while (iterator.hasNext()) {
            final VariantContext context = iterator.next();
            list.add(context);
        }

        CloserUtil.close(iterator);
        CloserUtil.close(fileReader);
    }
}
