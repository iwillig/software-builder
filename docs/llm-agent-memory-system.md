# LLM Agent Memory System

This document outlines the memory architecture for an LLM coding agent that provides persistent, unlimited memory.

## Overview

Traditional LLMs operate statelessly - each interaction exists in isolation. This memory system enables long-term retention, dynamic organization, and selective retrieval to support complex, multi-session coding tasks.

## Memory Hierarchy

The system implements a three-tier memory architecture:

| Memory Type | Duration | Purpose | Implementation |
|------------|----------|---------|----------------|
| **Working Memory** | Immediate | Current conversation context | In-context window |
| **Short-term Memory** | Hours to days | Recent session history | Vector cache |
| **Long-term Memory** | Permanent | All historical interactions | Vector database |

### Memory Types Explained

#### Working Memory
- The LLM's active context window (8K-128K tokens)
- Contains raw, uncompressed recent interactions
- Directly accessible for reasoning

#### Short-term Memory
- Session-scoped working memory
- Fast retrieval for recent turns
- May include session summaries

#### Long-term Memory
- Persistent across all sessions
- Semantic indexing for relevance-based retrieval
- Compression and consolidation over time

## Core Operations

### Store
Persist new information to long-term memory:

1. **Raw Storage**: Encode interactions as embeddings
2. **Extraction**: Extract facts, preferences, entities
3. **Linking**: Connect related memories bidirectionally

### Retrieve
Find relevant historical context:

1. **Semantic Search**: Query embedding -> similar memories
2. **Recency Filter**: Prioritize recent interactions
3. **Importance Scoring**: Weight critical facts higher
4. **Relationship Traversal**: Follow linked memories

### Update
Refine existing memories:

- Add corrections without losing history
- Version memories for audit trails
- Merge duplicate or redundant entries

### Consolidate
Manage memory growth over time:

- **Summarization**: Compress old conversations
- **Recursive Compression**: Summarize summaries
- **Pruning**: Discard low-importance memories
- **Merging**: Combine related memories

## Architecture Patterns

### Vector Database + RAG

The primary retrieval pattern:

```
User Query
    |
    v
[Embed Query] ----> [Vector Database]
    |                    |
    |<--------------[Semantic Search (k-NN)]
    v
[Re-rank by relevance/recency/importance]
    |
    v
[Inject Top-K Memories + Current Query]
    |
    v
[LLM Generates Response]
    |
    v
[Store Response + Extract Facts]
```

**Key Components:**
- **Embedding Model**: Converts text to dense vectors (text-embedding-3, sentence-transformers)
- **Vector Store**: Persistent database with similarity indexing (Chroma, Weaviate, Pinecone)
- **Retrieval Pipeline**: Query -> Embedding -> Search -> Re-ranking -> Context injection

### Hierarchical Memory Organization

Address context window limitations:

```
Working Memory (Recent, Raw)
       |
       v
Short-term Memory (Session Summaries)
       |
       v
Long-term Memory (Episodic Facts)
```

When working memory exceeds limits:
1. Push oldest turns to short-term
2. Summarize when short-term grows
3. Extract key facts to long-term

### Proactive Memory Extraction

Instead of storing raw conversations:

1. **Entity Recognition**: Identify people, projects, decisions
2. **Fact Extraction**: "User prefers Clojure over Python"
3. **Preference Learning**: Track style choices, constraints
4. **Link Creation**: Connect related concepts

## Memory Schemas

### Memory Entity

```clojure
{:memory/id              ; Unique identifier
 :memory/type           ; :fact, :interaction, :summary, :preference
 :memory/content        ; Raw text or structured data
 :metadata/timestamp    ; Creation time
 :metadata/session-id   ; Associated session
 :metadata/importance   ; 0.0-1.0 score
 :metadata/embedding    ; Vector representation
 :relations/related-to  ; References to other memories
 :relations/source      ; Origin (user, system, extraction)}
```

### Interaction Memory

```clojure
{:interaction/id
 :interaction/session-id
 :interaction/role      ; :user, :assistant, :system
 :interaction/content   ; Message text
 :interaction/timestamp
 :interaction/embedding ; For semantic retrieval
 :interaction/sequence  ; Order within session}
```

### Episodic Memory

```clojure
{:episode/id
 :episode/title         ; Human-readable summary
 :episode/start-time
 :episode/end-time
 :episode/summary       ; Compressed description
 :episode/key-facts     ; List of extracted facts
 :episode/session-ids   ; Related sessions
 :episode/project-id    ; Scope}
```

## Retrieval Strategies

### Multi-Factor Ranking

Combine multiple signals for relevance:

```
Score = (semantic-similarity * 0.5) +
        (recency-decay * 0.3) +
        (importance * 0.2)
```

### Hybrid Retrieval

Mix complementary approaches:

1. **Vector Search**: Semantic relevance
2. **Keyword Search**: Exact matches
3. **Temporal**: Recent interactions
4. **Graph Traversal**: Related memories

### Context Assembly

When building context window:

1. Always include recent N turns (working memory)
2. Add top-K semantically relevant from long-term
3. Include session summary for continuity
4. Inject procedural/tool information

## Implementation Considerations

### Vector Database Selection

| Option | Best For | Notes |
|--------|----------|-------|
| **Chroma** | Local/dev | Embeddable, simple API |
| **Weaviate** | Hybrid search | Native GraphQL, multi-modal |
| **Pinecone** | Production | Managed, high performance |
| **pgvector** | PostgreSQL users | SQL interface, good start |

### Embedding Models

- **OpenAI text-embedding-3**: High quality, vendor lock-in
- **sentence-transformers**: Local, open source
- **Multi-modal**: For code + text + images

### Compression Techniques

1. **Summarization**: LLM-generated summaries
2. **Entity Extraction**: Structured fact storage
3. **Hierarchical**: Recursive consolidation
4. **Deduplication**: Merge similar memories

## Never Forget Guarantees

### Persistence Strategy

- All interactions stored before LLM response
- Asynchronous extraction to prevent latency
- Write-ahead logging for crash safety
- Backup and export capabilities

### Importance Scoring

Use LLM to classify storage priority:

- **Critical**: User preferences, key decisions, project scope
- **Important**: Code reviewed, bugs encountered, solutions found
- **Routine**: General chat, thinking out loud
- **Transient**: Temporary files, debug output

### Memory Validation

Proactive verification of stored memories:

1. **Self-questioning**: Generate questions, verify answers
2. **Conflict Detection**: Identify contradictory memories
3. **Recency Updates**: Refresh importance of old facts
4. **User Feedback**: Explicit confirmation/correction

## Integration with Coding Agent

### Session Context Loading

When resuming a session:

1. Retrieve session summary
2. Find related past sessions
3. Load project-specific memories
4. Include user preferences

### Code-Specific Memories

Track coding-specific context:

- File patterns and conventions
- Project architecture decisions
- Bug patterns and resolutions
- Dependency versions and issues
- Build/test patterns

### Tool Memory

Remember tool interactions:

- Previous command outputs
- File states across sessions
- Tool preferences and aliases
- Integration quirks

## References

- [A-Mem: Agentic Memory for LLM Agents (arXiv)](https://arxiv.org/html/2502.12110v1)
- [Letta: Agent Memory](https://www.letta.com/blog/agent-memory)
- [SimpleMem: Efficient Lifelong Memory](https://arxiv.org/html/2601.02553v1)
- [MemGPT: Operating System Paradigm](https://memgpt.ai)
- [LangMem: Long-term Memory](https://langchain-ai.github.io/langmem/)
