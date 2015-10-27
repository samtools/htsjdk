/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.ref;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ReferenceSource {
    private static final Log log = Log.getInstance(ReferenceSource.class);
    private ReferenceSequenceFile rsFile;
    private int downloadTriesBeforeFailing = 2;

    private final Map<String, WeakReference<byte[]>> cacheW = new HashMap<String, WeakReference<byte[]>>();

    public ReferenceSource() {
    }

    public ReferenceSource(final File file) {
        if (file != null)
            rsFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(file);
    }

    public ReferenceSource(final ReferenceSequenceFile rsFile) {
        this.rsFile = rsFile;
    }

    public void clearCache() {
        cacheW.clear();
    }

    private byte[] findInCache(final String name) {
        final WeakReference<byte[]> weakReference = cacheW.get(name);
        if (weakReference != null) {
            final byte[] bytes = weakReference.get();
            if (bytes != null)
                return bytes;
        }
        return null;
    }

    public synchronized byte[] getReferenceBases(final SAMSequenceRecord record,
                                                 final boolean tryNameVariants) {
        { // check cache by sequence name:
            final String name = record.getSequenceName();
            final byte[] bases = findInCache(name);
            if (bases != null)
                return bases;
        }

        final String md5 = record.getAttribute(SAMSequenceRecord.MD5_TAG);
        { // check cache by md5:
            if (md5 != null) {
                byte[] bases = findInCache(md5);
                if (bases != null)
                    return bases;
                bases = findInCache(md5.toLowerCase());
                if (bases != null)
                    return bases;
                bases = findInCache(md5.toUpperCase());
                if (bases != null)
                    return bases;
            }
        }

        byte[] bases;

        { // try to fetch sequence by name:
            bases = findBasesByName(record.getSequenceName(), tryNameVariants);
            if (bases != null) {
                SequenceUtil.upperCase(bases);
                cacheW.put(record.getSequenceName(), new WeakReference<byte[]>(
                        bases));
                return bases;
            }
        }

        {
            if (Defaults.USE_CRAM_REF_DOWNLOAD) { // try to fetch sequence by md5:
                if (md5 != null) {
                    try {
                        bases = findBasesByMD5(md5.toLowerCase());
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                if (bases != null) {
                    SequenceUtil.upperCase(bases);
                    cacheW.put(md5, new WeakReference<byte[]>(bases));
                    return bases;
                }
            }
        }

        // sequence not found, give up:
        return null;
    }

    byte[] findBasesByName(final String name, final boolean tryVariants) {
        if (rsFile == null || !rsFile.isIndexed())
            return null;

        ReferenceSequence sequence = null;
        try {
            sequence = rsFile.getSequence(name);
        } catch (final SAMException e) {
            // the only way to test if rsFile contains the sequence is to try and catch exception.
        }
        if (sequence != null)
            return sequence.getBases();

        if (tryVariants) {
            for (final String variant : getVariants(name)) {
                try {
                    sequence = rsFile.getSequence(variant);
                } catch (final SAMException e) {
                    log.warn("Sequence not found: " + variant);
                }
                if (sequence != null)
                    return sequence.getBases();
            }
        }
        return null;
    }

    byte[] findBasesByMD5(final String md5) throws
            IOException {
        final String url = String.format(Defaults.EBI_REFERENCE_SEVICE_URL_MASK, md5);

        for (int i = 0; i < downloadTriesBeforeFailing; i++) {
            final InputStream is = new URL(url).openStream();
            if (is == null)
                return null;

            log.debug("Downloading reference sequence: " + url);
            final byte[] data = InputStreamUtils.readFully(is);
            log.debug("Downloaded " + data.length + " bytes for md5 " + md5);
            is.close();

            try {
                final String downloadedMD5 = SequenceUtil.calculateMD5String(data);
                if (md5.equals(downloadedMD5)) {
                    return data;
                } else {
                    final String message = String
                            .format("Downloaded sequence is corrupt: requested md5=%s, received md5=%s",
                                    md5, downloadedMD5);
                    log.error(message);
                }
            } catch (final NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Giving up on downloading sequence for md5 "
                + md5);
    }

    private static final Pattern chrPattern = Pattern.compile("chr.*",
            Pattern.CASE_INSENSITIVE);

    List<String> getVariants(final String name) {
        final List<String> variants = new ArrayList<String>();

        if (name.equals("M"))
            variants.add("MT");

        if (name.equals("MT"))
            variants.add("M");

        final boolean chrPatternMatch = chrPattern.matcher(name).matches();
        if (chrPatternMatch)
            variants.add(name.substring(3));
        else
            variants.add("chr" + name);

        if ("chrM".equals(name)) {
            // chrM case:
            variants.add("MT");
        }
        return variants;
    }

    public int getDownloadTriesBeforeFailing() {
        return downloadTriesBeforeFailing;
    }

    public void setDownloadTriesBeforeFailing(final int downloadTriesBeforeFailing) {
        this.downloadTriesBeforeFailing = downloadTriesBeforeFailing;
    }
}
