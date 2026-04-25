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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a specific CRAM record data series and its associated type and unique Content ID. The
 * content id for a data series is not prescribed by the CrAM spec, so the ids used here represent the
 * ID used by this implementation on write. On read, the ID for each data series must be discovered
 * by interrogating the encoding map.
 */
public enum DataSeries {

    // Content IDs match htslib's cram_DS_ID enum (cram_structs.h) for easier cross-implementation
    // debugging. These IDs are written into each container's compression header encoding map and
    // are only used for newly written files — existing files encode their own ID mapping.

    // Main

    BF_BitFlags                         (DataSeriesType.INT,        "BF", 15),
    CF_CompressionBitFlags              (DataSeriesType.INT,        "CF", 16),

    // Positional

    RI_RefId                            (DataSeriesType.INT,        "RI", 33),
    RL_ReadLength                       (DataSeriesType.INT,        "RL", 25),
    AP_AlignmentPositionOffset          (DataSeriesType.INT,        "AP", 17),
    RG_ReadGroup                        (DataSeriesType.INT,        "RG", 18),

    // Read Name

    RN_ReadName                         (DataSeriesType.BYTE_ARRAY, "RN", 11),

    // Mate Record

    NF_RecordsToNextFragment            (DataSeriesType.INT,        "NF", 24),
    MF_MateBitFlags                     (DataSeriesType.INT,        "MF", 21),
    NS_NextFragmentReferenceSequenceID  (DataSeriesType.INT,        "NS", 20),
    NP_NextFragmentAlignmentStart       (DataSeriesType.INT,        "NP", 23),
    TS_InsertSize                       (DataSeriesType.INT,        "TS", 22),

    // Auxiliary Tags

    TL_TagIdList                        (DataSeriesType.INT,        "TL", 32),

    // Retained for backward compatibility on CRAM read. See https://github.com/samtools/hts-specs/issues/598
    // https://github.com/samtools/htsjdk/issues/1571

    TC_TagCount                         (DataSeriesType.INT,        "TC", 44),
    TN_TagNameAndType                   (DataSeriesType.INT,        "TN", 39),

    // Mapped Reads

    MQ_MappingQualityScore              (DataSeriesType.INT,        "MQ", 19),

    // Read Feature Records

    FN_NumberOfReadFeatures             (DataSeriesType.INT,        "FN", 26),
    FP_FeaturePosition                  (DataSeriesType.INT,        "FP", 28),
    FC_FeatureCode                      (DataSeriesType.BYTE,       "FC", 27),

    // Read Feature Codes

    BB_Bases                            (DataSeriesType.BYTE_ARRAY, "BB", 37),
    QQ_scores                           (DataSeriesType.BYTE_ARRAY, "QQ", 38),
    BA_Base                             (DataSeriesType.BYTE,       "BA", 30),
    // NOTE: the CramRecordReader and CramRecordWriter split the QS_QualityScore into two separate
    // DataSeriesReader/Writer(s), one uses the params described here (BYTE) and one uses BYTE_ARRAY
    QS_QualityScore                     (DataSeriesType.BYTE,       "QS", 12),
    BS_BaseSubstitutionCode             (DataSeriesType.BYTE,       "BS", 31),
    IN_Insertion                        (DataSeriesType.BYTE_ARRAY, "IN", 13),
    DL_DeletionLength                   (DataSeriesType.INT,        "DL", 29),
    RS_RefSkip                          (DataSeriesType.INT,        "RS", 34),
    SC_SoftClip                         (DataSeriesType.BYTE_ARRAY, "SC", 14),
    PD_padding                          (DataSeriesType.INT,        "PD", 35),
    HC_HardClip                         (DataSeriesType.INT,        "HC", 36),

    // For Testing Only — IDs match htslib's DS_TM=45, DS_TV=46

    // NOTE: these are not listed in the spec
    TM_TestMark                         (DataSeriesType.INT,        "TM", 45),
    TV_TestMark                         (DataSeriesType.INT,        "TV", 46);

    private final DataSeriesType type;
    private final String canonicalName;
    private final Integer externalBlockContentId;

    DataSeries(final DataSeriesType type, final String name, final Integer contentId) {
        this.type = type;
        this.canonicalName = name;
        this.externalBlockContentId = contentId;
    }

    public DataSeriesType getType() {
        return type;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    /**
     * The content id for a data series is not prescribed by the CrAM spec, so the ids used here represent the
     * ID used by this implementation on write. On read, the ID for each data series must be discovered
     * by interrogating the encoding map.
     * @return content ID used when writing a CRAM
     */
    public Integer getExternalBlockContentId() {
        return externalBlockContentId;
    }

    /**
     * Return the DataSeries associated with the two-character canonical name
     *
     * @param dataSeriesAbbreviation A Data Series canonical name, such as QS for Quality Score
     * @return the associated DataSeries
     * @throws CRAMException for an unknown Data Series
     */
    public static DataSeries byCanonicalName(final String dataSeriesAbbreviation) {
        if (dataSeriesAbbreviation.length() != 2) {
            throw new CRAMException("Data Series Canonical Name should be exactly two characters: " + dataSeriesAbbreviation);
        }

        return Optional.ofNullable(CANONICAL_NAME_MAP.get(dataSeriesAbbreviation))
                .orElseThrow(() -> new CRAMException("Could not find Data Series Encoding for: " + dataSeriesAbbreviation));
    }

    private static final Map<String, DataSeries> CANONICAL_NAME_MAP =
            Collections.unmodifiableMap(Stream.of(DataSeries.values())
                    .collect(Collectors.toMap(DataSeries::getCanonicalName, Function.identity())));
}
