/*
 * The MIT License
 *
 * Date: 2015
 * Author: Pierre Lindenbaum @yokofakun
 *     Institut du Thorax , Nantes, France
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

package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

public class SAMSequenceDictionaryTest {
    @Test
    public void testAliases() {
        final SAMSequenceRecord ssr1 = new SAMSequenceRecord("1", 1);
        final SAMSequenceRecord ssr2 = new SAMSequenceRecord("2", 1);

        final SAMSequenceDictionary dict = new SAMSequenceDictionary(
                Arrays.asList(ssr1, ssr2));
        Assert.assertEquals(dict.size(), 2);
        dict.addSequenceAlias("1", "chr1");
        dict.addSequenceAlias("1", "01");
        dict.addSequenceAlias("1", "1");
        dict.addSequenceAlias("01", "chr01");
        Assert.assertEquals(dict.size(), 2);
        Assert.assertNotNull(dict.getSequence("chr1"));
        Assert.assertNull(dict.getSequence("chr2"));
    }

    /**
     * should be saved as XML
     * 
     * <pre>
     * <?xml version="1.0" encoding="UTF-8" standalone="yes"?><References><Reference assembly="as" md5="68b329da9893e34099c7d8ad5cb9c940" index="0" length="1" species="sp">1</Reference><Reference index="1" length="1">2</Reference></References>
     * </pre>
     * 
     * @throws JAXBException
     */
    @Test
    public void testXmlSeralization() throws JAXBException {
        // create dict
        final SAMSequenceRecord ssr1 = new SAMSequenceRecord("1", 1);
        ssr1.setMd5("68b329da9893e34099c7d8ad5cb9c940");
        ssr1.setAssembly("as");
        ssr1.setSpecies("sp");
        final SAMSequenceRecord ssr2 = new SAMSequenceRecord("2", 1);
        final StringWriter xmlWriter = new StringWriter();
        final SAMSequenceDictionary dict1 = new SAMSequenceDictionary(
                Arrays.asList(ssr1, ssr2));
        // create jaxb context
        JAXBContext jaxbContext = JAXBContext
                .newInstance(SAMSequenceDictionary.class);
        // save to XML
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.marshal(dict1, xmlWriter);
        // reload XML
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        final SAMSequenceDictionary dict2 = (SAMSequenceDictionary) jaxbUnmarshaller
                .unmarshal(new StringReader(xmlWriter.toString()));
        Assert.assertEquals(dict1, dict2);
    }

}
