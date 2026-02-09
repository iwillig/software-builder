# Example LLM Development Session

This document shows what a good development session looks like when building Software Builder with LLM assistance.

## Session Setup

**User**: "Let's add a function to export evaluation traces to JSON. Load the system prompt first."

**LLM**: 
```
I've read .pi/SYSTEM.md. I understand this project:
- Uses Flask + Datastar for Web UI (Phase 1)
- Requires type hints, tests, and validation for every change
- Follows incremental development: small steps → validate → commit

I'll help you add trace export functionality with:
1. Type-safe implementation
2. Tests first (TDD)
3. Validation after each step

Ready to proceed!
```

## Step 1: Write Test First (TDD)

**LLM**:
```
Let me start by writing a test for the trace export functionality.
This will define the expected behavior before implementation.

I'll add this to tests/test_evals.py:
```

```python
# tests/test_evals.py
import pytest
import json
from software_builder.evals.exporters import export_traces_to_json
from software_builder.models import Trace

def test_export_traces_to_json(db_session, sample_traces):
    """Test exporting traces to JSON format."""
    # Given: Sample traces in database
    trace1 = Trace(prompt="test 1", response="result 1", model="gpt-4")
    trace2 = Trace(prompt="test 2", response="result 2", model="gpt-4")
    db_session.add_all([trace1, trace2])
    db_session.commit()
    
    # When: Export to JSON
    result = export_traces_to_json(db_session, [trace1.id, trace2.id])
    
    # Then: Valid JSON with expected structure
    data = json.loads(result)
    assert len(data["traces"]) == 2
    assert data["traces"][0]["prompt"] == "test 1"
    assert data["traces"][1]["prompt"] == "test 2"
    assert "timestamp" in data["traces"][0]
```

**Validation**:
```bash
just test-file tests/test_evals.py
```

**Expected**: Test fails (function doesn't exist yet) ❌

**LLM**: "Test written and failing as expected. Ready for implementation."

## Step 2: Implement Functionality

**LLM**:
```
Now I'll implement the export_traces_to_json function with full type hints.
```

```python
# src/software_builder/evals/exporters.py
import json
from datetime import datetime
from typing import List
from sqlalchemy.orm import Session
from software_builder.models import Trace

def export_traces_to_json(
    session: Session,
    trace_ids: List[int]
) -> str:
    """
    Export traces to JSON format.
    
    Args:
        session: Database session
        trace_ids: List of trace IDs to export
        
    Returns:
        JSON string containing trace data
        
    Raises:
        ValueError: If any trace ID is not found
    """
    traces = session.query(Trace).filter(Trace.id.in_(trace_ids)).all()
    
    if len(traces) != len(trace_ids):
        found_ids = {t.id for t in traces}
        missing_ids = set(trace_ids) - found_ids
        raise ValueError(f"Traces not found: {missing_ids}")
    
    data = {
        "version": "1.0",
        "exported_at": datetime.utcnow().isoformat(),
        "traces": [
            {
                "id": trace.id,
                "prompt": trace.prompt,
                "response": trace.response,
                "model": trace.model,
                "timestamp": trace.created_at.isoformat(),
                "tokens": trace.tokens,
            }
            for trace in traces
        ]
    }
    
    return json.dumps(data, indent=2)
```

**Validation Step 1 - Type Check**:
```bash
just type-check
```
**Result**: ✅ No type errors

**Validation Step 2 - Lint**:
```bash
just lint
```
**Result**: ✅ No linting issues

**Validation Step 3 - Format**:
```bash
just format
```
**Result**: ✅ Code formatted

**Validation Step 4 - Test**:
```bash
just test-file tests/test_evals.py
```
**Result**: ✅ Test passes!

**LLM**: "Implementation complete and validated. All checks pass."

## Step 3: Add Route (Small Next Step)

**User**: "Great! Now add a Flask route to expose this functionality."

**LLM**:
```
I'll add a route to src/software_builder/routes/evals.py
This is a small, focused change that builds on the previous step.
```

```python
# src/software_builder/routes/evals.py
from flask import Blueprint, request, Response
from software_builder.evals.exporters import export_traces_to_json
from software_builder.models import get_session
import structlog

bp = Blueprint("evals", __name__, url_prefix="/evals")
logger = structlog.get_logger()

@bp.route("/export", methods=["POST"])
def export_traces() -> Response:
    """Export selected traces to JSON."""
    trace_ids = request.json.get("trace_ids", [])
    
    if not trace_ids:
        return Response(
            '{"error": "No trace IDs provided"}',
            status=400,
            mimetype="application/json"
        )
    
    try:
        with get_session() as session:
            json_data = export_traces_to_json(session, trace_ids)
            
        logger.info("traces_exported", count=len(trace_ids))
        
        return Response(
            json_data,
            mimetype="application/json",
            headers={"Content-Disposition": "attachment; filename=traces.json"}
        )
        
    except ValueError as e:
        logger.error("export_failed", error=str(e))
        return Response(
            f'{{"error": "{str(e)}"}}',
            status=404,
            mimetype="application/json"
        )
```

**Add Route Test**:
```python
# tests/test_routes.py
def test_export_traces_route(client, db_session):
    """Test the /evals/export route."""
    # Given: Traces in database
    trace1 = Trace(prompt="test", response="result")
    db_session.add(trace1)
    db_session.commit()
    
    # When: POST to export endpoint
    response = client.post(
        "/evals/export",
        json={"trace_ids": [trace1.id]}
    )
    
    # Then: Returns JSON file
    assert response.status_code == 200
    assert response.mimetype == "application/json"
    data = response.get_json()
    assert len(data["traces"]) == 1
    assert data["traces"][0]["prompt"] == "test"
```

**Run Validation**:
```bash
just validate
```

**Result**:
```
🔍 Software Builder - Code Validation
======================================

▶ Type checking with ty...
✅ Type checking passed

▶ Linting with ruff...
✅ Linting passed

▶ Checking formatting with ruff...
✅ Formatting check passed

▶ Running tests with pytest...
✅ All tests passed

======================================
🎉 All validation checks passed!
======================================

Your code is ready to commit. Changes validated:
  ✅ Type safety (ty)
  ✅ Code quality (ruff lint)
  ✅ Formatting (ruff format)
  ✅ Tests (pytest)
```

**LLM**: "Route added and fully validated. Ready to commit!"

## Step 4: Commit

**User**: "Perfect! Let's commit this."

**LLM**:
```bash
git add src/software_builder/evals/exporters.py
git add src/software_builder/routes/evals.py
git add tests/test_evals.py
git add tests/test_routes.py
git commit -m "Add trace export to JSON functionality

- Implement export_traces_to_json() with full type hints
- Add /evals/export Flask route with error handling
- Include comprehensive tests for both functions
- All validation checks pass (ty, ruff, pytest)"
```

## What Made This Session Good?

✅ **Small Steps**:
   - Test first
   - Implement function
   - Add route
   - Each step ~20-50 lines

✅ **Validation at Each Step**:
   - Ran type checker, linter, formatter, tests
   - Fixed issues immediately
   - Didn't proceed until all green

✅ **Type Safety**:
   - Full type hints on all functions
   - Specified return types
   - Used Pydantic/SQLAlchemy types

✅ **Tests First (TDD)**:
   - Wrote test before implementation
   - Test defined expected behavior
   - Test passed after implementation

✅ **Error Handling**:
   - Raised specific exceptions
   - Used structured logging
   - Returned appropriate HTTP status codes

✅ **Documentation**:
   - Docstrings on public functions
   - Clear parameter descriptions
   - Documented error cases

## What to Avoid

❌ **Bad Session Example**:
```
User: "Add trace export, route, CLI command, and tests"

LLM: [Changes 10 files at once, 500+ lines]

User: "Now validate"

LLM runs pytest:
- 5 type errors ❌
- 12 linting errors ❌
- 3 test failures ❌

Now we have to debug a massive change with multiple issues.
```

**Why this is bad**:
- Too many changes at once
- Hard to debug failures
- Didn't validate incrementally
- Can't identify which change broke what

## Key Takeaways

1. **One focused change** at a time
2. **Validate immediately** after each change
3. **Write tests first** when possible
4. **Full type hints** on all new code
5. **Don't proceed** until all checks pass ✅

---

**The Perfect Development Loop**:

```
Test → Code → Validate → Fix → Commit → Repeat
  ↓      ↓       ↓        ↓       ↓
 TDD  +Types  ty+ruff  Iterate  Green
              +pytest            ✅
```

This ensures every change is **tested**, **type-safe**, and **ready to ship**.
