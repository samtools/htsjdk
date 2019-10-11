package htsjdk.tribble.util;

import java.net.URL;

/**
 * A factory for creating {@link  URLHelper} instances.
 */
public interface URLHelperFactory {

    /**
     * @param url
     * @return a {@link URLHelper} object for the given URL
     */
    default URLHelper getHelper(URL url) {
        return new RemoteURLHelper(url);
    }

}
