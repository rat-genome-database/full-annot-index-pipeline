package edu.mcw.rgd.dataload;

import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jdepons
 * @since 4/26/12
 * Pipeline job to index full annot table.  The index allows for querying of child terms based on an annotation
 */
public class FullAnnotIndex {

    Logger log = LogManager.getLogger("status");
    FullAnnotIndexDao dao = new FullAnnotIndexDao();

    long rowsInserted = 0;
    long rowsDeleted = 0;
    long rowsMatching = 0;

    long rowsInsertedForAspect = 0;
    long rowsDeletedForAspect = 0;
    long rowsMatchingForAspect = 0;

    private String version;

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Usage: specify at least one aspect code on the command line");
            System.out.println("   f.e. D for disease ontology, W for pathway ontology, * for all public ontologies etc.");
            System.out.println("--fixRogueRows: delete those rows from FULL_ANNOT_INDEX table that violate integrity constraints on the table");
            System.exit(0);
        }

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        FullAnnotIndex manager = (FullAnnotIndex) (bf.getBean("manager"));

        try {
            manager.run(args);

        }catch (Exception e) {
            // print stack trace to error stream
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    void run(String[] args) throws Exception {

        long time0 = System.currentTimeMillis();

        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

        dao.prepareSqlStatements();

        String msg;

        for( String option: args ) {
            option = option.toUpperCase();

            if( option.equals("--FIXROGUEROWS") ) {
                fixRogueKeys();
            } else if( option.equals("*") ) {
                for( String aspect: dao.getAspectsForPublicOntologies() ) {
                    runForAspect(aspect);
                }
            } else {
                runForAspect(option);
            }
        }

        msg = " TOTAL ROWS MATCHING  "+Utils.formatThousands(rowsMatching);
        log.info(msg);
        msg = " TOTAL ROWS INSERTED  "+Utils.formatThousands(rowsInserted);
        log.info(msg);
        msg = " TOTAL ROWS DELETED   "+Utils.formatThousands(rowsDeleted);
        log.info(msg);

        msg = "\n";
        log.info(msg);

        dao.releaseSqlStatements();

        long time1 = System.currentTimeMillis();
        log.info("   finished at "+sdt.format(new Date(time1)));
        log.info(" === TOTAL ELAPSED TIME: "+ Utils.formatElapsedTime(time0, time1));
        log.info("");
    }

    void runForAspect(String aspect) throws Exception {
        long time0 = System.currentTimeMillis();

        String msg = "Indexing for aspect " + aspect + " - " + new Date();
        log.debug(msg);

        rowsInsertedForAspect = 0;
        rowsDeletedForAspect = 0;
        rowsMatchingForAspect = 0;

        buildFullAnnotIndex(aspect);

        rowsInserted += rowsInsertedForAspect;
        rowsDeleted += rowsDeletedForAspect;
        rowsMatching += rowsMatchingForAspect;

        msg = "  aspect " + aspect
                + "  matching=" + Utils.formatThousands(rowsMatchingForAspect)
                + "  inserted=" + Utils.formatThousands(rowsInsertedForAspect)
                + "  deleted=" + Utils.formatThousands(rowsDeletedForAspect);
        log.info(msg +",  "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    void fixRogueKeys() throws Exception {

        log.info("Deleting 'rogue' rows from FULL_ANNOT_INDEX table, if any ...");

        // happens very rarely during data release:
        // when during data release, constraints on FULL_ANNOT_INDEX table could not be enforced
        // because of 'rogue' extra entries in FULL_ANNOT_INDEX table
        int rowsAffected = dao.deleteRogueFullAnnotKeys();

        log.info("Deleted 'rogue' rows from FULL_ANNOT_INDEX table: "+rowsAffected);
    }

    /**
     * SQL method that inserts/updates data in full_annot_index.  Full annot index includes all rows from full
     * annot in addition to a row for each child term
     * @param aspect aspect
     * @throws Exception when problems with execution of sql statements appear
     */
    public void buildFullAnnotIndex(String aspect) throws Exception {

        // read all full_annot_key, term_acc into a map
        final Map<Integer, String> map = dao.getFullAnnotInfo(aspect);

        for( Map.Entry<Integer, String> entry: map.entrySet() ) {

            int fullAnnotKey = entry.getKey();
            String termAcc = entry.getValue();

            // for given full annot key, get term acc and parent term acc
            Collection<String> termAccIdsIncoming = dao.getAllActiveTermAncestorAccIds(termAcc);

            List<String> termAccIdsInRgd = dao.getTermAccIdsForFullAnnotKey(fullAnnotKey);

            // handle matching
            Collection<String> termAccIdsMatching = CollectionUtils.intersection(termAccIdsInRgd, termAccIdsIncoming);
            rowsMatchingForAspect += termAccIdsMatching.size();

            // handle insertions
            Collection<String> termAccIdsToBeInserted = CollectionUtils.subtract(termAccIdsIncoming, termAccIdsInRgd);
            if( !termAccIdsToBeInserted.isEmpty() ) {
                for( String termAccId: termAccIdsToBeInserted ) {
                    dao.insertFullAnnotIndex(fullAnnotKey, termAccId);
                    rowsInsertedForAspect ++;
                }
            }

            // handle deletions
            Collection<String> termAccIdsToBeDeleted = CollectionUtils.subtract(termAccIdsInRgd, termAccIdsIncoming);
            if( !termAccIdsToBeDeleted.isEmpty() ) {
                for( String termAccId: termAccIdsToBeDeleted ) {
                    dao.deleteFullAnnotIndex(fullAnnotKey, termAccId);
                    rowsDeletedForAspect ++;
                }
            }
        }

        // handle stale rows
        int staleRowsDeleted = dao.deleteStaleFullAnnotKeys(aspect);
        if( staleRowsDeleted<0 )
            staleRowsDeleted = -staleRowsDeleted;
        if( staleRowsDeleted!=0 ) {
            log.info("  aspect " + aspect + " stale=" + staleRowsDeleted);
            rowsDeletedForAspect += staleRowsDeleted;
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

