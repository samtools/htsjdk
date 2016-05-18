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

import java.util.ArrayList;
import java.util.List;

public class PreservationPolicy {
    public ReadCategory readCategory;
    public final List<BaseCategory> baseCategories = new ArrayList<BaseCategory>();

    public QualityScoreTreatment treatment;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (readCategory != null)
            sb.append(readCategory.toString());

        if (baseCategories != null)
            for (final BaseCategory c : baseCategories)
                sb.append(c.toString());

        sb.append(treatment.toString());
        return sb.toString();
    }
}
