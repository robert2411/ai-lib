package com.agentlibrary.index;

import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.Filter;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Full-text search over artifact metadata using Lucene in-memory index.
 * Rebuilt from scratch on each IndexService refresh.
 */
@Service
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class.getName());
    private static final int MAX_RESULTS = 1000;

    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile IndexSearcher searcher;
    private volatile ByteBuffersDirectory directory;
    private volatile Map<String, ArtifactMetadata> metadataLookup = Map.of();

    /**
     * Rebuilds the Lucene index from the given list of artifacts.
     * Thread-safe: builds outside the lock, then swaps references.
     * Closes old resources (DirectoryReader + Directory) after swap.
     */
    public void rebuild(List<ArtifactMetadata> artifacts) {
        try {
            ByteBuffersDirectory newDirectory = new ByteBuffersDirectory();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            Map<String, ArtifactMetadata> newLookup = new LinkedHashMap<>();

            try (IndexWriter writer = new IndexWriter(newDirectory, config)) {
                for (ArtifactMetadata meta : artifacts) {
                    String id = meta.type().slug() + "/" + meta.name();
                    Document doc = new Document();

                    // Stored ID for retrieval
                    doc.add(new StringField("id", id, Field.Store.YES));

                    // Full-text searchable fields
                    String title = meta.title() != null ? meta.title() : "";
                    String description = meta.description() != null ? meta.description() : "";
                    doc.add(new TextField("title", title, Field.Store.NO));
                    doc.add(new TextField("description", description, Field.Store.NO));
                    doc.add(new TextField("all", title + " " + description, Field.Store.NO));

                    // Filter fields (exact match)
                    doc.add(new StringField("type", meta.type().slug(), Field.Store.YES));

                    for (var harness : meta.harnesses()) {
                        doc.add(new StringField("harness", harness.slug(), Field.Store.YES));
                    }

                    for (String tag : meta.tags()) {
                        doc.add(new StringField("tag", tag, Field.Store.YES));
                    }

                    if (meta.category() != null) {
                        doc.add(new StringField("category", meta.category(), Field.Store.YES));
                    }
                    if (meta.author() != null) {
                        doc.add(new StringField("author", meta.author(), Field.Store.YES));
                    }
                    if (meta.language() != null) {
                        doc.add(new StringField("language", meta.language(), Field.Store.YES));
                    }

                    writer.addDocument(doc);
                    newLookup.put(id, meta);
                }
            }

            // Build new searcher
            DirectoryReader reader = DirectoryReader.open(newDirectory);
            IndexSearcher newSearcher = new IndexSearcher(reader);

            // Capture old resources before swap
            IndexSearcher oldSearcher;
            ByteBuffersDirectory oldDirectory;

            // Swap references under write lock
            lock.writeLock().lock();
            try {
                oldSearcher = this.searcher;
                oldDirectory = this.directory;
                this.searcher = newSearcher;
                this.directory = newDirectory;
                this.metadataLookup = newLookup;
            } finally {
                lock.writeLock().unlock();
            }

            // Close old resources outside lock
            closeOldResources(oldSearcher, oldDirectory);

            LOG.fine("SearchService rebuilt index with " + artifacts.size() + " documents");
        } catch (IOException e) {
            LOG.warning("Failed to rebuild search index: " + e.getMessage());
        }
    }

    /**
     * Closes the DirectoryReader (via the searcher's IndexReader) and Directory.
     */
    private void closeOldResources(IndexSearcher oldSearcher, ByteBuffersDirectory oldDirectory) {
        if (oldSearcher != null) {
            try {
                oldSearcher.getIndexReader().close();
            } catch (IOException e) {
                LOG.warning("Failed to close old IndexReader: " + e.getMessage());
            }
        }
        if (oldDirectory != null) {
            try {
                oldDirectory.close();
            } catch (IOException e) {
                LOG.warning("Failed to close old Directory: " + e.getMessage());
            }
        }
    }

    /**
     * Releases Lucene resources on application shutdown.
     */
    @PreDestroy
    public void close() {
        lock.writeLock().lock();
        try {
            closeOldResources(this.searcher, this.directory);
            this.searcher = null;
            this.directory = null;
            this.metadataLookup = Map.of();
        } finally {
            lock.writeLock().unlock();
        }
        LOG.fine("SearchService closed");
    }

    /**
     * Searches the index with a text query and optional filter.
     * Returns ranked list of matching ArtifactMetadata.
     */
    public List<ArtifactMetadata> search(String query, Filter filter) {
        lock.readLock().lock();
        try {
            if (searcher == null) {
                return List.of();
            }

            Query luceneQuery = buildQuery(query, filter);
            TopDocs topDocs = searcher.search(luceneQuery, MAX_RESULTS);

            List<ArtifactMetadata> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String id = doc.get("id");
                ArtifactMetadata meta = metadataLookup.get(id);
                if (meta != null) {
                    results.add(meta);
                }
            }
            return results;
        } catch (IOException e) {
            LOG.warning("Search failed: " + e.getMessage());
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    private Query buildQuery(String queryText, Filter filter) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasClauses = false;

        // Full-text query
        if (queryText != null && !queryText.isBlank()) {
            try {
                QueryParser parser = new QueryParser("all", analyzer);
                Query parsed = parser.parse(queryText);
                builder.add(parsed, BooleanClause.Occur.MUST);
                hasClauses = true;
            } catch (ParseException e) {
                LOG.warning("Failed to parse query '" + queryText + "': " + e.getMessage());
                // Fall back to term query on "all" field
                builder.add(new TermQuery(new Term("all", queryText.toLowerCase())),
                        BooleanClause.Occur.MUST);
                hasClauses = true;
            }
        }

        // Filter clauses
        if (filter != null) {
            if (filter.type() != null) {
                builder.add(new TermQuery(new Term("type", filter.type().slug())),
                        BooleanClause.Occur.MUST);
                hasClauses = true;
            }
            if (filter.harness() != null) {
                builder.add(new TermQuery(new Term("harness", filter.harness().slug())),
                        BooleanClause.Occur.MUST);
                hasClauses = true;
            }
            if (filter.tag() != null) {
                builder.add(new TermQuery(new Term("tag", filter.tag())),
                        BooleanClause.Occur.MUST);
                hasClauses = true;
            }
        }

        if (!hasClauses) {
            return new MatchAllDocsQuery();
        }

        return builder.build();
    }
}
