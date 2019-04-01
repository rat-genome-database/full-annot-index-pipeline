#!/usr/bin/env bash
# shell script to run fullAnnotIndex pipeline
. /etc/profile

APPNAME=fullAnnotIndex
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@" | tee run.log 2>&1
