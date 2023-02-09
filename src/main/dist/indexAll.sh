# generate full annot index for all public ontologies
#
APPNAME="full-annot-index-pipeline"
APPHOME=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAIL_LIST=rgd.devops@mcw.edu
fi

$APPHOME/index.sh "*" >/dev/null 2>&1

mailx -s "[$SERVER] Full annot index pipeline OK" $EMAIL_LIST < $APPHOME/logs/summary.log

