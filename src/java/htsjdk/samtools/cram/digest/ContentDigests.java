package htsjdk.samtools.cram.digest;

import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.util.Log;

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

    private static final Log log = Log.getInstance(ContentDigests.class);
    private List<Digester> digesters = new LinkedList<ContentDigests.Digester>();

    public static ContentDigests create(EnumSet<KNOWN_DIGESTS> requestedDigests) {
        List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
            for (KNOWN_DIGESTS digest : requestedDigests)
                digesters.add(digest.createDigester());
            return new ContentDigests(digesters);
    }

    public static ContentDigests create(SAMBinaryTagAndValue binaryTags) {
            List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
            SAMBinaryTagAndValue binaryTag = binaryTags;
            while (binaryTag != null) {
                String tagID = SAMTagUtil.getSingleton().makeStringTag(
                        binaryTag.tag);
                KNOWN_DIGESTS hash ;
                try {
                    hash = KNOWN_DIGESTS.valueOf(tagID);
                    digesters.add(hash.createDigester());
                } catch (IllegalArgumentException e) {
                    // The tag is not one of the known content digest tags.
                }
                binaryTag = binaryTag.getNext();
            }
            return new ContentDigests(digesters);
    }

    private ContentDigests(List<Digester> hashers) {
        this.digesters = hashers;
    }

    void add(SAMRecord record) {
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
                String expectedString = toHexString(expected);
                String actualString = toHexString(actual);
                log.error(String
                        .format("Content hash mismatch for tag %s, actual: %s; expected: %s",
                                digester.tagID, actualString, expectedString));
                return false;
            } else
                log.debug("Content digest ok: " + digester.tagID);
        }
        return true;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte t : bytes) {
            sb.append(String.format("%02x", (0xFF & t)).toUpperCase()).append(
                    ' ');
        }
        return sb.toString();
    }

    private static String toHexString(byte[] bytes) {
        return toHex(bytes).replace(" ", "");
    }

    private static class Digester {
        final AbstractSerialDigest<?> digest;
        final SERIES series;
        final String tagID;
        final short tagCode;

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
