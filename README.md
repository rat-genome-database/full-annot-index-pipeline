# full-annot-index-pipeline
Updates FULL_ANNOT_INDEX table: big auxiliary table used to speed up many annotation queries used by RGD tools.



NOTE on ICREMENTAL UPDATES with VERSIONING: Oct 6-9 2014
   - implemented incremental updates by adding a versioning column into FULL_ANNOT_INDEX table
   - overall, the load time increased twice in addition to extra space needed to hold versioning data
   - therefore, we abandoned that feature
