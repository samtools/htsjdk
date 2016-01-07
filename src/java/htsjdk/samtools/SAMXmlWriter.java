/*
 * The MIT License
 *
 * Copyright (c) 2015 Pierre Lindenbaum @yokofakun Institut du Thorax Nantes France
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

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.ProgressLoggerInterface;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Writer for xml SAM files using JAXB marshalling
 * @author Pierre Lindenbaum @yokofakun
 */
public class SAMXmlWriter implements SAMFileWriter {

    /** output stream */
    private final Writer out;
    /** XML stream */
    private XMLStreamWriter xw;
    /** xml stream writer doesn't close the underlying stream */
    private final boolean close_stream_at_end;
    /** xml fragment (don't print XML header ) */
    private final boolean xml_fragment;
    /** progress */
    private ProgressLoggerInterface progress = null;
    /** sam file header */
    private final SAMFileHeader header;
    /** xml marshaller */
    private Marshaller marshaller;

    /**
     * Constructs a SAMXmlWriter that writes to a File.
     * 
     * @param header sam header
     * @param file Where to write the output.
     */
    public SAMXmlWriter(final SAMFileHeader header, final File file) {
        if( header == null) throw new IllegalArgumentException("null header");
        if( file == null) throw new IllegalArgumentException("null file");
        try {
            this.header = header;
            final XMLOutputFactory xmlOutputFactory = XMLOutputFactory
                    .newFactory();
            this.out = new FileWriter(file);
            this.xw = xmlOutputFactory.createXMLStreamWriter(this.out);
            this.close_stream_at_end = true;
            this.xml_fragment = false;
            writeHeader();
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Constructs a SAMXmlWriter that writes to a XMLStreamWriter.
     * 
     * @param header sam header
     * @param xwriter existing XMLStreamWriter
     */
    public SAMXmlWriter(final SAMFileHeader header,
            final XMLStreamWriter xwriter) {
        if( header == null) throw new IllegalArgumentException("null header");
        if( xwriter == null) throw new IllegalArgumentException("null file");
        try {
            this.header = header;
            this.out = null;
            this.xw = xwriter;
            this.close_stream_at_end = false;
            this.xml_fragment = true;
            writeHeader();
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Constructs a SAMXmlWriter that writes to a XMLStreamWriter.
     * 
     * @param header sam header
     * @param outputStream existing OutputStream
     */
    public SAMXmlWriter(final SAMFileHeader header,
            final OutputStream outputStream) {
        try {
            this.header = header;
            final XMLOutputFactory xmlOutputFactory = XMLOutputFactory
                    .newFactory();
            this.out = new PrintWriter(outputStream);
            this.xw = xmlOutputFactory.createXMLStreamWriter(this.out);
            this.close_stream_at_end = false;
            this.xml_fragment = true;
            writeHeader();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeIOException(e);
        }
    }

    /** called by the constructors:
     * creates the XML marshaller
     * writes the XML declaration if needed,
     * starts the XML document,
     * writes the header
     * starts the read section
     */
    private void writeHeader() {
        try {
            /* create the JAXB context */
            final JAXBContext context = JAXBContext.newInstance(
                    SAMFileHeader.class,
                    SAMRecord.class
                    );
            this.marshaller = context.createMarshaller();
            /* never print the XML declaration */
            this.marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            if (!this.xml_fragment)
                this.xw.writeStartDocument("UTF-8", "1.0");
            this.xw.writeStartElement("sam-file");
            this.marshaller.marshal(this.header, this.xw);
            this.xw.writeStartElement("reads");
        } catch (JAXBException err) {
            throw new RuntimeException("Java IO/XML error", err);
        } catch (XMLStreamException err) {
            throw new RuntimeException("Java IO/XML error", err);
        }
    }

    @Override
    public void addAlignment(final SAMRecord alignment) {
        if (this.xw == null)
            throw new IllegalStateException("xml stream closed");
        try {
            this.marshaller.marshal(alignment, this.xw);
            if (this.progress != null)
                this.progress.record(alignment);
        } catch (JAXBException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return this.header;
    }

    @Override
    public void setProgressLogger(final ProgressLoggerInterface progress) {
        this.progress = progress;
    }

    @Override
    public void close() {
        if (this.xw != null) {
            try {
                this.xw.writeEndElement();// reads
                this.xw.writeEndElement();// sam file
                if (!this.xml_fragment)
                    this.xw.writeEndDocument();
                this.xw.flush();
                this.xw = null;
            } catch (XMLStreamException err) {
                throw new RuntimeIOException(err);
            }
        }
        if (this.close_stream_at_end) {
            CloserUtil.close(this.out);
        }
    }
}
