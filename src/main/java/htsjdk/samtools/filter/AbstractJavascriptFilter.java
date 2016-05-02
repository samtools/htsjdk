/*
 * The MIT License
 *
 * Copyright (c) 2015 Pierre Lindenbaum @yokofakun Institut du Thorax - Nantes - France
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
package htsjdk.samtools.filter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.RuntimeScriptException;

/**
 * Javascript filter with HEADER type containing TYPE records. contains two
 * static method to get a SAM Read filter or a VariantFilter.
 * 
 * warning: tools, like galaxy, using this class are not safe because a script
 * can access the filesystem.
 * 
 * @author Pierre Lindenbaum PhD
 */
public abstract class AbstractJavascriptFilter<HEADER, TYPE> {
    public static final String DEFAULT_HEADER_KEY = "header";
    /** compiled user script */
    private CompiledScript script = null;

    /** javascript bindings */
    protected Bindings bindings;

    /**
     * constructor using a java.io.File script, compiles the script, puts
     * 'header' in the bindings
     */
    protected AbstractJavascriptFilter(final File scriptFile, final HEADER header) throws IOException {
        this(new FileReader(scriptFile), header);
    }

    /**
     * constructor using a java.lang.String script, compiles the script, puts
     * 'header' in the bindings
     */
    protected AbstractJavascriptFilter(final String scriptExpression, final HEADER header) {
        this(new StringReader(scriptExpression), header);
    }

    /**
     * Constructor, compiles script, put header in the bindings
     * 
     * @param scriptReader
     *            reader containing the script. will be closed.
     * @param header
     *            the header to be injected in the javascript context
     */
    protected AbstractJavascriptFilter(final Reader scriptReader, final HEADER header) {
        final ScriptEngineManager manager = new ScriptEngineManager();
        /* get javascript engine */
        final ScriptEngine engine = manager.getEngineByName("js");
        if (engine == null) {
            CloserUtil.close(scriptReader);
            throw new RuntimeScriptException("The embedded 'javascript' engine is not available in java. "
                    + "Do you use the SUN/Oracle Java Runtime ?");
        }
        if (scriptReader == null) {
            throw new RuntimeScriptException("missing ScriptReader.");
        }
        
        try {
            final Compilable compilingEngine = getCompilable(engine);
            this.script = compilingEngine.compile(scriptReader);
        } catch (ScriptException err) {
            throw new RuntimeScriptException("Script error in input", err);
        } finally {
            CloserUtil.close(scriptReader);
        }

        /*
         * create the javascript bindings and put the file header in that
         * context
         */
        this.bindings = new SimpleBindings();
        this.bindings.put(DEFAULT_HEADER_KEY, header);
    }

    /** return a javascript engine as a Compilable */
    private static Compilable getCompilable(final ScriptEngine engine) {
        if (!(engine instanceof Compilable)) {
            throw new IllegalStateException("The current javascript engine (" + engine.getClass()
                    + ") cannot be cast to Compilable. " + "Do you use the SUN/Oracle Java Runtime ?");
        }
        return Compilable.class.cast(engine);
    }

    /** returns key used for header binding */
    public String getHeaderKey() {
        return DEFAULT_HEADER_KEY;
    }

    /** returns key used for record binding */
    public abstract String getRecordKey();

    /**
     * Evaluates this predicate on the given argument
     * 
     * @param record
     *            the record to test. It will be inject in the javascript
     *            context using getRecordKey()
     * @return true (keep) if the user script returned 1 or true, else false
     *         (reject).
     */
    protected boolean accept(final TYPE record) {
        try {
            /* insert the record into the javascript context */
            this.bindings.put(getRecordKey(), record);
            /* get the result */
            final Object result = this.script.eval(this.bindings);
            if (result == null) {
                return false;
            } else if (result instanceof Boolean) {
                return Boolean.TRUE.equals(result);
            } else if (result instanceof Number) {
                return (((Number) result).intValue() == 1);
            } else {
                return false;
            }
        } catch (ScriptException err) {
            throw new RuntimeException(err);
        }
    }
}
