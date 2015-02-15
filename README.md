# sfutils
Salesforce utility programs

AuditTrail.groovy

This is a Groovy  script that tries to parse the output from the Salesforce Audit Trail export.
It is a work in progress, but anyone is welcome to download it and try it out.  Suggestions
and bug reports are welcomed.

My initial thought was to generate the package.xml file from it, and that is still a
goal, but I figured it would also be useful to generate a report that developers could
use to cross-check their change sets or package.xml files (the latter for Ant deployment).

The most useful thing is that it allows you to enter the starting date to filter out
entries that are older than you care about.  Inside the code, you can add entries
to an "ignore list" to filter out things that belong to managed packages.  I plan
to try and figure out a way to make that configurable as well, but since this is
really a developer-and-admin tool, it is not a high priority.

$ groovy AuditTrail.groovy -h
usage: AuditTrail.groovy -[hfpDdsvea]
 -a <apiVersion>    Salesforce API version
 -D <startDate>     oldest date for changes, format: mm/dd/yyyy
 -d                 Debug on
 -e                 Stop on Error
 -f <file>          Salesforce audit trail csv file
 -h                 show help text
 -p <packagefile>   Generate package.xml file
 -s                 Special Debug on
 -v                 Verbose mode
 
 
