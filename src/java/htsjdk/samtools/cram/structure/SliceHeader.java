/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.structure;

class SliceHeader {
    // as defined in the specs:
    public int sequenceId = -1;
    public int alignmentStart = -1;
    public int alignmentSpan = -1;
    public int nofRecords = -1;
    public long globalRecordCounter = -1;
    public int nofBlocks = -1;
    public int[] contentIDs;
    public int embeddedRefBlockContentID;
    public byte[] refMD5;
}
