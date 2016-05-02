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

    public static ContentDigests create(final EnumSet<KNOWN_DIGESTS> requestedDigests) {
        final List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
        for (final KNOWN_DIGESTS digest : requestedDigests)
            digesters.add(digest.createDigester());
        return new ContentDigests(digesters);
    }

    public static ContentDigests create(final SAMBinaryTagAndValue binaryTags) {
        final List<Digester> digesters = new LinkedList<ContentDigests.Digester>();
        SAMBinaryTagAndValue binaryTag = binaryTags;
        while (binaryTag != null) {
            final String tagID = SAMTagUtil.getSingleton().makeStringTag(
                    binaryTag.tag);
            final KNOWN_DIGESTS hash;
            try {
                hash = KNOWN_DIGESTS.valueOf(tagID);
                digesters.add(hash.createDigester());
            } catch (final IllegalArgumentException e) {
                // The tag is not one of the known content digest tags.
            }
            binaryTag = binaryTag.getNext();
        }
        return new ContentDigests(digesters);
    }

    private ContentDigests(final List<Digester> hashers) {
        this.digesters = hashers;
    }

    void add(final SAMRecord record) {
        for (final Digester digester : digesters)
            digester.add(record);
    }

    public void add(final CramCompressionRecord record) {
        for (final Digester digester : digesters)
            digester.addCramRecord(record);
    }

    public void addSAMRecords(final Iterable<SAMRecord> records) {
        for (final SAMRecord record : records)
            add(record);
    }

    public void addCramRecords(final Iterable<CramCompressionRecord> records) {
        for (final CramCompressionRecord record : records)
            add(record);
    }

    public SAMBinaryTagAndValue getAsTags() {
        SAMBinaryTagAndValue tag = null;
        for (final Digester digester : digesters) {
            if (tag == null)
                tag = digester.toTag();
            else
                tag = tag.insert(digester.toTag());
        }

        return tag;
    }

    public boolean test(final SAMBinaryTagAndValue tags) {
        for (final Digester digester : digesters) {
            final SAMBinaryTagAndValue foundTag = tags.find(digester.tagCode);
            if (foundTag == null)
                continue;

            if (!(foundTag.value instanceof byte[]))
                throw new RuntimeException("Expecting a byte array but got: "
                        + foundTag.value.getClass().getName());

            final byte[] expected = (byte[]) foundTag.value;
            final byte[] actual = digester.digest.asByteArray();
            if (!Arrays.equals(expected, actual)) {
                final String expectedString = toHexString(expected);
                final String actualString = toHexString(actual);
                log.error(String
                        .format("Content hash mismatch for tag %s, actual: %s; expected: %s",
                                digester.tagID, actualString, expectedString));
                return false;
            } else
                log.debug("Content digest ok: " + digester.tagID);
        }
        return true;
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (final byte t : bytes) {
            sb.append(String.format("%02x", (0xFF & t)).toUpperCase()).append(
                    ' ');
        }
        return sb.toString();
    }

    private static String toHexString(final byte[] bytes) {
        return toHex(bytes).replace(" ", "");
    }

    private static class Digester {
        final AbstractSerialDigest<?> digest;
        final SERIES series;
        final String tagID;
        final short tagCode;

        Digester(final AbstractSerialDigest<?> digest, final SERIES series, final String tagID) {
            this.digest = digest;
            this.series = series;
            this.tagID = tagID;
            this.tagCode = SAMTagUtil.getSingleton().makeBinaryTag(tagID);
        }

        void add(final SAMRecord record) {
            digest.add(series.getBytes(record));
        }

        void addCramRecord(final CramCompressionRecord record) {
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
                } catch (final NoSuchAlgorithmException e) {
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
                } catch (final NoSuchAlgorithmException e) {
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
                } catch (final NoSuchAlgorithmException e) {
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
                } catch (final NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        abstract Digester createDigester();

    }
}
