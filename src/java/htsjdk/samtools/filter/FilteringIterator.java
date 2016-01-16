package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;

import java.util.Iterator;

/**
 * Filtering Iterator which takes a filter and an iterator and iterates through only those records
 * which are not rejected by the filter.
 * <p/>
 * $Id$
 *
 * @author Kathleen Tibbetts
 *
 * use {@link FilteringSamIterator} instead
 */

@Deprecated /** use {@link FilteringSamIterator} instead **/
public class FilteringIterator extends FilteringSamIterator{

    public FilteringIterator(final Iterator<SAMRecord> iterator, final SamRecordFilter filter, final boolean filterByPair) {
        super(iterator, filter, filterByPair);
    }

    public FilteringIterator(final Iterator<SAMRecord> iterator, final SamRecordFilter filter) {
        super(iterator, filter);
    }

}
