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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.structure.CRAMRecord;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.Slice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainerParser {
    private final SAMFileHeader samFileHeader;

    public ContainerParser(final SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    //TODO: its unnecessary to both pass in AND return the list of records
    public List<CRAMRecord> getRecords(final Container container,
                                                  ArrayList<CRAMRecord> records,
                                                  final ValidationStringency validationStringency) {
        if (container.isEOF()) {
            return Collections.emptyList();
        }

        if (records == null) {
            records = new ArrayList<>(container.getContainerHeader().getNofRecords());
        }

        for (final Slice slice : container.getSlices()) {
            records.addAll(slice.getRecords(samFileHeader, validationStringency));
        }

        return records;
    }
}
