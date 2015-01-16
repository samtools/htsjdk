/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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

import htsjdk.samtools.util.Log;

import java.net.URL;
import java.net.URLClassLoader;


/**
 * Factory for creating custom readers for accessing API based resources, 
 * e.g. ga4gh.
 * The configuration is controlled via custom_reader property (@see Defaults).
 * This allows injection of such readers from code bases outside HTSJDK.
 */
public class CustomReaderFactory {
  private final static Log LOG = Log.getInstance(CustomReaderFactory.class);
  /**
   * Interface to be implemented by custom factory classes that register
   * themselves with this factory and are loaded dynamically.
   */
  public interface ICustomReaderFactory {
    SamReader open(URL url);
  }
  
  private static final CustomReaderFactory DEFAULT_FACTORY;
  private static CustomReaderFactory currentFactory;
  
  private String urlPrefix = "";
  private String factoryClassName = "";
  private String jarFile = "";
  private ICustomReaderFactory factory;
  
  static {
      DEFAULT_FACTORY = new CustomReaderFactory();
      currentFactory = DEFAULT_FACTORY;
  }

  public static void setInstance(final CustomReaderFactory factory){
      currentFactory = factory;
  }
  
  public static void resetToDefaultInstance() {
    setInstance(DEFAULT_FACTORY);
  }

  public static CustomReaderFactory getInstance(){
      return currentFactory;
  }
  
  /**
   * Initializes factory based on the custom_reader property specification.
   */
  private CustomReaderFactory() {
    this(Defaults.CUSTOM_READER_FACTORY);
  }
  
  CustomReaderFactory(String cfg) {
    final String[] cfgComponents = cfg.split(",");
    if (cfgComponents.length < 2) {
      return;
    }
    urlPrefix = cfgComponents[0].toLowerCase();
    factoryClassName = cfgComponents[1];
    if (cfgComponents.length > 2) {
      jarFile = cfgComponents[2];
    }
  }
  
  /**
   * Lazily creates factory based on the configuration.
   * @return null if creation fails, factory instance otherwise.
   */
  private synchronized ICustomReaderFactory getFactory() {
    if (factory == null) {
      try {
        Class clazz = null;
        
        if (!jarFile.isEmpty()) {
          LOG.info("Attempting to load factory class " + factoryClassName + 
              " from " + jarFile);
          final URL jarURL = new URL("file:///"+jarFile);
          clazz = Class.forName(factoryClassName, true, 
                    new URLClassLoader (new URL[] { jarURL }, 
                        this.getClass().getClassLoader()));
        } else {
          LOG.info("Attempting to load factory class " + factoryClassName);
          clazz = Class.forName(factoryClassName);
        }
        
        factory = (ICustomReaderFactory)clazz.newInstance();
        LOG.info("Created custom factory for " + urlPrefix + " from " + 
            factoryClassName + " loaded from " + (jarFile.isEmpty() ? 
                " this jar" : jarFile));
      } catch (Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return factory;
  }
  
  /**
   * Check if the url is supposed to be handled by the custom factory and if so
   * attempt to create reader via an instance of this custom factory.
   * 
   * @return null if the url is not handled by this factory, SamReader otherwise.
   */
  public SamReader maybeOpen(URL url) {
    if (urlPrefix.isEmpty() || 
        !url.toString().toLowerCase().startsWith(urlPrefix)) {
      return null;
    }
    LOG.info("Attempting to open " + url + " with custom factory");
    final ICustomReaderFactory factory = getFactory();
    if (factory == null) {
      return null;
    }
    return factory.open(url);
  }
}
