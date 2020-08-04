package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsRecord;

import java.util.Iterator;

/**
 * Reads/alignment-specific query methods (reads-specific extension of HtsQuery)
 */
public interface ReadsQuery<R extends HtsRecord> {

    // query unmapped Reads
    default Iterator<R> queryUnmapped() {
        throw new IllegalStateException("Not implemented");
    };

    // Fetch the mate for the given read.
    default R queryMate(R rec) {
        throw new IllegalStateException("Not implemented");
    };
}
