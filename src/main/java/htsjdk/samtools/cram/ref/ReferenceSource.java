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
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;
import htsjdk.utils.ValidationUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Used to represent a CRAM reference, the backing source for which can either be
 * a file or the EBI ENA reference service.
 *
 * NOTE: In a future release, this class will be renamed and the functionality it
 * contains will be refactored and distributed into one or more separate reference
 * source implementations, each corresponding to the type of resource backing the
 * reference.
 */
public class ReferenceSource implements CRAMReferenceSource {
    private static final Log log = Log.getInstance(ReferenceSource.class);
    private final ReferenceSequenceFile rsFile;
    private int downloadTriesBeforeFailing = 2;

    private final Map<String, WeakReference<byte[]>> cacheW = new HashMap<>();

    // Optimize for locality of reference by maintaining the backing reference bases for the most recent request.
    // Failure to do this will result in the bases being aggressively GC'd when the only other reference to them
    // is the weak reference hash map above, resulting in much thrashing.
    private byte[] backingReferenceBases;
    private int backingContigIndex;

    public ReferenceSource(final File file) {
        this(IOUtil.toPath(file));
    }

    public ReferenceSource(final Path path) {
        this( path == null ? null : ReferenceSequenceFileFactory.getReferenceSequenceFile(path));
    }

    public ReferenceSource(final ReferenceSequenceFile rsFile) {
        this.rsFile = rsFile;
    }

    /**
     * Attempts to construct a default CRAMReferenceSource for use with CRAM files when
     * one has not been explicitly provided.
     *
     * @return CRAMReferenceSource if one can be acquired. Guaranteed to not be null if none
     * of the listed exceptions is thrown.
     * @throws IllegalArgumentException if the reference_fasta environment variable refers to a
     * a file that doesn't exist
     *<p>
     * Construct a default reference source to use when an explicit reference has not been
     * provided by checking for fallback sources in this order:
     *<p><ul>
     * <li>Defaults.REFERENCE_FASTA - the value of the system property "reference_fasta". If set,
     * must refer to a valid reference file.</li>
     * <li>ENA Reference Service if it is enabled</li>
     * </ul>
     */
     public static CRAMReferenceSource getDefaultCRAMReferenceSource() {
        if (null != Defaults.REFERENCE_FASTA) {
            if (Defaults.REFERENCE_FASTA.exists()) {
                log.info(String.format("Default reference file %s exists, so going to use that.", Defaults.REFERENCE_FASTA.getAbsolutePath()));
                return new ReferenceSource(Defaults.REFERENCE_FASTA);
            }
            else {
                throw new IllegalArgumentException(
                        "The file specified by the reference_fasta property does not exist: " + Defaults.REFERENCE_FASTA.getName());
            }
        }
        else if (Defaults.USE_CRAM_REF_DOWNLOAD) {
            log.info("USE_CRAM_REF_DOWNLOAD=true, so attempting to download reference file as needed.");
            return new ReferenceSource((ReferenceSequenceFile)null);
        }
        else {
            return new CRAMLazyReferenceSource();
        }
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

    // Upper case (in-place), and add to the cache
    private byte[] addToCache(final String sequenceName, final byte[] bases) {
        // Normalize to upper case only. We can't use the cram normalization utility Utils.normalizeBases, since
        // we don't want to normalize ambiguity codes, we can't use SamUtils.normalizeBases, since we don't want
        // to normalize no-call ('.') bases.
        for (int i = 0; i < bases.length; i++) {
            bases[i] = StringUtil.toUpperCase(bases[i]);
        }
        cacheW.put(sequenceName, new WeakReference<>(bases));
        return bases;
    }

    @Override
    public synchronized byte[] getReferenceBases(final SAMSequenceRecord record,
                                                 final boolean tryNameVariants) {
        { // check cache by sequence name:
            final String name = record.getSequenceName();
            final byte[] bases = findInCache(name);
            if (bases != null) {
                return bases;
            }
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
                return addToCache(record.getSequenceName(), bases);
            }
        }

        {
            if (Defaults.USE_CRAM_REF_DOWNLOAD) { // try to fetch sequence by md5:
                if (md5 != null) {
                    bases = findBasesByMD5(md5.toLowerCase());
                }
                if (bases != null) {
                    return addToCache(md5, bases);
                }
            }
        }

        // sequence not found, give up:
        return null;
    }

    @Override
    public byte[] getReferenceBasesByRegion(
            final SAMSequenceRecord sequenceRecord,
            final int zeroBasedStart,
            final int requestedRegionLength) {
        ValidationUtils.validateArg(zeroBasedStart >= 0, "start must be >= 0");

        // this implementation maintains the entire reference sequence, and hands out whatever region
        // of it is requested
        final byte[] bases = getBackingBases(sequenceRecord);

        if (bases != null) {
            // cache the backing bases to prevent thrashing due to aggressive GC
            backingReferenceBases = bases;
            backingContigIndex = sequenceRecord.getSequenceIndex();

            if (zeroBasedStart >= bases.length) {
                throw new IllegalArgumentException(String.format("Requested start %d is beyond the sequence length %s",
                        zeroBasedStart,
                        sequenceRecord.getSequenceName()));
            }
            return Arrays.copyOfRange(bases, zeroBasedStart, Math.min(bases.length, zeroBasedStart + requestedRegionLength));
        }
        return bases;
    }

    private byte[] getBackingBases(final SAMSequenceRecord sequenceRecord) {
        if (backingReferenceBases != null && backingContigIndex == sequenceRecord.getSequenceIndex()) {
            return backingReferenceBases;
        } else {
            return getReferenceBases(sequenceRecord, false);
        }
    }

    private byte[] findBasesByName(final String name, final boolean tryVariants) {
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

    private byte[] findBasesByMD5(final String md5) {
        final String url = String.format(Locale.US, Defaults.EBI_REFERENCE_SERVICE_URL_MASK, md5);

        for (int i = 0; i < downloadTriesBeforeFailing; i++) {
            try (final InputStream is = new URL(url).openStream()) {
                if (is == null)
                    return null;

                log.info("Downloading reference sequence: " + url);
                final byte[] data = InputStreamUtils.readFully(is);
                log.info("Downloaded " + data.length + " bytes for md5 " + md5);

                final String downloadedMD5 = SequenceUtil.calculateMD5String(data);
                if (md5.equals(downloadedMD5)) {
                    return data;
                } else {
                    final String message = String
                            .format("Downloaded sequence is corrupt: requested md5=%s, received md5=%s",
                                    md5, downloadedMD5);
                    log.error(message);
                }
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new GaveUpException("Giving up on downloading sequence for md5 "
                + md5);
    }

    private static final Pattern chrPattern = Pattern.compile("chr.*",
            Pattern.CASE_INSENSITIVE);

    private List<String> getVariants(final String name) {
        final List<String> variants = new ArrayList<>();

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
