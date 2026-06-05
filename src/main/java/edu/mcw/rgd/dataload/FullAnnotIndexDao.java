package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.datamodel.ontologyx.Ontology;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.object.BatchSqlUpdate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

/**
 * @author mtutaj
 * @since 2/6/15
 * encapsulates all dao code
 */
public class FullAnnotIndexDao {

    private Logger logInserted = LogManager.getLogger("inserted");
    private Logger logDeleted = LogManager.getLogger("deleted");
    private OntologyXDAO dao = new OntologyXDAO();
    private BatchSqlUpdate suInsert;
    private BatchSqlUpdate suDelete;

    public String getConnectionInfo() {
        return dao.getConnectionInfo();
    }

    public void prepareSqlStatements() throws Exception {
        suInsert = new BatchSqlUpdate(dao.getDataSource(),
                "INSERT INTO full_annot_index (full_annot_key, term_acc) VALUES(?,?)",
                new int[]{Types.INTEGER, Types.VARCHAR},
                100);
        suInsert.compile();

        suDelete = new BatchSqlUpdate(dao.getDataSource(),
                "DELETE FROM full_annot_index WHERE full_annot_key=? AND term_acc=?",
                new int[]{Types.INTEGER, Types.VARCHAR},
                100);
        suDelete.compile();
    }

    public void releaseSqlStatements() throws Exception {
        // flush any remaining batched inserts and deletes
        dao.executeBatch(suInsert);
        dao.executeBatch(suDelete);
    }


    public void deleteFullAnnotIndex(int fullAnnotKey, String termAcc) throws Exception {
        suDelete.update(fullAnnotKey, termAcc);
        logDeleted.debug("FAK="+fullAnnotKey+" "+termAcc);
    }

    public void insertFullAnnotIndex(int fullAnnotKey, String termAcc) throws Exception {
        suInsert.update(fullAnnotKey, termAcc);
        logInserted.debug("FAK="+fullAnnotKey+" "+termAcc);
    }

    /**
     * Stream FULL_ANNOT rows for the given aspect joined with their existing FULL_ANNOT_INDEX rows,
     * ordered by full_annot_key, and invoke the processor once per fak with the set of term_accs
     * currently in the index. Replaces the N+1 SELECT pattern from the previous build loop with a
     * single streaming query.
     */
    public void streamFullAnnotIndex(String aspect, FullAnnotIndexProcessor processor) throws Exception {

        String sql = """
            SELECT fa.full_annot_key, fa.term_acc fa_term_acc, fai.term_acc fai_term_acc
              FROM full_annot fa
              LEFT JOIN full_annot_index fai ON fa.full_annot_key = fai.full_annot_key
             WHERE fa.aspect = ?
             ORDER BY fa.full_annot_key
            """;

        try (Connection conn = dao.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            ps.setFetchSize(10000);
            ps.setString(1, aspect);

            try (ResultSet rs = ps.executeQuery()) {
                int currentFak = -1;
                String currentTermAcc = null;
                Set<String> existingAccs = new HashSet<>();

                while (rs.next()) {
                    int fak = rs.getInt("full_annot_key");
                    String faTermAcc = rs.getString("fa_term_acc");
                    String faiTermAcc = rs.getString("fai_term_acc");

                    if (fak != currentFak) {
                        if (currentFak != -1) {
                            processor.process(currentFak, currentTermAcc, existingAccs);
                        }
                        currentFak = fak;
                        currentTermAcc = faTermAcc;
                        existingAccs = new HashSet<>();
                    }
                    if (faiTermAcc != null) {
                        existingAccs.add(faiTermAcc);
                    }
                }

                if (currentFak != -1) {
                    processor.process(currentFak, currentTermAcc, existingAccs);
                }
            }
        }
    }

    @FunctionalInterface
    public interface FullAnnotIndexProcessor {
        void process(int fullAnnotKey, String termAcc, Set<String> existingTermAccs) throws Exception;
    }

    public Collection<String> getAllActiveTermAncestorAccIds(String termAcc) throws Exception {
        Collection<String> ancestorTerms = _cacheAncestorTerms.get(termAcc);
        if( ancestorTerms==null ) {
            ancestorTerms = new HashSet<>(dao.getAllActiveTermAncestorAccIds(termAcc));
            ancestorTerms.add(termAcc);

            _cacheAncestorTerms.put(termAcc, ancestorTerms);
        }
        return ancestorTerms;
    }
    Map<String, Collection<String>> _cacheAncestorTerms = new HashMap<>();


    public int deleteStaleFullAnnotKeys(String aspect) throws Exception {

        // get prefix for aspect
        // f.e. aspect 'E' has prefix 'CHEBI:'
        Ontology ont = dao.getOntologyFromAspect(aspect);
        if( ont==null )
            return 0;
        String rootTermAcc  = dao.getRootTerm(ont.getId());
        if( rootTermAcc==null )
            throw new Exception("No ROOT_TERM_ACC for ontology "+ont.getId());
        int pos = rootTermAcc.indexOf(':');
        if( pos<0 )
            throw new Exception("Invalid ROOT_TERM_ACC '"+rootTermAcc+"' for ontology "+ont.getId());
        String prefix = rootTermAcc.substring(0, pos+1)+"%";

        String sql = """
            DELETE FROM full_annot_index WHERE full_annot_key IN(
                SELECT full_annot_key FROM full_annot WHERE aspect=?
                MINUS
                SELECT full_annot_key FROM full_annot_index WHERE term_acc LIKE ?
            )
            """;
        return dao.update(sql, aspect, prefix);
    }

    public int deleteRogueFullAnnotKeys() throws Exception {

        String sql = """
            DELETE FROM full_annot_index WHERE full_annot_key IN(
                SELECT full_annot_key FROM full_annot_index
                MINUS
                SELECT full_annot_key FROM full_annot
            )
            """;
        return dao.update(sql);
    }

    public List<String> getAspectsForPublicOntologies() throws Exception {
        List<String> aspects = new ArrayList<>();
        for( Ontology o: dao.getPublicOntologies() ) {
            aspects.add(o.getAspect());
        }
        Collections.shuffle(aspects);
        return aspects;
    }
}
