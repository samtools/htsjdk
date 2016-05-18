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

public class QualityScoreTreatment {
    public final QualityScoreTreatmentType type;
    public final int param;

    private QualityScoreTreatment(final QualityScoreTreatmentType type, final int param) {
        this.type = type;
        this.param = param;
    }

    public static QualityScoreTreatment preserve() {
        return new QualityScoreTreatment(
                QualityScoreTreatmentType.PRESERVE, 40);
    }

    public static QualityScoreTreatment drop() {
        return new QualityScoreTreatment(QualityScoreTreatmentType.DROP, 40);
    }

    public static QualityScoreTreatment bin(final int bins) {
        return new QualityScoreTreatment(QualityScoreTreatmentType.BIN,
                bins);
    }

    @Override
    public String toString() {
        return String.format("[%s%d]", type.name(), param);
    }
}
