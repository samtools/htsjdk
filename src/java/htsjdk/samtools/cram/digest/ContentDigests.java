package htsjdk.samtools.cram.digest;

import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import net.sf.picard.util.Log;
import net.sf.samtools.SAMBinaryTagAndValue;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMTagUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class ContentDigests {
    public static final EnumSet<KNOWN_DIGESTS> ALL = EnumSet
            .allOf(KNOWN_DIGESTS.class);
    public static final EnumSet<KNOWN_DIGESTS> CRC32 = EnumSet.of(
            KNOWN_DIGESTS.BD, KNOWN_DIGESTS.SD);

    private static Log log = Log.getInstance(ContentDigests.class);
    private List<Digester> digesters = new LinkedList<ContentDigests.Digester>();

    public static ContentDigests create(EnumSet<KNOWN_DIGESTS> requestedDigests) {
        List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
        try {
            for (KNOWN_DIGESTS digest : requestedDigests)
                digesters.add(digest.createDigester());
            return new ContentDigests(digesters);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static ContentDigests create(SAMBinaryTagAndValue binrayTags) {
        try {
            List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
            SAMBinaryTagAndValue binaryTag = binrayTags;
            while (binaryTag != null) {
                String tagID = SAMTagUtil.getSingleton().makeStringTag(
                        binaryTag.tag);
                KNOWN_DIGESTS hash = null;
                try {
                    hash = KNOWN_DIGESTS.valueOf(tagID);
                    digesters.add(hash.createDigester());
                } catch (IllegalArgumentException e) {
                    // The tag is not one of the known content digest tags.
                }
                binaryTag = binaryTag.getNext();
            }
            return new ContentDigests(digesters);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private ContentDigests(List<Digester> hashers)
            throws NoSuchAlgorithmException {
        this.digesters = hashers;
    }

    public void add(SAMRecord record) {
        for (Digester digester : digesters)
            digester.add(record);
    }

    public void add(CramCompressionRecord record) {
        for (Digester digester : digesters)
            digester.addCramRecord(record);
    }

    public void addSAMRecords(Iterable<SAMRecord> records) {
        for (SAMRecord record : records)
            add(record);
    }

    public void addCramRecords(Iterable<CramCompressionRecord> records) {
        for (CramCompressionRecord record : records)
            add(record);
    }

    public SAMBinaryTagAndValue getAsTags() {
        SAMBinaryTagAndValue tag = null;
        for (Digester digester : digesters) {
            if (tag == null)
                tag = digester.toTag();
            else
                tag = tag.insert(digester.toTag());
        }

        return tag;
    }

    public boolean test(SAMBinaryTagAndValue tags) {
        for (Digester digester : digesters) {
            SAMBinaryTagAndValue foundTag = tags.find(digester.tagCode);
            if (foundTag == null)
                continue;

            if (!(foundTag.value instanceof byte[]))
                throw new RuntimeException("Expecting a byte array but got: "
                        + foundTag.value.getClass().getName());

            byte[] expected = (byte[]) foundTag.value;
            byte[] actual = digester.digest.asByteArray();
            if (!Arrays.equals(expected, actual)) {
                String expectedString = ByteBufferUtils.toHexString(expected);
                String actualString = ByteBufferUtils.toHexString(actual);
                log.error(String
                        .format("Content hash mismatch for tag %s, actual: %s; expected: %s",
                                digester.tagID, actualString, expectedString));
                return false;
            } else
                log.debug("Content digest ok: " + digester.tagID);
        }
        return true;
    }

    private static class Digester {
        AbstractSerialDigest<?> digest;
        SERIES series;
        String tagID;
        short tagCode;

        Digester(AbstractSerialDigest<?> digest, SERIES series, String tagID) {
            this.digest = digest;
            this.series = series;
            this.tagID = tagID;
            this.tagCode = SAMTagUtil.getSingleton().makeBinaryTag(tagID);
        }

        void add(SAMRecord record) {
            digest.add(series.getBytes(record));
        }

        void addCramRecord(CramCompressionRecord record) {
            digest.add(series.getBytes(record));
        }

        SAMBinaryTagAndValue toTag() {
            return new SAMBinaryTagAndValue(tagCode, digest.asByteArray());
        }
    }

    public enum KNOWN_DIGESTS {
        BD {
            @Override
            Digester createDigester() {
                return new Digester(new Crc32Hasher(new IntegerSumCombine()),
                        SERIES.BASES, name());
            }
        },
        SD {
            @Override
            Digester createDigester() {
                return new Digester(new Crc32Hasher(new IntegerSumCombine()),
                        SERIES.SCORES, name());
            }
        },
        B5 {
            @Override
            Digester createDigester() {
                try {
                    return new Digester(new MessageDigestHasher(
                            MessageDigest.getInstance("SHA-512"),
                            new ByteSumCombine(), null), SERIES.BASES, name());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        },
        S5 {
            @Override
            Digester createDigester() {
                try {
                    return new Digester(new MessageDigestHasher(
                            MessageDigest.getInstance("SHA-512"),
                            new ByteSumCombine(), null), SERIES.SCORES, name());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        },
        B1 {
            @Override
            Digester createDigester() {
                try {
                    return new Digester(new MessageDigestHasher(
                            MessageDigest.getInstance("SHA-1"),
                            new ByteSumCombine(), null), SERIES.BASES, name());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        },
        S1 {
            @Override
            Digester createDigester() {
                try {
                    return new Digester(new MessageDigestHasher(
                            MessageDigest.getInstance("SHA-1"),
                            new ByteSumCombine(), null), SERIES.SCORES, name());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        abstract Digester createDigester();

    }
}
