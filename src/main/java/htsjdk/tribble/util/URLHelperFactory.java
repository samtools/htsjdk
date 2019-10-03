package htsjdk.tribble.util;

import java.net.URL;

public interface URLHelperFactory {

    URLHelper getHelper(URL url);

}
