package htsjdk.variant.variantcontext;

import htsjdk.HtsjdkTest;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import org.testng.annotations.Test;

public class VariantContextUtilsTest extends HtsjdkTest {

    /** Loads classes from this JVM's class path, but none of JEXL's. */
    private static final class ClassLoaderWithoutJexl extends URLClassLoader {
        ClassLoaderWithoutJexl() {
            super(classPath(), ClassLoader.getPlatformClassLoader());
        }

        private static URL[] classPath() {
            return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                    .map(entry -> {
                        try {
                            return new File(entry).toURI().toURL();
                        } catch (final MalformedURLException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toArray(URL[]::new);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.commons.jexl2.")) throw new ClassNotFoundException(name);
            return super.loadClass(name, resolve);
        }
    }

    @Test
    public void theClassInitialisesWithoutJexlOnTheClassPath() throws Exception {
        try (ClassLoaderWithoutJexl loader = new ClassLoaderWithoutJexl()) {
            Class.forName(VariantContextUtils.class.getName(), true, loader);
        }
    }
}
