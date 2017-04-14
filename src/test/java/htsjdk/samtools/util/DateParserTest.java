// DateParser.java
// $Id: DateParser.java,v 1.3 2001/01/04 13:26:19 bmahe Exp $
// (c) COPYRIGHT MIT, INRIA and Keio, 2000.

/*
W3C IPR SOFTWARE NOTICE

Copyright 1995-1998 World Wide Web Consortium, (Massachusetts Institute of
Technology, Institut National de Recherche en Informatique et en
Automatique, Keio University). All Rights Reserved.
http://www.w3.org/Consortium/Legal/

This W3C work (including software, documents, or other related items) is
being provided by the copyright holders under the following license. By
obtaining, using and/or copying this work, you (the licensee) agree that you
have read, understood, and will comply with the following terms and
conditions:

Permission to use, copy, and modify this software and its documentation,
with or without modification,  for any purpose and without fee or royalty is
hereby granted, provided that you include the following on ALL copies of the
software and documentation or portions thereof, including modifications,
that you make:

  1. The full text of this NOTICE in a location viewable to users of the
     redistributed or derivative work.
  2. Any pre-existing intellectual property disclaimers, notices, or terms
     and conditions. If none exist, a short notice of the following form
     (hypertext is preferred, text is permitted) should be used within the
     body of any redistributed or derivative code: "Copyright World Wide
     Web Consortium, (Massachusetts Institute of Technology, Institut
     National de Recherche en Informatique et en Automatique, Keio
     University). All Rights Reserved. http://www.w3.org/Consortium/Legal/"
  3. Notice of any changes or modifications to the W3C files, including the
     date changes were made. (We recommend you provide URIs to the location
     from which the code is derived).

In addition, creators of derivitive works must include the full text of this
NOTICE in a location viewable to users of the derivitive work.

THIS SOFTWARE AND DOCUMENTATION IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS
MAKE NO REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO, WARRANTIES OF MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR
PURPOSE OR THAT THE USE OF THE SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE
ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS.

COPYRIGHT HOLDERS WILL NOT BE LIABLE FOR ANY DIRECT, INDIRECT, SPECIAL OR
CONSEQUENTIAL DAMAGES ARISING OUT OF ANY USE OF THE SOFTWARE OR
DOCUMENTATION.

The name and trademarks of copyright holders may NOT be used in advertising
or publicity pertaining to the software without specific, written prior
permission. Title to copyright in this software and any associated
documentation will at all times remain with copyright holders.

____________________________________

This formulation of W3C's notice and license became active on August 14
1998. See the older formulation for the policy prior to this date. Please
see our Copyright FAQ for common questions about using materials from our
site, including specific terms and conditions for packages like libwww,
Amaya, and Jigsaw. Other questions about this notice can be directed to
site-policy@w3.org .




webmaster
(last updated 14-Aug-1998)

 */

package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Date;

/**
 * NOTE: This code has been taken from w3.org, and modified slightly to handle timezones of the form [-+]DDDD,
 * and also to fix a bug in the application of time zone to the parsed date.
 *
 * Date parser for ISO 8601 format
 * http://www.w3.org/TR/1998/NOTE-datetime-19980827
 * @version $Revision: 1.3 $
 * @author  bmahe@w3.org
 */

public class DateParserTest extends HtsjdkTest {

    private static void test(final String isodate) {
        Date date = DateParser.parse(isodate);
        final String isodateRoundTrip = DateParser.getIsoDate(date);

        final Date orig = DateParser.parse(isodate);
        final Date roundTrip = DateParser.parse(isodateRoundTrip);

        assertDatesAreClose(orig, roundTrip);
    }

    private static void test(final Date date) {
        String isodate;
        isodate = DateParser.getIsoDate(date);
        final Date dateRoundTrip = DateParser.parse(isodate);

        assertDatesAreClose(date, dateRoundTrip);
        Assert.assertTrue(Math.abs(date.getTime() - dateRoundTrip.getTime()) < 10);
    }

    @DataProvider(name="dateDate")
    public Object[][] dateData() {
        return new Object[][]{
                {"1997-07-16T19:20:30.45-02:00"},
                {"1997-07-16T19:20:30+01:00"},
                {"1997-07-16T19:20:30+01:00"},
                {"1997-07-16T19:20"},
                {"1997-07-16"},
                {"1997-07"},
                {"1997"},
        };
    }

    @Test(dataProvider = "dateDate")
    public static void testString(final String string) {
        test(string);
    }

    @Test(dataProvider = "dateDate")
    public static void testDates(final String string) {
        test(DateParser.parse(string));
    }

    @Test
    public static void testDate() {
        test(new Date());
    }

    public static void assertDatesAreClose(final Date lhs, final Date rhs) {
        Assert.assertEquals(lhs.getYear(), rhs.getYear());
        Assert.assertEquals(lhs.getMonth(), rhs.getMonth());
        Assert.assertEquals(lhs.getDate(), rhs.getDate());
        Assert.assertEquals(lhs.getDay(), rhs.getDay());
        Assert.assertEquals(lhs.getHours(), rhs.getHours());
        Assert.assertEquals(lhs.getMinutes(), rhs.getMinutes());
        Assert.assertEquals(lhs.getSeconds(), rhs.getSeconds());
        Assert.assertEquals(lhs.getTimezoneOffset(), rhs.getTimezoneOffset());
    }
}
