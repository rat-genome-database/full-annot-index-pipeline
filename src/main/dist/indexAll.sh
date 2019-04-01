# generate full annot index for all public ontologies
#
APPHOME=/home/rgddata/pipelines/fullAnnotIndex
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAIL_LIST=rgd.developers@mcw.edu
fi

$APPHOME/index.sh "*"

mailx -s "[$SERVER] Output from full annot index pipeline" $EMAIL_LIST < $APPHOME/logs/core.log

