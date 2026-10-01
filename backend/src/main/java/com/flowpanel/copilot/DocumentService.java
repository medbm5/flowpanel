package com.flowpanel.copilot;

import com.flowpanel.ai.EmbeddingService;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.ConflictException;
import com.flowpanel.invoice.InvoicePdf;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant documents: ingestion (extract → chunk → embed) and tenant-scoped vector retrieval. */
@Service
@Transactional
public class DocumentService {

    public static final String EMBEDDING_FEATURE = "copilot.embedding";

    public record DocumentView(Long id, String title, String source, String contentType, int chunks, Instant createdAt) {
    }

    /** A retrieved passage; {@code score} is the cosine similarity. */
    public record Passage(Long chunkId, Long documentId, String documentTitle, String heading, String content, double score) {
    }

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    private final RequestContext context;
    private final AuditService audit;

    public DocumentService(JdbcTemplate jdbc, EmbeddingService embeddings, RequestContext context, AuditService audit) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.context = context;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list() {
        Long tenantId = context.requireTenantId();
        return jdbc.query("""
                select d.id, d.title, d.source, d.content_type, d.created_at, count(c.id) as chunks
                from document d left join document_chunk c on c.document_id = d.id
                where d.tenant_id = ? group by d.id order by d.id
                """, (rs, i) -> new DocumentView(rs.getLong("id"), rs.getString("title"), rs.getString("source"),
                rs.getString("content_type"), rs.getInt("chunks"), rs.getTimestamp("created_at").toInstant()), tenantId);
    }

    /** Upload of a PDF or Markdown / plain-text file by a buyer. */
    public DocumentView upload(String filename, String contentType, byte[] bytes, String title) {
        Long tenantId = context.requireTenantId();
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("The file is empty");
        }
        String name = filename == null ? "document" : filename;
        boolean pdf = name.toLowerCase(Locale.ROOT).endsWith(".pdf") || "application/pdf".equals(contentType);
        boolean text = name.toLowerCase(Locale.ROOT).matches(".*\\.(md|markdown|txt)$")
                || (contentType != null && contentType.startsWith("text/"));
        if (!pdf && !text) {
            throw new BadRequestException("Only PDF and Markdown/text files are supported");
        }
        String content = pdf ? InvoicePdf.text(bytes) : new String(bytes, StandardCharsets.UTF_8);
        String docTitle = title != null && !title.isBlank() ? title.strip() : titleOf(content, name);
        long id = ingest(tenantId, docTitle, "UPLOAD", pdf ? "application/pdf" : "text/markdown", content);
        audit.human(null, "document.uploaded", "Uploaded document " + docTitle, Map.of("documentId", id));
        return list().stream().filter(d -> d.id() == id).findFirst().orElseThrow();
    }

    /** Extracts, chunks (headings first, ~500 tokens, ~60 overlap) and embeds; identical content is not ingested twice. */
    public long ingest(Long tenantId, String title, String source, String contentType, String content) {
        String hash = sha256(content);
        List<Long> existing = jdbc.queryForList("select id from document where tenant_id = ? and content_hash = ?", Long.class,
                tenantId, hash);
        if (!existing.isEmpty()) {
            if ("UPLOAD".equals(source)) {
                throw new ConflictException("This document is already in your library");
            }
            return existing.get(0);
        }
        Long id = jdbc.queryForObject("insert into document (tenant_id, title, source, content_type, content, content_hash) "
                + "values (?, ?, ?, ?, ?, ?) returning id", Long.class, tenantId, title, source, contentType, content, hash);
        List<Chunker.Chunk> chunks = Chunker.chunk(content);
        List<float[]> vectors = embeddings.embedAll(EMBEDDING_FEATURE, chunks.stream().map(c -> title + "\n" + c.content()).toList());
        String model = embeddings.model();
        for (int i = 0; i < chunks.size(); i++) {
            Chunker.Chunk c = chunks.get(i);
            jdbc.update("insert into document_chunk (document_id, tenant_id, chunk_index, heading, content, tokens, model, embedding) "
                    + "values (?, ?, ?, ?, ?, ?, ?, cast(? as vector))", id, tenantId, c.index(), c.heading(), c.content(), c.tokens(),
                    model, EmbeddingService.literal(vectors.get(i)));
        }
        return id;
    }

    /**
     * Vector search restricted to one tenant in the same SQL statement (the tenant filter is never applied after
     * the fact in Java), and to chunks embedded with the current model.
     */
    @Transactional(readOnly = true)
    public List<Passage> search(Long tenantId, String query, int limit) {
        if (tenantId == null || query == null || query.isBlank()) {
            return List.of();
        }
        String vector = EmbeddingService.literal(embeddings.embed(EMBEDDING_FEATURE, query));
        // pgvector 0.8: keep scanning the HNSW index until enough rows pass the tenant filter.
        jdbc.execute("set local hnsw.iterative_scan = relaxed_order");
        return jdbc.query("""
                select c.id, c.document_id, d.title, c.heading, c.content, 1 - (c.embedding <=> cast(? as vector)) as score
                from document_chunk c join document d on d.id = c.document_id
                where c.tenant_id = ? and d.tenant_id = ? and c.model = ?
                order by c.embedding <=> cast(? as vector)
                limit ?
                """, (rs, i) -> new Passage(rs.getLong("id"), rs.getLong("document_id"), rs.getString("title"),
                rs.getString("heading"), rs.getString("content"), rs.getDouble("score")),
                vector, tenantId, tenantId, embeddings.model(), vector, limit);
    }

    /**
     * Re-embeds chunks stored with another embedding model (e.g. after switching AI_PROFILE from mock to live), so
     * retrieval keeps working. Runs per tenant, in that tenant's scope (for ai_call attribution).
     */
    public int reembedStaleChunks(Long tenantId) {
        record Stale(Long id, String title, String content) {
        }
        List<Stale> stale = jdbc.query("""
                select c.id, d.title, c.content from document_chunk c join document d on d.id = c.document_id
                where c.tenant_id = ? and c.model <> ? order by c.id
                """, (rs, i) -> new Stale(rs.getLong(1), rs.getString(2), rs.getString(3)), tenantId, embeddings.model());
        if (stale.isEmpty()) {
            return 0;
        }
        List<float[]> vectors = embeddings.embedAll(EMBEDDING_FEATURE, stale.stream().map(c -> c.title() + "\n" + c.content()).toList());
        for (int i = 0; i < stale.size(); i++) {
            jdbc.update("update document_chunk set embedding = cast(? as vector), model = ? where id = ?",
                    EmbeddingService.literal(vectors.get(i)), embeddings.model(), stale.get(i).id());
        }
        return stale.size();
    }

    @Transactional(readOnly = true)
    public int count(Long tenantId) {
        return jdbc.queryForObject("select count(*) from document where tenant_id = ?", Integer.class, tenantId);
    }

    private static String titleOf(String content, String filename) {
        return content.lines().filter(l -> l.startsWith("# ")).map(l -> l.substring(2).strip()).findFirst()
                .orElse(filename.replaceAll("\\.[A-Za-z]+$", ""));
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
