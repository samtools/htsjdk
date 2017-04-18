#/bin/bash

# Check that Scala files only exist in the scala test dir and
# that java files do not reside in the scala test dir

if `find src | grep -v '^src/test/scala' | grep -q '\.scala$' ` ; then
	echo 'Found scala file(s) outside of scala test directory';
	find src | grep -v '^src/test/scala' | grep '\.scala$'
	exit 1; 
fi

if `find src/test/scala | grep -q '\.java$' ` ; then
        echo 'Found java file(s) in scala test directory';
	find src/test/scala | grep '\.java$'        
	exit 1;
fi

