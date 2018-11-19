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
 * Represents a specific CRAM record data series and its associated type and unique Content ID
 */
public enum DataSeries {

    // in rough encoding/decoding order, by group

    // Main

    BF_BitFlags                         (DataSeriesType.INT,        "BF",  1),
    CF_CompressionBitFlags              (DataSeriesType.BYTE,       "CF",  2),

    // Positional

    RI_RefId                            (DataSeriesType.INT,        "RI",  3),
    RL_ReadLength                       (DataSeriesType.INT,        "RL",  4),
    AP_AlignmentPositionOffset          (DataSeriesType.INT,        "AP",  5),
    RG_ReadGroup                        (DataSeriesType.INT,        "RG",  6),

    // Read Name

    RN_ReadName                         (DataSeriesType.BYTE_ARRAY, "RN",  7),

    // Mate Record

    NF_RecordsToNextFragment            (DataSeriesType.INT,        "NF",  8),
    MF_MateBitFlags                     (DataSeriesType.BYTE,       "MF",  9),
    NS_NextFragmentReferenceSequenceID  (DataSeriesType.INT,        "NS", 10),
    NP_NextFragmentAlignmentStart       (DataSeriesType.INT,        "NP", 11),
    TS_InsertSize                       (DataSeriesType.INT,        "TS", 12),

    // Auxiliary Tags

    TL_TagIdList                        (DataSeriesType.INT,        "TL", 13),
    TC_TagCount                         (DataSeriesType.INT,        "TC", 14),
    TN_TagNameAndType                   (DataSeriesType.INT,        "TN", 15),

    // Mapped Reads

    MQ_MappingQualityScore              (DataSeriesType.INT,        "MQ", 16),

    // Read Feature Records

    FN_NumberOfReadFeatures             (DataSeriesType.INT,        "FN", 17),
    FP_FeaturePosition                  (DataSeriesType.INT,        "FP", 18),
    FC_FeatureCode                      (DataSeriesType.BYTE,       "FC", 19),

    // Read Feature Codes

    BB_bases                            (DataSeriesType.BYTE_ARRAY, "BB", 20),
    QQ_scores                           (DataSeriesType.BYTE_ARRAY, "QQ", 21),
    BA_Base                             (DataSeriesType.BYTE,       "BA", 22),
    QS_QualityScore                     (DataSeriesType.BYTE,       "QS", 23),
    BS_BaseSubstitutionCode             (DataSeriesType.BYTE,       "BS", 24),
    IN_Insertion                        (DataSeriesType.BYTE_ARRAY, "IN", 25),
    DL_DeletionLength                   (DataSeriesType.INT,        "DL", 26),
    RS_RefSkip                          (DataSeriesType.INT,        "RS", 27),
    SC_SoftClip                         (DataSeriesType.BYTE_ARRAY, "SC", 28),
    PD_padding                          (DataSeriesType.INT,        "PD", 29),
    HC_HardClip                         (DataSeriesType.INT,        "HC", 30),

    // For Testing Only

    TM_TestMark                         (DataSeriesType.INT,        "TM", 31),
    TV_TestMark                         (DataSeriesType.INT,        "TV", 32);

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
