# remove from FULL_ANNOT_INDEX table 'rogue' rows:
#   the rows that violate constraints for FULL_ANNOT_INDEX table
#
# Note: if you want to run this script of a prod database,
#    point to correct config file, f.e. lomu.xml or eales.xml
#
APPHOME=/home/rgddata/pipelines/fullAnnotIndex

$APPHOME/_run.sh --fixRogueRows
