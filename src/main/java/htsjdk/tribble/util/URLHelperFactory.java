package htsjdk.tribble.util;

import java.net.URL;

/**
 * A factory for creating {@link  URLHelper} instances.  The factory contains a single function
 * @see #getHelper(URL) which should return a <code>URLHelper</code> instance for the given URL.
 */
public interface URLHelperFactory {

    /**
     * @param url
     * @return a {@link URLHelper} object for the given URL
     */
    URLHelper getHelper(URL url);

}
