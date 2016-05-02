/** answer to https://www.biostars.org/p/77802/#77966 */
(record.referenceIndex==record.mateReferenceIndex && record.referenceIndex>=0 && record.readNegativeStrandFlag!=record.mateNegativeStrandFlag &&  ((record.mateNegativeStrandFlag && record.alignmentStart < record.mateAlignmentStart ) || (record.readNegativeStrandFlag && record.mateAlignmentStart < record.alignmentStart ) ))
