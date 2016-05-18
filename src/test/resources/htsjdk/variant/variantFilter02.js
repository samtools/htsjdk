/** prints a VARIATION if two samples at least have a DP>100 */ 
function myfilterFunction(thevariant)
    {
    var samples=header.genotypeSamples;
    var countOkDp=0;


    for(var i=0; i< samples.size();++i)
        {
        var sampleName=samples.get(i);
        if(! variant.hasGenotype(sampleName)) continue;
        var genotype = thevariant.genotypes.get(sampleName);
        if( ! genotype.hasDP()) continue;
        var dp= genotype.getDP();
        if(dp > 100 ) countOkDp++;
        }
    return (countOkDp>2)
    }

myfilterFunction(variant)
