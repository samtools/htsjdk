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

import gov.nih.nlm.ncbi.ngs.error.LibraryLoadError;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.Log;
import gov.nih.nlm.ncbi.ngs.NGS;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Describes a single SRA accession for SRA read collection
 * Also provides app string functionality and allows to check if working SRA is supported on the running platform
 *
 * Important: due to checks performed in SRAAccession.isValid(), we won't recognise any accessions other
 * than ones that follow the pattern "^[SED]RR[0-9]{6,9}$", e.g. SRR000123
 */
public class SRAAccession implements Serializable {
    private static final Log log = Log.getInstance(SRAAccession.class);

    private static boolean noLibraryDownload;
    private static boolean initTried = false;
    private static String appVersionString = null;
    private final static String defaultAppVersionString = "[unknown software]";
    private final static String htsJdkVersionString = "HTSJDK-NGS";

    private String acc;

    static {
        noLibraryDownload = !Defaults.SRA_LIBRARIES_DOWNLOAD;
        if (noLibraryDownload) {
            System.setProperty("vdb.System.noLibraryDownload", "1");
        }
    }

    /**
     * Sets an app version string which will let SRA know which software uses it.
     * @param appVersionString a string that describes running application
     */
    public static void setAppVersionString(String appVersionString) {
        SRAAccession.appVersionString = appVersionString;
    }

    /**
     * @deprecated
     * @return true if SRA successfully loaded native libraries and fully initialized,
     * false otherwise
     */
    public static boolean isSupported() {
        return checkIfInitialized() == null;
    }

    /**
     * Tries to initialize SRA. Initialization error is saved during first call,
     * all subsequent calls will return the same saved error or null.
     *
     * @return ExceptionInInitializerError if initialization failed, null if initialization was successful
     */
    public static ExceptionInInitializerError checkIfInitialized() {
        final ExceptionInInitializerError ngsInitError;
        if (!initTried) {
            log.debug("Initializing SRA module");
            ngsInitError = NGS.getInitializationError();
            if (ngsInitError != null) {
                log.info("SRA initialization failed. Will not be able to read from SRA");
            } else {
                NGS.setAppVersionString(getFullVersionString());
            }
            initTried = true;
        } else {
            ngsInitError = NGS.getInitializationError();
        }
        return ngsInitError;
    }

    /**
     * @param acc accession
     * @return true if a string is a valid SRA accession
     */
    public static boolean isValid(String acc) {
        boolean looksLikeSRA = false;
        File f = new File(acc);
        if (f.isFile()) {
            byte[] buffer = new byte[8];
            byte[] signature1 = "NCBI.sra".getBytes();
            byte[] signature2 = "NCBInenc".getBytes();

            try (InputStream is = new FileInputStream(f)) {
                int numRead = is.read(buffer);

                looksLikeSRA = numRead == buffer.length &&
                        (Arrays.equals(buffer, signature1) || Arrays.equals(buffer, signature2));
            } catch (IOException e) {
                looksLikeSRA = false;
            }
        } else if (f.exists()) {
            // anything else local other than a file is not an SRA archive
            looksLikeSRA = false;
        } else {
            looksLikeSRA = acc.toUpperCase().matches ( "^[SED]RR[0-9]{6,9}$" );
        }

        if (!looksLikeSRA) return false;

        final ExceptionInInitializerError initError = checkIfInitialized();
        if (initError != null) {
            if (noLibraryDownload && initError instanceof LibraryLoadError) {
                throw new LinkageError(
                        "Failed to load SRA native libraries and auto-download is disabled. " +
                        "Please re-run with JVM argument -Dsamjdk.sra_libraries_download=true to enable auto-download of native libraries",
                        initError
                );
            } else {
                throw initError;
            }
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
