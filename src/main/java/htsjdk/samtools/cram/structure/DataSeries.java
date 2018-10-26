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
    AP_AlignmentPositionOffset          (DataSeriesType.INT,        "AP",  1),
    BA_Base                             (DataSeriesType.BYTE,       "BA",  2),
    BB_bases                            (DataSeriesType.BYTE_ARRAY, "BB",  3),
    BF_BitFlags                         (DataSeriesType.INT,        "BF",  4),
    BS_BaseSubstitutionCode             (DataSeriesType.BYTE,       "BS",  5),
    CF_CompressionBitFlags              (DataSeriesType.BYTE,       "CF",  6),
    DL_DeletionLength                   (DataSeriesType.INT,        "DL",  7),
    FC_FeatureCode                      (DataSeriesType.BYTE,       "FC",  8),
    FN_NumberOfReadFeatures             (DataSeriesType.INT,        "FN",  9),
    FP_FeaturePosition                  (DataSeriesType.INT,        "FP", 10),
    HC_HardClip                         (DataSeriesType.INT,        "HC", 11),
    IN_Insertion                        (DataSeriesType.BYTE_ARRAY, "IN", 12),
    MF_MateBitFlags                     (DataSeriesType.BYTE,       "MF", 13),
    MQ_MappingQualityScore              (DataSeriesType.INT,        "MQ", 14),
    NF_RecordsToNextFragment            (DataSeriesType.INT,        "NF", 15),
    NP_NextFragmentAlignmentStart       (DataSeriesType.INT,        "NP", 16),
    NS_NextFragmentReferenceSequenceID  (DataSeriesType.INT,        "NS", 17),
    PD_padding                          (DataSeriesType.INT,        "PD", 18),
    QQ_scores                           (DataSeriesType.BYTE_ARRAY, "QQ", 19),
    QS_QualityScore                     (DataSeriesType.BYTE,       "QS", 20),
    RG_ReadGroup                        (DataSeriesType.INT,        "RG", 21),
    RI_RefId                            (DataSeriesType.INT,        "RI", 22),
    RL_ReadLength                       (DataSeriesType.INT,        "RL", 23),
    RN_ReadName                         (DataSeriesType.BYTE_ARRAY, "RN", 24),
    RS_RefSkip                          (DataSeriesType.INT,        "RS", 25),
    SC_SoftClip                         (DataSeriesType.BYTE_ARRAY, "SC", 26),
    TC_TagCount                         (DataSeriesType.INT,        "TC", 27),
    TL_TagIdList                        (DataSeriesType.INT,        "TL", 28),
    TM_TestMark                         (DataSeriesType.INT,        "TM", 29),
    TN_TagNameAndType                   (DataSeriesType.INT,        "TN", 30),
    TS_InsetSize                        (DataSeriesType.INT,        "TS", 31),
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
