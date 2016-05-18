/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests for MultiIterator relating to group collision.
 *
 * @author Dave Tefft, Andre Mesarovic
 */
public class MergingSamRecordIteratorGroupCollisionTest {

    private GroupAdapter padapter = new ProgramGroupAdapter();
    private GroupAdapter radapter = new ReadGroupAdapter();

    @DataProvider(name = "adapters")
    public Object[][] adapters() {
        return new Object[][]{
                {new ProgramGroupAdapter()},
                {new ReadGroupAdapter()}
        };
    }


    /** Test for groups with same ID and same attributes */
    @Test(dataProvider = "adapters")
    public void testSameIdsSameAttrs(GroupAdapter adapter) {
        boolean addReadGroup = addReadGroup(adapter);

        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group0 = adapter.newGroup("0");
        adapter.setAttribute(group0, "Hi Mom!");
        adapter.setBuilderGroup(builder1, group0);
        builder1.addFrag("read1", 20, 28833, addReadGroup);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group1 = adapter.newGroup("0");
        adapter.setAttribute(group1, "Hi Mom!");
        adapter.setBuilderGroup(builder2, group1);
        builder2.addFrag("read2", 19, 28833, addReadGroup);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);
        final List<? extends AbstractSAMHeaderRecord> outputGroups = adapter.getGroups(headerMerger.getMergedHeader());
        Assert.assertEquals(outputGroups.size(), 1);

        Assert.assertTrue(adapter.equivalent(outputGroups.get(0), group0));

        assertRecords(headerMerger, readers, adapter, addReadGroup, "0", "0");
        CloserUtil.close(readers);
    }

    /** Test for groups with same ID but different attributes */
    // @Test(dataProvider = "adapters")
    public void testSameIdsDifferentAttrs(GroupAdapter adapter) {
        boolean addReadGroup = addReadGroup(adapter);

        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group0 = adapter.newGroup("0");
        adapter.setAttribute(group0, "Hi Mom!");
        adapter.setBuilderGroup(builder1, group0);
        builder1.addFrag("read1", 20, 28833, addReadGroup);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group1 = adapter.newGroup("0");
        adapter.setAttribute(group1, "Hi Dad!");
        adapter.setBuilderGroup(builder2, group1);
        builder2.addFrag("read2", 19, 28833, addReadGroup);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);
        final List<? extends AbstractSAMHeaderRecord> outputGroups = adapter.getGroups(headerMerger.getMergedHeader());
        Assert.assertEquals(outputGroups.size(), 2);
        Assert.assertTrue(adapter.equivalent(outputGroups.get(0), group0));
        Assert.assertTrue(adapter.equivalent(outputGroups.get(1), group1));

        assertRecords(headerMerger, readers, adapter, addReadGroup, "0", "0.1");
        CloserUtil.close(readers);
    }


    /** Test for groups with different ID and same attributes */
    // @Test(dataProvider = "adapters")
    public void testDifferentIdsSameAttrs(GroupAdapter adapter) {
        boolean addReadGroup = addReadGroup(adapter);
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group0 = adapter.newGroup("0");
        adapter.setAttribute(group0, "Hi Mom!");
        adapter.setBuilderGroup(builder1, group0);
        builder1.addFrag("read1", 20, 28833, addReadGroup);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group1 = adapter.newGroup("55");
        adapter.setAttribute(group1, "Hi Mom!");
        adapter.setBuilderGroup(builder2, group1);
        builder2.addFrag("read2", 19, 28833, addReadGroup);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);
        final List<? extends AbstractSAMHeaderRecord> outputGroups = adapter.getGroups(headerMerger.getMergedHeader());
        Assert.assertEquals(outputGroups.size(), 2);
        Assert.assertTrue(adapter.equivalent(outputGroups.get(0), group0));
        Assert.assertTrue(adapter.equivalent(outputGroups.get(1), group1));

        assertRecords(headerMerger, readers, adapter, addReadGroup, "0", "55");
        CloserUtil.close(readers);
    }


    /** Test for groups with different ID and different attributes */
    @Test(dataProvider = "adapters")
    public void testDifferentIdsDifferentAttrs(GroupAdapter adapter) {
        boolean addReadGroup = addReadGroup(adapter);
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group0 = adapter.newGroup("0");
        adapter.setAttribute(group0, "Hi Mom!");
        adapter.setBuilderGroup(builder1, group0);
        builder1.addFrag("read1", 20, 28833, addReadGroup);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, addReadGroup);
        final AbstractSAMHeaderRecord group1 = adapter.newGroup("55");
        adapter.setAttribute(group1, "Hi Dad!");
        adapter.setBuilderGroup(builder2, group1);
        builder2.addFrag("read2", 19, 28833, addReadGroup);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);
        final List<? extends AbstractSAMHeaderRecord> outputGroups = adapter.getGroups(headerMerger.getMergedHeader());
        Assert.assertEquals(outputGroups.size(), 2);
        Assert.assertTrue(adapter.equivalent(outputGroups.get(0), group0));
        Assert.assertTrue(adapter.equivalent(outputGroups.get(1), group1));

        assertRecords(headerMerger, readers, adapter, addReadGroup, "0", "55");
        CloserUtil.close(readers);
    }

    @Test(dataProvider = "adapters")
    public void differentIds(GroupAdapter adapter) throws Exception {
        final String[] groupIds = {"group1", "group2"};
        final List<? extends AbstractSAMHeaderRecord> groups = adapter.createGroups(groupIds);
        Assert.assertEquals(groups.size(), 2);
        int i = 0;
        for (final AbstractSAMHeaderRecord g : groups) {
            Assert.assertEquals(groupIds[i], adapter.getGroupId(g));
            i++;
        }
    }

    @Test(dataProvider = "adapters")
    public void sameIds(GroupAdapter adapter) throws Exception {
        final String[] groupIds = {"group1", "group1"};

        final List<? extends AbstractSAMHeaderRecord> groups = adapter.createGroups(groupIds);
        Assert.assertEquals(groups.size(), 1);
        AbstractSAMHeaderRecord group = groups.get(0);
        Assert.assertEquals(adapter.getGroupId(group), "group1");
    }

    /**
     * List of program groups from the input files are merged, and renumbered, and SAMRecords
     * with PG tags get assigned the updated PG ID.
     * Original ProgramRecord-specific test.
     */
    @Test
    public void testMergingProgramGroups() {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        final SAMProgramRecord program1 = new SAMProgramRecord("0");
        program1.setCommandLine("Hi, Mom!");
        builder1.setProgramRecord(program1);
        builder1.addFrag("read1", 20, 28833, false);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        final SAMProgramRecord program2 = new SAMProgramRecord("0");
        program2.setCommandLine("Hi, Dad!");
        program2.setProgramVersion("123");
        builder2.setProgramRecord(program2);
        builder2.addFrag("read2", 19, 28833, false);
        // No PG tag on this record
        builder2.setProgramRecord(null);
        builder2.addFrag("read3", 19, 28833, false);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);

        final List<SAMProgramRecord> outputProgramGroups = headerMerger.getMergedHeader().getProgramRecords();
        Assert.assertEquals(outputProgramGroups.size(), 2);
        Assert.assertTrue(outputProgramGroups.get(0).equivalent(program1));
        Assert.assertTrue(outputProgramGroups.get(1).equivalent(program2));

        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(headerMerger, readers, false);
        SAMRecord samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), "0");
        samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), "0.1");
        samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), null);
        Assert.assertFalse(iterator.hasNext());
        CloserUtil.close(readers);
    }

    private void assertRecords(SamFileHeaderMerger headerMerger, Collection<SamReader> readers,
                               GroupAdapter adapter, boolean addReadGroup, String... attrs) {
        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(headerMerger, readers, addReadGroup);
        for (int j = 0; j < attrs.length; j++) {
            SAMRecord samRecord = iterator.next();
            Assert.assertEquals(samRecord.getAttribute(adapter.getTagName()), attrs[j]);
        }
        Assert.assertFalse(iterator.hasNext());
    }

    private boolean addReadGroup(GroupAdapter adapter) {
        return adapter instanceof ProgramGroupAdapter;
    }

    /**
     * List of program groups from the input files are merged, and renumbered, and SAMRecords
     * with PG tags get assigned the updated PG ID.
     * Original ProgramRecord-specific test.
     */
    @Test
    public void testMergingProgramGroupsWithThreeReaders() {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        final SAMProgramRecord program1 = new SAMProgramRecord("0");
        program1.setCommandLine("Hi, Mom!");
        program1.setProgramVersion("123");
        builder1.setProgramRecord(program1);
        builder1.addFrag("read1", 20, 28833, false);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        final SAMProgramRecord program2 = new SAMProgramRecord("0");
        program2.setCommandLine("Hi, Mom!");
        program2.setProgramVersion("123");
        builder2.setProgramRecord(program2);
        builder2.addFrag("read2", 19, 28833, false);


        final SAMRecordSetBuilder builder3 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        final SAMProgramRecord program3 = new SAMProgramRecord("0");
        program3.setCommandLine("Hi, Dad!");
        builder3.setProgramRecord(program3);
        builder3.addFrag("read3", 19, 28833, false);


        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());
        readers.add(builder3.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());
        headers.add(readers.get(2).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);
        final List<SAMProgramRecord> outputProgramGroups = headerMerger.getMergedHeader().getProgramRecords();
        Assert.assertEquals(outputProgramGroups.size(), 2);
        Assert.assertTrue(outputProgramGroups.get(0).equivalent(program1));
        Assert.assertTrue(outputProgramGroups.get(1).equivalent(program3));


        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(headerMerger, readers, false);
        SAMRecord samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), "0");
        samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), "0");
        samRecord = iterator.next();
        Assert.assertEquals(samRecord.getAttribute(SAMTag.PG.name()), "0.1");
        Assert.assertFalse(iterator.hasNext());
        CloserUtil.close(readers);
    }

    /**
     * List of program groups from the input files are merged, and renumbered, and SAMRecords
     * with PG tags get assigned the updated PG ID.
     * Original ProgramRecord-specific test.
     */
    @Test
    public void testMergingMultipleReadGroups() {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, false);
        builder1.setReadGroup(createSAMReadGroupRecord("a0"));
        builder1.setReadGroup(createSAMReadGroupRecord("a1"));
        builder1.setReadGroup(createSAMReadGroupRecord("a2"));
        builder1.addFrag("read1", 20, 28833, false);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname, false);
        builder2.setReadGroup(createSAMReadGroupRecord("0"));
        builder2.setReadGroup(createSAMReadGroupRecord("a1"));      //collision 1
        builder2.setReadGroup(createSAMReadGroupRecord("a2"));      //collision 2
        builder2.setReadGroup(createSAMReadGroupRecord("a1.1"));    //doesn't collide
        builder2.setReadGroup(createSAMReadGroupRecord("a2.1"));    //doesn't collide
        builder2.setReadGroup(createSAMReadGroupRecord("a2.4.9"));  //doesn't collide
        builder2.setReadGroup(createSAMReadGroupRecord("a2.4"));    //collision
        builder2.addFrag("read1", 20, 28833, false);

        final List<SamReader> readers = new ArrayList<SamReader>();
        readers.add(builder1.getSamReader());
        readers.add(builder2.getSamReader());

        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        headers.add(readers.get(0).getFileHeader());
        headers.add(readers.get(1).getFileHeader());

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headers, false);

        final List<? extends AbstractSAMHeaderRecord> outputGroups = headerMerger.getMergedHeader().getReadGroups();
        // "0, a0, a1, a1.1, a1.3, a2, a2.1, a2.6
        //the merged read groups are sorted in order
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(0)).getReadGroupId(), "0"); //0
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(1)).getReadGroupId(), "a0"); //1
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(2)).getReadGroupId(), "a1"); //2
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(3)).getReadGroupId(), "a1.1"); //3
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(4)).getReadGroupId(), "a1.2"); //4
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(5)).getReadGroupId(), "a2");  //5
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(6)).getReadGroupId(), "a2.1");
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(7)).getReadGroupId(), "a2.4");
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(8)).getReadGroupId(), "a2.4.9");
        Assert.assertEquals(((SAMReadGroupRecord) outputGroups.get(9)).getReadGroupId(), "a2.4.A");
        Assert.assertEquals(outputGroups.size(), 10);
        CloserUtil.close(readers);
    }

    private SAMReadGroupRecord createSAMReadGroupRecord(String id) {
        SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord(id);
        readGroupRecord.setAttribute(SAMTag.SM.name(), Double.toString(Math.random()));
        return readGroupRecord;
    }

    /** Captures commonality between ProgramRecord and ReadGroup for having one set of tests */
    abstract class GroupAdapter {

        /** Gets the group's group ID */
        abstract String getGroupId(AbstractSAMHeaderRecord group);

        /** Gets the groups from header */
        abstract List<? extends AbstractSAMHeaderRecord> getGroups(SAMFileHeader header);

        /** Gets the group's 'name' tag */
        abstract String getTagName();

        /** Creates groups for specified IDs */
        abstract List<? extends AbstractSAMHeaderRecord> createGroups(final String[] groupIds);

        /** Sets a group-specific attribute - for CL for ProgramRecord CL, for PU for ReadGroup */
        abstract void setAttribute(AbstractSAMHeaderRecord group, String value);

        /** Creates a new group */
        abstract AbstractSAMHeaderRecord newGroup(String groupId);

        /** Sets the group for the builder */
        abstract void setBuilderGroup(SAMRecordSetBuilder builder, AbstractSAMHeaderRecord group);

        /** Attributes equivalent */
        abstract boolean equivalent(AbstractSAMHeaderRecord group1, AbstractSAMHeaderRecord group2);

        SamReader newFileReader() {
            final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
            builder.addFrag("read_28833_29006_6945", 20, 28833, false); // ok
            return builder.getSamReader();
        }
    }

    class ProgramGroupAdapter extends GroupAdapter {
        String getGroupId(AbstractSAMHeaderRecord group) {
            return ((SAMProgramRecord) group).getProgramGroupId();
        }

        List<? extends AbstractSAMHeaderRecord> getGroups(SAMFileHeader header) {
            return header.getProgramRecords();
        }

        String getTagName() {
            return SAMTag.PG.toString();
        }

        List<? extends AbstractSAMHeaderRecord> createGroups(final String[] groupIds) {
            final List<SamReader> readers = new ArrayList<SamReader>();
            for (final String groupId : groupIds) {
                final SamReader samReader = newFileReader();
                final List<SAMProgramRecord> records = new ArrayList<SAMProgramRecord>();
                final SAMProgramRecord record = new SAMProgramRecord(groupId);
                records.add(record);
                samReader.getFileHeader().setProgramRecords(records);
                readers.add(samReader);
            }

            final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
            for (final SamReader reader : readers) {
                headers.add(reader.getFileHeader());
            }
            CloserUtil.close(readers);

            final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headers, false);
            return fileHeaderMerger.getMergedHeader().getProgramRecords();
        }

        void setAttribute(AbstractSAMHeaderRecord group, String value) {
            ((SAMProgramRecord) group).setCommandLine(value);
        }

        AbstractSAMHeaderRecord newGroup(String id) {
            return new SAMProgramRecord(id);
        }

        void setBuilderGroup(SAMRecordSetBuilder builder, AbstractSAMHeaderRecord group) {
            builder.setProgramRecord((SAMProgramRecord) group);
        }

        boolean equivalent(AbstractSAMHeaderRecord group1, AbstractSAMHeaderRecord group2) {
            return ((SAMProgramRecord) group1).equivalent((SAMProgramRecord) group2);
        }
    }

    class ReadGroupAdapter extends GroupAdapter {
        String getGroupId(AbstractSAMHeaderRecord group) {
            return ((SAMReadGroupRecord) group).getReadGroupId();
        }

        List<? extends AbstractSAMHeaderRecord> getGroups(SAMFileHeader header) {
            return header.getReadGroups();
        }

        String getTagName() {
            return SAMTag.RG.toString();
        }

        List<? extends AbstractSAMHeaderRecord> createGroups(final String[] groupIds) {
            final List<SamReader> readers = new ArrayList<SamReader>();

            for (final String groupId : groupIds) {
                final SamReader samReader = newFileReader();
                final List<SAMReadGroupRecord> records = new ArrayList<SAMReadGroupRecord>();
                final SAMReadGroupRecord record = new SAMReadGroupRecord(groupId);
                records.add(record);
                samReader.getFileHeader().setReadGroups(records);
                readers.add(samReader);
            }
            final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
            for (final SamReader reader : readers) {
                headers.add(reader.getFileHeader());
            }
            CloserUtil.close(readers);
            final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headers, false);
            return fileHeaderMerger.getMergedHeader().getReadGroups();
        }

        void setAttribute(AbstractSAMHeaderRecord group, String value) {
            ((SAMReadGroupRecord) group).setPlatformUnit(value);
        }

        AbstractSAMHeaderRecord newGroup(String id) {
            SAMReadGroupRecord group = new SAMReadGroupRecord(id);
            group.setAttribute(SAMTag.SM.name(), id);
            return group;
        }

        void setBuilderGroup(SAMRecordSetBuilder builder, AbstractSAMHeaderRecord group) {
            builder.setReadGroup((SAMReadGroupRecord) group);
        }

        boolean equivalent(AbstractSAMHeaderRecord group1, AbstractSAMHeaderRecord group2) {
            return ((SAMReadGroupRecord) group1).equivalent((SAMReadGroupRecord) group2);
        }
    }
}
