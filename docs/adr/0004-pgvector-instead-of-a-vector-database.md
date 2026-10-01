# ADR 0004 — pgvector instead of a dedicated vector database

- Status: accepted
- Date: 2026-10-01

## Context

The copilot answers policy questions from tenant documents (RAG) and sourcing ranks workers by embedding similarity. The corpus is
small (tens to thousands of chunks per tenant), and tenant isolation of retrieval is a hard requirement.

## Decision

Store embeddings in PostgreSQL with pgvector (`vector(1536)`, HNSW index with cosine distance), next to the business data.

## Why

- **Isolation in one statement**: `WHERE tenant_id = ? ORDER BY embedding <=> ? LIMIT k` — no cross-system filtering to get wrong.
  pgvector 0.8's iterative index scans keep the filter from starving the HNSW index.
- One database to run, back up, secure and test (Testcontainers with `pgvector/pgvector:pg16`); Neon's free tier supports it.
- Transactions: documents, chunks and embeddings are written atomically; the embedding cache is a plain table keyed by content hash.

## Alternatives considered

- Pinecone / Weaviate / Qdrant: better at very large scale and with richer hybrid search, but another service, another bill and
  another place where tenant filters must be enforced.

## Consequences

- At very large scale (millions of chunks per tenant) we would revisit partitioning by tenant or a dedicated engine.
- Hybrid search (BM25 + vectors) is not implemented; `tsvector` would be the natural next step.
