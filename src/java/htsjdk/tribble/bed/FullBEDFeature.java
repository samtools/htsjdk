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
 * Object for full BED file.
 */
public class FullBEDFeature implements BEDFeature {

    protected String chr;
    protected int start = -1;
    protected int end = -1;
    protected Strand strand = Strand.NONE;
    private String name = "";
    private float score = Float.NaN;
    private String type = "";
    private Color color;
    private String description;
    //protected float confidence;
    //private String identifier;
    private String link;

    public FullBEDFeature(String chr, int start, int end) {
        this.start = start;
        this.end = end;
        this.chr = chr;
    }

    private List<FeatureSubSequence> exons = new ArrayList<FeatureSubSequence>();

    public List<FeatureSubSequence> getExons() {
        return exons;
    }

    public void setExons(List<FeatureSubSequence> exons) {
        this.exons = exons;
    }

    public void addExon(Exon exon) {
        exons.add(exon);
    }

    public void addExon(int exonStart, int exonEnd, int cdStart, int cdEnd, int exonNumber) {
        Exon exon = new Exon(exonStart, exonEnd);
        exon.setCodingStart(cdStart);
        exon.setCodingEnd(cdEnd);
        exon.setNumber(exonNumber);
        addExon(exon);
    }

    @Deprecated
    public String getChr() {
        return getContig();
    }

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

    /**
     * A sub region of a feature.  For example,  a Gene exon
     *
     * @author jrobinso
     */
    public class Exon implements FeatureSubSequence {

        int start;
        int end;
        /**
         * The index of the exon relative to the start codon.  The exon with the start
         * codon is number "1".
         */
        private int number;
        private int readingFrame = -1;

        /**
         * Coding start position.  This is the leftmost position of the coding region, not neccessarily the 5'utr end
         */
        private int codingStart;
        private int codingEnd;
        boolean utr = false;

        /** The position of the first base of this exon relative to the start of the mRNA.
         * This will correspond to either the beginning or end of the exon, depending on the strand */
        private int mrnaBase = -1;

        public void setMrnaBase(int base) {
            this.mrnaBase = base;
        }

        public Exon(int start, int end) {
            this.start = start;
            this.end = end;

            // By default the entre exon is a coding region
            this.codingStart = start;
            this.codingEnd = end;
        }

        public void setUTR(boolean utr) {
            this.utr = utr;
            if (strand == Strand.POSITIVE) {
                codingStart = codingEnd = end;
            } else {
                codingStart = codingEnd = start;
            }
        }

        public void setCodingStart(int codingStart) {
            this.codingStart = Math.max(start, codingStart);
        }

        public void setCodingEnd(int codingEnd) {
            this.codingEnd = Math.min(end, codingEnd);
        }

        public void setReadingFrame(int offset) {
            this.readingFrame = offset;
        }

        public void setPhase(int phase) {
            if (strand == Strand.POSITIVE) {
                readingFrame = phase;
            } else if (strand == Strand.NEGATIVE) {
                int modLen = (getCodingLength() - phase) % 3;
                readingFrame = modLen;
            }
        }

        public int getCdStart() {
            return codingStart;
        }

        public int getCdEnd() {
            return this.codingEnd;
        }

        public int getCodingLength() {
            return utr ? 0 : Math.max(0, codingEnd - codingStart + 1);
        }

        int getReadingShift() {
            return readingFrame;
        }


        public String getValueString(double position) {
            String msg = number > 0 ? "Exon number: " + number : "";
            return msg;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }
    }


    public class Exon2 implements FeatureSubSequence {

        /**
         * The index of the exon relative to the start codon.  The exon with the start
         * codon is number "1".
         */
        private int number;
        private int readingFrame = -1;

        /**
         * Coding start position.  This is the leftmost position of the coding region, not neccessarily the 5'utr end
         */
        private int start;
        private int end;
        private int codingStart;
        private int codingEnd;
        //private AminoAcidSequence aminoAcidSequence;
        boolean utr = false;

        // The position of the first base of this exon relative to the start of the mRNA.  This will correspond
        // to either the beginning or end of the exon, depending on the strand
        private int mrnaBase = -1;


        public Exon2(int start, int end, int codingStart, int codingDne) {

            this.start = start;
            this.end = end;
            this.codingStart = codingStart;
            this.codingEnd = codingDne;
        }

        @Override
        public void setMrnaBase(int base) {
            this.mrnaBase = base;
        }

        public int getAminoAcidNumber(int genomeCoordinate) {
            if (mrnaBase < 0) {
                return -1;
            }
            if (genomeCoordinate < getStart() || genomeCoordinate > getEnd()) {
                throw new IndexOutOfBoundsException();
            }
            if (getStrand() == Strand.POSITIVE) {
                int mrnaCoord = mrnaBase + (genomeCoordinate - codingStart) - 1;
                return mrnaCoord < 0 ? -1 : mrnaCoord / 3 + 1;

            } else if (getStrand() == Strand.NEGATIVE) {
                int mrnaCoord = mrnaBase + (codingEnd - genomeCoordinate);
                return mrnaCoord < 0 ? -1 : mrnaCoord / 3 + 1;

            } else {
                return 0;
            }
        }

        /**
         * Flag indicating that the entire exon is the UTR.
         *
         * @param utr
         */
        @Override
        public void setUTR(boolean utr) {
            this.utr = utr;
            if (getStrand() == Strand.POSITIVE) {
                codingStart = codingEnd = getEnd();
            } else {
                codingStart = codingEnd = getStart();
            }
        }

        /**
         * Method description
         *
         * @param codingStart
         */
        @Override
        public void setCodingStart(int codingStart) {
            this.codingStart = Math.max(getStart(), codingStart);
        }

        /**
         * Method description
         *
         * @param codingEnd
         */
        public void setCodingEnd(int codingEnd) {
            this.codingEnd = Math.min(getEnd(), codingEnd);
        }

        /**
         * Method description
         *
         * @param offset
         */
        @Override
        public void setReadingFrame(int offset) {
            this.readingFrame = offset;
        }

        /**
         * Method description
         *
         * @param phase
         */
        @Override
        public void setPhase(int phase) {
            if (getStrand() == Strand.POSITIVE) {
                readingFrame = phase;
            } else if (getStrand() == Strand.NEGATIVE) {
                int modLen = (getCodingLength() - phase) % 3;
                readingFrame = modLen;
            }
        }


        /**
         * Method description
         *
         * @return
         */
        @Override
        public int getCdStart() {
            return codingStart;
        }

        /**
         * Method description
         *
         * @return
         */
        @Override
        public int getCdEnd() {
            return this.codingEnd;
        }

        /**
         * Method description
         *
         * @return
         */
        @Override
        public int getCodingLength() {
            return utr ? 0 : Math.max(0, codingEnd - codingStart);
        }

        /**
         * This is exposed for unit tests.
         *
         * @return
         */
        int getReadingShift() {
            return readingFrame;
        }


        /**
         * Method description
         *
         * @return
         */
        /*
        public AminoAcidSequence getAminoAcidSequence() {
            if (aminoAcidSequence == null) {
                computeAminoAcidSequence();
            }
            return aminoAcidSequence;
        }
        */


        /*
        public void setAminoAcidSequence(AminoAcidSequence aminoAcidSequence) {
            this.aminoAcidSequence = aminoAcidSequence;
        }
        */

        /*
        private void computeAminoAcidSequence() {

            if (utr) {
                return;
            }
            int start = getStart();
            int end = getEnd();
            String chr = getChr();
            if (readingFrame >= 0) {
                int readStart = (codingStart > start) ? codingStart : start + readingFrame;
                int readEnd = Math.min(end, codingEnd);
                if (readEnd > readStart + 3) {
                    String genome = IGVModel.getInstance().getViewContext().getGenomeId();
                    aminoAcidSequence = AminoAcidManager.getAminoAcidSequence(genome, chr, readStart,
                            readEnd, getStrand());
                }
            }
        }
        */
        @Override
        public String getValueString(double position) {
            String msg = number > 0 ? "Exon number: " + number : "";
            int aaNumber = this.getAminoAcidNumber((int) position);
            if (aaNumber > 0) {
                msg += "<br>Amino acid number: " + aaNumber;
            }
            return msg;
        }

        @Override
        public int getNumber() {
            return number;
        }

        @Override
        public void setNumber(int number) {
            this.number = number;
        }

    }

}
