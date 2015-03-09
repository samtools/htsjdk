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

public class BaseCategory {
    public final BaseCategoryType type;
    public final int param;

    private BaseCategory(final BaseCategoryType type, final int param) {
        this.type = type;
        this.param = param;
    }

    public static BaseCategory match() {
        return new BaseCategory(BaseCategoryType.MATCH, -1);
    }

    public static BaseCategory mismatch() {
        return new BaseCategory(BaseCategoryType.MISMATCH, -1);
    }

    public static BaseCategory flankingDeletion() {
        return new BaseCategory(BaseCategoryType.FLANKING_DELETION, -1);
    }

    public static BaseCategory pileup(final int threshold) {
        return new BaseCategory(BaseCategoryType.PILEUP, threshold);
    }

    public static BaseCategory lowerThanCoverage(final int coverage) {
        return new BaseCategory(BaseCategoryType.LOWER_COVERAGE, coverage);
    }

    public static BaseCategory insertion() {
        return new BaseCategory(BaseCategoryType.INSERTION, -1);
    }

    @Override
    public String toString() {
        return String.format("[%s%d]", type.name(), param);
    }
}
