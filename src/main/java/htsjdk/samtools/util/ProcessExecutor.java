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

package htsjdk.samtools.util;

import htsjdk.samtools.SAMException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Utility class that will execute sub processes via Runtime.getRuntime().exec(...) and read
 * off the output from stderr and stdout of the sub process. This implementation uses a different
 * thread to read each stream: the current thread for stdout and another, internal thread for 
 * stderr. This utility is able to handle concurrent executions, spawning as many threads as
 * are required to handle the concurrent load.
 *
 * @author Doug Voet (dvoet at broadinstitute dot org)
 */
public class ProcessExecutor {
    private static final Log log = Log.getInstance(ProcessExecutor.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(r, "ProcessExecutor Thread");
        }
    });
    
    /**
     * Executes the command via Runtime.getRuntime().exec() then writes stderr to log.error
     * and stdout to log.info and blocks until the command is complete.
     * 
     * @see Runtime#exec(String)
     * 
     * @param command command string
     * @return return code of command
     */
    public static int execute(final String command) {
        try {
            final Process process = Runtime.getRuntime().exec(command);
            return readStreamsAndWaitFor(process);
        } catch (Throwable t) {
            throw new SAMException("Unexpected exception executing [" + htsjdk.samtools.util.StringUtil.join(" ", command) + "]", t);
        }
    }

    /**
     * Executes the command via Runtime.getRuntime().exec() then writes stderr to log.error
     * and stdout to log.info and blocks until the command is complete.
     * 
     * @see Runtime#exec(String[])
     * 
     * @param commandParts command string
     * @return return code of command
     */
    public static int execute(final String[] commandParts) {
        return execute(commandParts, null);
    }

    /**
     * Executes the command via Runtime.getRuntime().exec(), writes <code>outputStreamString</code>
     * to the process output stream if it is not null, then writes stderr to log.error
     * and stdout to log.info and blocks until the command is complete.
     *
     * @see Runtime#exec(String[])
     *
     * @param commandParts command string
     * @return return code of command
     */
    public static int execute(final String[] commandParts, String outputStreamString) {
        try {
            final Process process = Runtime.getRuntime().exec(commandParts);
            if (outputStreamString != null) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                writer.write(outputStreamString);
                writer.newLine();
                writer.close();
            }
            return readStreamsAndWaitFor(process);
        } catch (Throwable t) {
            throw new SAMException("Unexpected exception executing [" + htsjdk.samtools.util.StringUtil.join(" ", commandParts) + "]", t);
        }
    }

    public static String executeAndReturnResult(final String command) {
        try {
            final Process process = Runtime.getRuntime().exec(command);
            final StringBuilderProcessOutputReader err = new StringBuilderProcessOutputReader(process.getErrorStream());
            final Future<?> stderrReader = executorService.submit(err);
            final StringBuilderProcessOutputReader stdout = new StringBuilderProcessOutputReader(process.getInputStream());
            stdout.run();
            // wait for stderr reader to be done
            stderrReader.get();
            final int result = process.waitFor();
            return result == 0 ? stdout.getOutput() : err.getOutput();
        } catch (Throwable t) {
            throw new SAMException("Unexpected exception executing [" + command + "]", t);
        }

    }

    public static class ExitStatusAndOutput {
        public final int exitStatus;
        public final String stdout;
        /** May be null if interleaved */
        public final String stderr;

        public ExitStatusAndOutput(int exitStatus, String stdout, String stderr) {
            this.exitStatus = exitStatus;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    /**
     * Execute the command and capture stdout and stderr.
     * @return Exit status of command, and both stderr and stdout interleaved into stdout attribute.
     */
    public static ExitStatusAndOutput executeAndReturnInterleavedOutput(final String command) {
        try {
            final Process process = Runtime.getRuntime().exec(command);
            return interleaveProcessOutput(process);

        } catch (Throwable t) {
            throw new SAMException("Unexpected exception executing [" + command + "]", t);
        }
    }

    /**
     * Execute the command and capture stdout and stderr.
     * @return Exit status of command, and both stderr and stdout interleaved into stdout attribute.
     */
    public static ExitStatusAndOutput executeAndReturnInterleavedOutput(final String[] commandArray) {
        try {
            final Process process = Runtime.getRuntime().exec(commandArray);
            return interleaveProcessOutput(process);

        } catch (Throwable t) {
            throw new SAMException("Unexpected exception executing [" + StringUtil.join(" ", commandArray) + "]", t);
        }
    }

    private static ExitStatusAndOutput interleaveProcessOutput(final Process process) throws InterruptedException, IOException {
        final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        final StringBuilder sb = new StringBuilder();

        String stdoutLine = null;
        String stderrLine = null;
        while ((stderrLine = stderrReader.readLine()) != null ||
                (stdoutLine = stdoutReader.readLine()) != null) {
            if (stderrLine!= null) sb.append(stderrLine).append('\n');
            if (stdoutLine!= null) sb.append(stdoutLine).append('\n');
            stderrLine = null;
            stdoutLine = null;
        }
        return new ExitStatusAndOutput(process.waitFor(), sb.toString(), null);

    }

    private static int readStreamsAndWaitFor(final Process process)
            throws InterruptedException, ExecutionException {
        final Future<?> stderrReader = executorService.submit(new LogErrorProcessOutputReader(process.getErrorStream()));
        new LogInfoProcessOutputReader(process.getInputStream()).run();
        // wait for stderr reader to be done
        stderrReader.get();
        return process.waitFor();
    }


    /**
     * Runnable that reads off the given stream and logs it somewhere.
     */
    private static abstract class ProcessOutputReader implements Runnable {
        private final BufferedReader reader;
        public ProcessOutputReader(final InputStream stream) {
            reader = new BufferedReader(new InputStreamReader(stream));
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    write(line);
                }
            } catch (IOException e) {
                throw new SAMException("Unexpected exception reading from process stream", e);
            }
        }
        
        protected abstract void write(String message);
    }


    private static class LogErrorProcessOutputReader extends ProcessOutputReader {
        public LogErrorProcessOutputReader(final InputStream stream) { super(stream); }
        @Override protected void write(final String message) { log.error(message); }
    }

    private static class LogInfoProcessOutputReader extends ProcessOutputReader {
        public LogInfoProcessOutputReader(final InputStream stream) { super(stream); }
        @Override protected void write(final String message) { log.info(message); }
    }

    private static class StringBuilderProcessOutputReader extends ProcessOutputReader {
        private final StringBuilder sb = new StringBuilder();
        public StringBuilderProcessOutputReader(final InputStream stream) { super(stream); }
        @Override protected void write(final String message) { sb.append(message).append('\n'); }
        public String getOutput() { return sb.toString(); }
    }

}
