/*===========================================================================
*
*                            PUBLIC DOMAIN NOTICE
*               National Center for Biotechnology Information
*
*  This software/database is a "United States Government Work" under the
*  terms of the United States Copyright Act.  It was written as part of
*  the author's official duties as a United States Government employee and
*  thus cannot be copyrighted.  This software/database is freely available
*  to the public for use. The National Library of Medicine and the U.S.
*  Government have not placed any restriction on its use or reproduction.
*
*  Although all reasonable efforts have been taken to ensure the accuracy
*  and reliability of the software and data, the NLM and the U.S.
*  Government do not and cannot warrant the performance or results that
*  may be obtained by using this software or data. The NLM and the U.S.
*  Government disclaim all warranties, express or implied, including
*  warranties of performance, merchantability or fitness for any particular
*  purpose.
*
*  Please cite the author in any work or product based on this material.
*
* ===========================================================================
*
*/

package htsjdk.samtools.sra;

import htsjdk.samtools.util.Log;
import gov.nih.nlm.ncbi.ngs.NGS;

import java.io.Serializable;

/**
 * Describes a single SRA accession
 * Also provides app string functionality and allows to check if working SRA is supported on the running platform
 */
public class SRAAccession implements Serializable {
    private static final Log log = Log.getInstance(SRAAccession.class);

    private static Boolean isSupportedCached = null;
    private static String appVersionString = null;
    private final static String defaultAppVersionString = "[unknown software]";
    private final static String htsJdkVersionString = "HTSJDK-NGS";

    private String acc;

    /**
     * Sets an app version string which will let SRA know which software uses it.
     * @param appVersionString a string that describes running application
     */
    public static void setAppVersionString(String appVersionString) {
        SRAAccession.appVersionString = appVersionString;
    }

    /**
     * Returns true if SRA is supported on the running platform
     * @return true if SRA engine was successfully loaded and operational, false otherwise
     */
    public static boolean isSupported() {
        if (isSupportedCached == null) {
            log.debug("Checking if SRA module is supported in that environment");
            isSupportedCached = NGS.isSupported();
            if (!isSupportedCached) {
                log.info("SRA is not supported. Will not be able to read from SRA");
            } else {
                NGS.setAppVersionString(getFullVersionString());
            }
        }
        return isSupportedCached;
    }

    /**
     * @param acc accession
     * @return true if a string is a valid SRA accession
     */
    public static boolean isValid(String acc) {
        if (!isSupported()) {
            return false;
        }

        return NGS.isValid(acc);
    }

    /**
     * @param acc accession
     */
    public SRAAccession(String acc) {
        this.acc = acc;
    }

    public String toString() {
        return acc;
    }

    /**
     * @return true if contained string is an SRA accession
     */
    public boolean isValid() {
        return SRAAccession.isValid(acc);
    }

    private static String getFullVersionString() {
        String versionString = appVersionString == null ? defaultAppVersionString : appVersionString;
        versionString += " through " + htsJdkVersionString;
        return versionString;
    }
}
