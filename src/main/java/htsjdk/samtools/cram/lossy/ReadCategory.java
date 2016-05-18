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
package htsjdk.samtools.cram.lossy;

public class ReadCategory {
    public final ReadCategoryType type;
    public final int param;

    private ReadCategory(final ReadCategoryType type, final int param) {
        this.type = type;
        this.param = param;
    }

    public static ReadCategory unplaced() {
        return new ReadCategory(ReadCategoryType.UNPLACED, -1);
    }

    public static ReadCategory higher_than_mapping_score(final int score) {
        return new ReadCategory(ReadCategoryType.HIGHER_MAPPING_SCORE,
                score);
    }

    public static ReadCategory lower_than_mapping_score(final int score) {
        return new ReadCategory(ReadCategoryType.LOWER_MAPPING_SCORE, score);
    }

    public static ReadCategory all() {
        return new ReadCategory(ReadCategoryType.ALL, -1);
    }

    @Override
    public String toString() {
        return String.format("[%s%d]", type.name(), param);
    }
}
