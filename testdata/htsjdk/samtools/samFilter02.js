/** accept record if second base of DNA is a A */
function accept(r)
	{
	/* using substring instead of charAt because http://developer.actuate.com/community/forum/index.php?/topic/25434-javascript-stringcharati-wont-return-a-character/ */
	return r.getReadString().length()>2 &&
		r.getReadString().substring(1,2)=="A";
	}

accept(record);
