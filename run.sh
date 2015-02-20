#!/bin/sh
set -x

case $# in
	0) echo "usage: run.sh <date> <audit file>";
	   echo "    Date:  mm/dd/yyyy";exit 1;
esac
	   
groovy AuditTrail.groovy -D $1 -e -f $2 \
	   -i Activated,Deactivated,Logged,Password,Requested,Granted,For,Email,Feed,Organization *$

