package com.takin.db.store;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.takin.db.KVConfig;
import com.takin.rpc.server.GuiceDI;

/**
 * 
 *  
 * 
 * @author Administrator
 * @version 1.0
 * @date  2017年5月23日 下午10:28:17
 * @see 
 * @since
 */
@Singleton
public class KVStoreManager {
    private static final Logger logger = LoggerFactory.getLogger(KVStoreManager.class);

    private IndexWriter writer;

    private ScheduledExecutorService scheduler;

    @Inject
    private KVStoreManager() {
        try {
            scheduler = Executors.newScheduledThreadPool(1);
            KVConfig kvconfig = GuiceDI.getInstance(KVConfig.class);
            File indexPath = new File(kvconfig.getLogdirs());

            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_45, new WhitespaceAnalyzer(Version.LUCENE_45));
            config.setOpenMode(OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(64);

            writer = new IndexWriter(FSDirectory.open(indexPath), config);

            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        writer.maybeMerge();
                        logger.info("commit");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 1000, 5000, TimeUnit.MILLISECONDS);

        } catch (IOException e) {
            logger.error("", e);
            System.exit(-1);
        }
    }

    public List<String> get(String key) {
        List<String> values = Lists.newArrayList();
        try {
            IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(writer, true));
            Query query = new TermQuery(new Term("k", key));
            TopDocs docs = indexSearcher.search(query, 10);
            if (docs != null && docs.totalHits > 0) {
                for (int i = 0; i < docs.totalHits; i++) {
                    values.add(indexSearcher.doc(docs.scoreDocs[i].doc).get("v"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return values;
    }

    public boolean insert(String key, String value) {
        try {
            writer.addDocument(getDocument(key, value));
            logger.info(String.format("insert key:%s value:%s", key, value));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Document getDocument(String key, String value) {
        Document doc = new Document();
        doc.add(new KeyField("k", key));
        doc.add(new ValueField("v", value));
        return doc;
    }

    protected static class KeyField extends Field {
        public static final FieldType TYPE = new FieldType();
        static {
            TYPE.setStored(true);
            TYPE.setIndexed(true);
            TYPE.setOmitNorms(true);
            TYPE.setIndexOptions(IndexOptions.DOCS_ONLY);
            TYPE.setTokenized(false);
            TYPE.freeze();
        }

        public KeyField(String name, String key) {
            super(name, TYPE);
            fieldsData = key;
        }
    }

    protected static class ValueField extends Field {
        public static final FieldType TYPE = new FieldType();
        static {
            TYPE.setIndexed(true);
            TYPE.setStored(true);
            // TYPE.setDocValueType(DocValuesType.NUMERIC);
            //            TYPE.setNumericType(NumericType.INT);// 需要支持范围查询，NumbericType会自动建Trie结构
            TYPE.setOmitNorms(true);
            TYPE.setIndexOptions(IndexOptions.DOCS_ONLY);
            TYPE.setTokenized(false);
            TYPE.freeze();
        }

        public ValueField(String name, String value) {
            super(name, TYPE);
            fieldsData = value;
        }
    }

}
