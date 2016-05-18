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
package htsjdk.tribble.bed;

import htsjdk.tribble.annotation.Strand;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 *  Feature from a BED file without exon blocks.
 */
public class SimpleBEDFeature implements BEDFeature {
    protected String chr;
    protected int start = -1;
    protected int end = -1;
    protected Strand strand = Strand.NONE;
    private String name = "";
    private float score = Float.NaN;
    private String type = "";
    private Color color;
    private String description;//protected float confidence;
    //private String identifier;
    private String link;

    public SimpleBEDFeature(int start, int end, String chr) {
        this.start = start;
        this.end = end;
        this.chr = chr;
    }


    @Deprecated
    @Override
    public String getChr() {
        return getContig();
    }


    @Override
    public String getContig() {
        return chr;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public Strand getStrand() {
        return strand;
    }

    public void setStrand(Strand strand) {
        this.strand = strand;
    }

    public void setChr(String chr) {
        this.chr = chr;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    final static List<FullBEDFeature.Exon> emptyExonList = new ArrayList();

    public java.util.List<FullBEDFeature.Exon> getExons() {
        return emptyExonList;
    }
}
