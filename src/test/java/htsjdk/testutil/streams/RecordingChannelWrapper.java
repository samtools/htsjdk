package htsjdk.testutil.streams;

import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A channel wrapper, of the kind readers take for prefetching or caching, that hands back each channel unchanged and
 * remembers it, so that a test can check that whatever opened the channels has closed them all.
 */
public class RecordingChannelWrapper implements Function<SeekableByteChannel, SeekableByteChannel> {
    private final List<SeekableByteChannel> channels = new ArrayList<>();

    @Override
    public synchronized SeekableByteChannel apply(final SeekableByteChannel channel) {
        channels.add(channel);
        return channel;
    }

    /** @return how many channels have been opened through this wrapper */
    public synchronized int openedCount() {
        return channels.size();
    }

    /** @return how many of the channels opened through this wrapper are still open */
    public synchronized long stillOpenCount() {
        return channels.stream().filter(SeekableByteChannel::isOpen).count();
    }
}
