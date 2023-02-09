# run fullAnnotIndex pipeline with commandline parameters
#    ("$@" passes all cmdline parameters to pipeline program)
# allowed parameters:  D W N B C F P E H S V "*"
# you can specify one or multiple parameters

APPNAME="full-annot-index-pipeline"
APPHOME=/home/rgddata/pipelines/$APPNAME

$APPHOME/_run.sh "$@"
