/*
 * Copyright (c) 2007-2011 by The Broad Institute of MIT and Harvard.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

package htsjdk.samtools.util.ftp;


import htsjdk.samtools.SAMException;
import htsjdk.samtools.seekablestream.UserPasswordInput;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;


/**
 * @author jrobinso
 * @date Aug 31, 2010
 */
public class FTPUtils {

    static Map<String, String> userCredentials = new HashMap<String, String>();

    static int TIMEOUT = 10000;

    public static boolean resourceAvailable(URL url) {
        InputStream is = null;
        try {
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            is = conn.getInputStream();
            return (is.read() >= 0);

        } catch (IOException e) {
            return false;
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new SAMException("Error closing connection", e);
                }
            }
        }
    }

    public static long getContentLength(URL url) throws IOException {
        FTPClient ftp = null;
        try {
            ftp = FTPUtils.connect(url.getHost(), url.getUserInfo(), null);
            String sizeString = ftp.executeCommand("size " + url.getPath()).getReplyString();
            return Integer.parseInt(sizeString);
        } catch (Exception e) {
            return -1 ;
        }
        finally {
            if(ftp != null) {
                ftp.disconnect();
            }
        }
    }


    /**
     * Connect to an FTP server
     *
     * @param host
     * @param userInfo
     * @param userPasswordInput Dialog with which a user can enter credentials, if login fails
     * @return
     * @throws IOException
     */
    public static synchronized FTPClient connect(String host, String userInfo, UserPasswordInput userPasswordInput) throws IOException {

        FTPClient ftp = new FTPClient();
        FTPReply reply = ftp.connect(host);
        if (!reply.isSuccess()) {
            throw new RuntimeIOException("Could not connect to " + host);
        }

        String user = "anonymous";
        String password = "igv@broadinstitute.org";

        if (userInfo == null) {
            userInfo = userCredentials.get(host);
        }
        if (userInfo != null) {
            String[] tmp = userInfo.split(":");
            user = tmp[0];
            if (tmp.length > 1) {
                password = tmp[1];
            }
        }

        reply = ftp.login(user, password);
        if (!reply.isSuccess()) {
        	if (userPasswordInput == null) {
                throw new RuntimeIOException("Login failure for host: " + host);
        	}
        	else {
	        	userPasswordInput.setHost(host);
	            boolean success = false;
	            while (!success) {
	                if (userPasswordInput.showDialog()) {
	                    user = userPasswordInput.getUser();
	                    password = userPasswordInput.getPassword();
	                    reply = ftp.login(user, password);
	                    success = reply.isSuccess();
	                } else {
	                    // canceled
	                    break;
	                }
	
	            }
	            if (success) {
	                userInfo = user + ":" + password;
	                userCredentials.put(host, userInfo);
	            } else {
	                throw new RuntimeIOException("Login failure for host: " + host);
	            }
        	}
        }

        reply = ftp.binary();
        if (!(reply.isSuccess())) {
            throw new RuntimeIOException("Could not set binary mode on host: " + host);
        }

        return ftp;

    }

}

