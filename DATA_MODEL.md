# Software Builder - Data Model Design

## Overview

This data model supports:
1. **Coding Agent**: Sessions, messages (OpenAI format), tool calls, documents
2. **Prompt Versioning**: System prompts and tool prompts with version tracking
3. **Evaluation System**: Traces, annotations, datasets for continuous improvement

**Design Principles**:
- Local-first (no user management)
- OpenAI message format compatibility
- Full trace capture for evaluation
- Version control for prompts
- Binary pass/fail evaluations (not rating scales)

---

## Entity Relationship Diagram

```
┌──────────────┐
│ SystemPrompt │ (versioned system prompts)
│──────────────│
│ id           │
│ version      │◄────┐
│ name         │     │
│ content      │     │
│ created_at   │     │
│ is_active    │     │
└──────────────┘     │
                     │
┌──────────────┐     │
│  ToolPrompt  │     │ (versioned tool definitions)
│──────────────│     │
│ id           │◄────┼────┐
│ version      │     │    │
│ name         │     │    │
│ tools_json   │     │    │  (JSON array of tool definitions)
│ created_at   │     │    │
│ is_active    │     │    │
└──────────────┘     │    │
                     │    │
┌──────────────┐     │    │
│   Session    │─────┘    │ (coding conversation)
│──────────────│          │
│ id           │          │
│ title        │          │
│ system_prompt_id │      │
│ tool_prompt_id   │──────┘
│ created_at   │
│ updated_at   │
│ status       │ (active, completed, failed)
└──────┬───────┘
       │
       │ 1:N
       ▼
┌──────────────┐
│   Message    │ (OpenAI format messages)
│──────────────│
│ id           │
│ session_id   │◄── FK to Session
│ role         │ (system, user, assistant, tool)
│ content      │ (text content or null for tool calls)
│ name         │ (optional: function name for tool role)
│ tool_calls   │ (JSON: array of tool call objects)
│ tool_call_id │ (for tool response messages)
│ sequence     │ (ordering within session)
│ created_at   │
└──────┬───────┘
       │
       │ 1:N
       ▼
┌──────────────┐
│   ToolCall   │ (individual tool invocations)
│──────────────│
│ id           │
│ message_id   │◄── FK to Message
│ tool_call_id │ (OpenAI tool_call_id)
│ name         │ (tool/function name)
│ arguments    │ (JSON object)
│ result       │ (tool execution result)
│ status       │ (pending, success, failed)
│ error        │ (error message if failed)
│ created_at   │
│ completed_at │
└──────────────┘

┌──────────────┐
│   Document   │ (stored documents for context)
│──────────────│
│ id           │
│ name         │
│ path         │ (file path or identifier)
│ content      │ (full text content)
│ content_type │ (markdown, python, text, etc.)
│ size_bytes   │
│ checksum     │ (SHA256 for deduplication)
│ metadata     │ (JSON: language, tags, etc.)
│ created_at   │
│ updated_at   │
└──────┬───────┘
       │
       │ N:M
       ▼
┌──────────────┐
│MessageDocument│ (junction table)
│──────────────│
│ message_id   │◄── FK to Message
│ document_id  │◄── FK to Document
│ relevance    │ (optional: 0.0-1.0 relevance score)
└──────────────┘

═══════════════════════════════════════════════════════════
              EVALUATION SYSTEM
═══════════════════════════════════════════════════════════

┌──────────────┐
│    Trace     │ (complete execution capture)
│──────────────│
│ id           │
│ session_id   │◄── FK to Session (1:1)
│ prompt_version │ (snapshot of system_prompt version)
│ tool_version │ (snapshot of tool_prompt version)
│ input        │ (initial user request)
│ output       │ (final agent response)
│ status       │ (success, failed, timeout)
│ error        │ (error message if failed)
│ total_tokens │
│ total_cost   │
│ duration_ms  │
│ metadata     │ (JSON: model, temp, etc.)
│ created_at   │
└──────┬───────┘
       │
       │ 1:N
       ▼
┌──────────────┐
│  Evaluation  │ (manual annotations)
│──────────────│
│ id           │
│ trace_id     │◄── FK to Trace
│ judgment     │ (pass, fail)
│ notes        │ (why it failed, issues found)
│ error_type   │ (hallucination, syntax, logic, etc.)
│ reviewed_by  │ (optional: who reviewed)
│ created_at   │
└──────────────┘

┌──────────────┐
│   Dataset    │ (eval test suites)
│──────────────│
│ id           │
│ name         │ (e.g., "agent_errors", "edge_cases")
│ description  │
│ version      │
│ created_at   │
│ updated_at   │
└──────┬───────┘
       │
       │ 1:N
       ▼
┌──────────────┐
│ DatasetCase  │ (individual test cases)
│──────────────│
│ id           │
│ dataset_id   │◄── FK to Dataset
│ trace_id     │◄── FK to Trace (optional: origin)
│ input        │ (test input)
│ expected     │ (expected behavior/output)
│ context      │ (JSON: files, env, etc.)
│ tags         │ (JSON array: categories)
│ created_at   │
└──────────────┘
```

---

## Design Decisions

### 1. **OpenAI Message Format Compatibility**

The `Message` model follows OpenAI's message structure:

```python
# User message
{
    "role": "user",
    "content": "Create a hello world function"
}

# Assistant message with tool call
{
    "role": "assistant",
    "content": null,
    "tool_calls": [
        {
            "id": "call_abc123",
            "type": "function",
            "function": {
                "name": "write_file",
                "arguments": "{\"path\": \"hello.py\", \"content\": \"...\"}"
            }
        }
    ]
}

# Tool response message
{
    "role": "tool",
    "tool_call_id": "call_abc123",
    "content": "File written successfully"
}
```

**Database Mapping**:
- `role`: Stored as string (system, user, assistant, tool)
- `content`: Text or null
- `tool_calls`: Stored as JSON
- `tool_call_id`: For tool response messages

### 2. **Tool Call Tracking**

Separate `ToolCall` table for detailed tracking:
- Links to the message that initiated it
- Stores arguments, results, status, timing
- Enables tool usage analytics
- Supports retry logic

### 3. **Prompt Versioning**

**Why separate tables for SystemPrompt and ToolPrompt?**
- Independent version control
- System prompt can change without affecting tool definitions
- Easy rollback to previous versions
- Track which prompt versions produced which results

**is_active flag**: Quick lookup of current active version

### 4. **Document Storage**

**Design choices**:
- Store full content (not just paths) for portability
- Checksum for deduplication
- Metadata as JSON for flexibility (language, tags, source, etc.)
- N:M relationship with messages (same doc can be referenced multiple times)

**Why not just store paths?**
- Documents might be generated/modified by agent
- Full content enables embedding generation
- Portable across systems

### 5. **Trace = Session Snapshot**

**Key insight**: A Trace captures the complete execution for evaluation:
- Links to Session (1:1)
- Snapshots prompt versions (prevents version drift)
- Stores input/output/metadata for analysis
- Immutable record for reproducibility

### 6. **Binary Evaluations**

Following Hamel Husain's best practices:
- Simple pass/fail (not 1-5 stars)
- Notes field for context
- Error type categorization
- Who reviewed (optional, for team settings)

### 7. **Datasets from Failures**

**Workflow**:
1. Agent runs, creates Trace
2. Human reviews, marks as "fail" in Evaluation
3. Failed trace converted to DatasetCase
4. pytest runs tests against dataset
5. Prevents regression

---

## Example Queries

### Get Session with Full Message History
```python
session = db.query(Session).filter_by(id=session_id).first()
messages = db.query(Message).filter_by(session_id=session_id).order_by(Message.sequence).all()
```

### Find All Failed Traces for a Prompt Version
```python
failed_traces = (
    db.query(Trace)
    .join(Evaluation)
    .filter(
        Trace.prompt_version == "v3",
        Evaluation.judgment == "fail"
    )
    .all()
)
```

### Get Tool Usage Statistics
```python
from sqlalchemy import func

tool_stats = (
    db.query(
        ToolCall.name,
        func.count(ToolCall.id).label("count"),
        func.avg(ToolCall.completed_at - ToolCall.created_at).label("avg_duration")
    )
    .filter(ToolCall.status == "success")
    .group_by(ToolCall.name)
    .all()
)
```

### Export Dataset
```python
dataset = db.query(Dataset).filter_by(name="agent_errors").first()
cases = db.query(DatasetCase).filter_by(dataset_id=dataset.id).all()

# Convert to JSON for version control
export = {
    "name": dataset.name,
    "version": dataset.version,
    "cases": [
        {
            "input": case.input,
            "expected": case.expected,
            "context": case.context,
            "tags": case.tags
        }
        for case in cases
    ]
}
```

---

## JSON Fields Schema

### Message.tool_calls
```json
[
    {
        "id": "call_abc123",
        "type": "function",
        "function": {
            "name": "write_file",
            "arguments": "{\"path\": \"hello.py\", \"content\": \"print('hello')\"}"
        }
    }
]
```

### ToolPrompt.tools_json
```json
[
    {
        "type": "function",
        "function": {
            "name": "write_file",
            "description": "Write content to a file",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {"type": "string", "description": "File path"},
                    "content": {"type": "string", "description": "File content"}
                },
                "required": ["path", "content"]
            }
        }
    }
]
```

### Document.metadata
```json
{
    "language": "python",
    "tags": ["utils", "helpers"],
    "source": "imported",
    "line_count": 145
}
```

### Trace.metadata
```json
{
    "model": "gpt-4o-mini",
    "temperature": 0.7,
    "max_tokens": 2048,
    "provider": "openai",
    "stream": true
}
```

### DatasetCase.context
```json
{
    "files": ["main.py", "utils.py"],
    "environment": {"python_version": "3.11"},
    "requirements": ["flask>=3.0.0"]
}
```

---

## Indexes for Performance

```python
# Message lookups by session
Index('idx_message_session_sequence', Message.session_id, Message.sequence)

# Tool call lookups
Index('idx_toolcall_message', ToolCall.message_id)
Index('idx_toolcall_status', ToolCall.status)

# Document deduplication
Index('idx_document_checksum', Document.checksum)

# Trace evaluation queries
Index('idx_trace_session', Trace.session_id)
Index('idx_trace_prompt_version', Trace.prompt_version)
Index('idx_evaluation_trace', Evaluation.trace_id)
Index('idx_evaluation_judgment', Evaluation.judgment)

# Dataset queries
Index('idx_datasetcase_dataset', DatasetCase.dataset_id)
Index('idx_datasetcase_trace', DatasetCase.trace_id)
```

---

## Database Migrations

This project uses **Alembic** for database schema migrations. See [MIGRATIONS.md](../MIGRATIONS.md) for detailed guide.

### Initial Migration

All 10 tables are created in the initial migration:

```bash
# Initialize database with all tables
just db-init

# Or use alembic directly
uv run alembic upgrade head
```

### Making Schema Changes

When modifying models, create a new migration:

```bash
# Autogenerate migration from model changes
just db-migrate "Description of change"

# Apply migration
just db-upgrade
```

### Why Alembic?

- ✅ Version control for database schema
- ✅ Team collaboration on schema changes
- ✅ Rollback support for errors
- ✅ Autogenerate detects model changes
- ✅ Production-ready migrations

---

## Next Steps

1. **Create SQLAlchemy models** in `src/software_builder/models/`
2. **Write Alembic migrations** for schema versioning
3. **Add Pydantic schemas** for validation and API
4. **Create test fixtures** for pytest
5. **Build CRUD operations** for each entity

Would you like me to implement the SQLAlchemy models next?
