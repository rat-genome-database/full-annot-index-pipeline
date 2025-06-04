package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.spring.StringListQuery;
import edu.mcw.rgd.datamodel.ontologyx.Ontology;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;
import org.springframework.jdbc.object.MappingSqlQuery;
import org.springframework.jdbc.object.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;
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
    private SqlUpdate suDelete;

    public String getConnectionInfo() {
        return dao.getConnectionInfo();
    }

    public Map<Integer, String> getFullAnnotInfo(String aspect) throws Exception {
        // read all full_annot_key, term_acc into a map
        final Map<Integer, String> map = new HashMap<Integer, String>();
        String query = "SELECT full_annot_key,term_acc FROM full_annot WHERE aspect=?";
        MappingSqlQuery q = new MappingSqlQuery(dao.getDataSource(), query) {
            @Override
            protected Object mapRow(ResultSet rs, int i) throws SQLException {
                int fullAnnotKey = rs.getInt("full_annot_key");
                String termAcc = rs.getString("term_acc");
                map.put(fullAnnotKey, termAcc);
                return null;
            }
        };
        q.declareParameter(new SqlParameter(Types.VARCHAR));
        q.compile();
        q.execute(aspect);
        return map;
    }


    public void prepareSqlStatements() throws Exception {
        suInsert = new BatchSqlUpdate(dao.getDataSource(),
                "INSERT /*+ APPEND */ INTO full_annot_index (full_annot_key, term_acc) VALUES(?,?)",
                new int[]{Types.INTEGER, Types.VARCHAR},
                100);
        suInsert.compile();

        suDelete = new SqlUpdate(dao.getDataSource(),
                "DELETE FROM full_annot_index WHERE full_annot_key=? AND term_acc=?");
        suDelete.declareParameter(new SqlParameter(Types.INTEGER));
        suDelete.declareParameter(new SqlParameter(Types.VARCHAR));
        suDelete.compile();
    }

    public void releaseSqlStatements() throws Exception {
        dao.executeBatch(suInsert); // release resources used by batched queries
    }


    public void deleteFullAnnotIndex(int fullAnnotKey, String termAcc) throws Exception {
        suDelete.update(fullAnnotKey, termAcc);
        logDeleted.debug("FAK="+fullAnnotKey+" "+termAcc);
    }

    public void insertFullAnnotIndex(int fullAnnotKey, String termAcc) throws Exception {
        suInsert.update(fullAnnotKey, termAcc);
        logInserted.debug("FAK="+fullAnnotKey+" "+termAcc);
    }

    public List<String> getTermAccIdsForFullAnnotKey(int fullAnnotKey) throws Exception {

        final String query = "SELECT term_acc FROM full_annot_index WHERE full_annot_key=?";
        return StringListQuery.execute(dao, query, fullAnnotKey);
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
        int pos = rootTermAcc.indexOf(':');
        if( pos<0 )
            throw new Exception("Invalid ROOT_TERM_ACC for ontology "+ont.getId());
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
