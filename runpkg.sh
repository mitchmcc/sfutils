#!/bin/sh

case $# in
	2) ;;
	*) echo "usage: run.sh <date> <audit file>";
	   echo "    Date:  mm/dd/yyyy";exit 1;
esac
	   
groovy AuditTrail.groovy -D 02/16/2015 -f SetupAuditTrailQA2_02162015.csv \
	   -i Activated,Deactivated,Logged,Password,Requested,Granted,For,Email,Feed,Organization -p package.xml

