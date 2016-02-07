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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalUtil;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

public class SAMSequenceDictionaryTest {
    private static final int CONTIG_LENGTH=100;
    
    @DataProvider(name = "good-intervals")
    public Object[][] goodIntervals() {
        return new Object[][]{
                {"1"     , new Interval("1",1,CONTIG_LENGTH)},
                {"1:10" , new Interval("1", 10, 10)},
                {"1:10bp" , new Interval("1", 10, 10)},
                {"1:10bp-" , new Interval("1", 10, CONTIG_LENGTH)},
                {"1:10-10kb" , new Interval("1", 10, CONTIG_LENGTH)},
                {"1:"+(CONTIG_LENGTH-5)+"+20" , new Interval("1", CONTIG_LENGTH-25, CONTIG_LENGTH)},
                {"1:10+2" , new Interval("1", 8, 12)},
                {"1:0-1" , new Interval("1",1,1)},
                {"1:4 + 50" , new Interval("1", 1, 54)},
                {"1: 10-20" , new Interval("1",10,20)},
                {"1 : 10 - 20  " , new Interval("1",10,20)},
                {"1 : 1,0 - 2,0  " , new Interval("1",10,20)},
                {"1 :10-" , new Interval("1",10,CONTIG_LENGTH)},
                {"1:10-"+(CONTIG_LENGTH+100) , new Interval("1", 10, CONTIG_LENGTH)}
        };
    }

    @Test(dataProvider = "good-intervals")
    void testIntervalParser(final String intervalStr,final Interval interval) {
        final SAMSequenceRecord ssr1 = new SAMSequenceRecord("1", CONTIG_LENGTH);
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(
                Arrays.asList(ssr1));
        Assert.assertEquals(dict.size(), 1);
        Assert.assertEquals(
                IntervalUtil.parseInterval(dict,intervalStr),
                interval
               );
    }

    @DataProvider(name = "bad-intervals")
    public Object[][] badIntervals() {
        return new Object[][]{
                {null},
                {"X"},
                {"1:0-0"},
                {"1:"+(CONTIG_LENGTH+10)},
                {"1:"+(CONTIG_LENGTH+10) + "-"},
                {"1:"+(CONTIG_LENGTH+10)+"-"+(CONTIG_LENGTH+11)},
                {"1:10zz"},
                {"1:1--10"},
                {"1:20-10"},
                {"1:1gp"}
        };
    }

    @Test(dataProvider = "bad-intervals",expectedExceptions= IllegalArgumentException.class)
    void testBadIntervalParser(final String intervalStr)  {
        final SAMSequenceRecord ssr1 = new SAMSequenceRecord("1", CONTIG_LENGTH);
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(
                Arrays.asList(ssr1));
        Assert.assertEquals(dict.size(), 1);
        IntervalUtil.parseInterval(dict,intervalStr);   
    }
    
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
