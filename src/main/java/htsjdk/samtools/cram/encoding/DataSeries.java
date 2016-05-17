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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.structure.EncodingKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to denote a data series field in a java class.
 * Some data can be represented as a set of column (data series) where
 * each column is characterized by it's intention ({@link htsjdk.samtools.cram.structure.EncodingKey} for CRAM)
 * and it's data type, like {@link java.lang.Integer}or {@link java.lang.String}.
 * Annotating fields in a class with this annotation allows for automated discovery of such column (data series)
 * and attaching specific codec to serialise/deserialize data.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSeries {
    /**
     * One of the pre-defined CRAM data series names
     * @return CRAM data series name (key)
     */
    EncodingKey key();

    /**
     * Data type of the series.
     * @return data type of the series
     */
    DataSeriesType type();
}
