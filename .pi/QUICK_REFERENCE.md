# Software Builder - Quick Reference

## Development Workflow Checklist

### Every Code Change Must:

1. [ ] **Type Check**: `just type-check`
2. [ ] **Lint**: `just lint`
3. [ ] **Format**: `just format`
4. [ ] **Test**: `just test`

**All-in-one validation**:
```bash
just validate
```

**Quick validation** (skip tests):
```bash
just quick
```

## Current Phase: Phase 1 (Web UI)

**In Scope**:
- ✅ Flask routes and templates
- ✅ Datastar reactive UI
- ✅ SQLAlchemy models
- ✅ Web-based evaluation dashboard
- ✅ Pico.css styling

**Out of Scope** (Phase 2):
- ❌ Textual/TUI features
- ❌ Terminal UI screens
- ❌ TCSS stylesheets

## Quick Commands

### Validation (Most Important!)
```bash
# Full validation - run after every change
just validate

# Quick validation (type + lint only)
just quick

# Validate and auto-fix issues
just validate-fix

# Individual checks
just type-check
just lint
just format
just test
```

### Development
```bash
# Start web server
just serve

# Start server with auto-reload
just serve-dev

# Initialize database
just db-init

# Reset database (WARNING: deletes data)
just db-reset
```

### Testing
```bash
# Run all tests
just test

# Run tests (verbose)
just test-verbose

# Run with coverage
just test-coverage

# Run specific test file
just test-file tests/test_agent.py

# Run tests matching pattern
just test-match "llm"

# Run evaluation tests only
just test-evals
```

### Dependencies
```bash
# Install dependencies
just install

# Update dependencies
just update

# Add new dependency
just add package-name

# Add dev dependency
just add-dev package-name
```

### Utilities
```bash
# Clean cache files
just clean

# Clean everything (cache + DB)
just clean-all

# Show project info
just info

# List all commands
just --list

# Show help
just help
```

## Code Templates

### New Model
```python
from sqlalchemy import Column, Integer, String, DateTime
from sqlalchemy.orm import DeclarativeBase
from datetime import datetime

class Base(DeclarativeBase):
    pass

class MyModel(Base):
    __tablename__ = "my_models"
    
    id: int = Column(Integer, primary_key=True)
    name: str = Column(String, nullable=False)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
```

### New Flask Route
```python
from flask import Blueprint, render_template
from datastar import ServerSentEventGenerator

bp = Blueprint("my_feature", __name__, url_prefix="/my-feature")

@bp.route("/")
def index():
    """Render the main page."""
    return render_template("my_feature/index.html")

@bp.route("/data")
def get_data():
    """Return data via SSE."""
    sse = ServerSentEventGenerator()
    data = get_my_data()
    sse.merge_fragments({"#data-container": render_data(data)})
    return sse.response()
```

### New Test
```python
import pytest
from software_builder.models import MyModel

@pytest.fixture
def sample_model(db_session):
    """Create a sample model for testing."""
    model = MyModel(name="test")
    db_session.add(model)
    db_session.commit()
    return model

def test_model_creation(sample_model):
    """Test that model is created correctly."""
    assert sample_model.id is not None
    assert sample_model.name == "test"
    assert sample_model.created_at is not None
```

### Structured Logging
```python
import structlog

logger = structlog.get_logger()

# Info logging
logger.info("trace_collected", trace_id=trace.id, tokens=trace.tokens)

# Error logging
logger.error("llm_failure", provider="openai", error=str(e))

# Debug logging
logger.debug("processing_started", item_count=len(items))
```

### Pydantic Configuration
```python
from pydantic import Field
from pydantic_settings import BaseSettings

class MyConfig(BaseSettings):
    """Configuration for my feature."""
    
    name: str = Field(default="default", description="Feature name")
    enabled: bool = Field(default=True, description="Whether feature is enabled")
    max_items: int = Field(default=100, ge=1, le=1000)
    
    class Config:
        env_prefix = "MY_FEATURE_"
```

## Type Hints Quick Reference

```python
# Basic types
def process(name: str, count: int, enabled: bool) -> None:
    ...

# Optional
from typing import Optional
def get_user(id: int) -> Optional[User]:
    ...

# Lists and dicts
from typing import List, Dict
def process_items(items: List[str]) -> Dict[str, int]:
    ...

# Modern syntax (Python 3.10+)
def process_items(items: list[str]) -> dict[str, int]:
    ...

# Union types
from typing import Union
def process(value: Union[str, int]) -> str:
    ...

# Or modern syntax
def process(value: str | int) -> str:
    ...

# Generic types
from typing import TypeVar, Generic
T = TypeVar('T')
def first(items: List[T]) -> T:
    ...

# Pydantic models
from pydantic import BaseModel
def process_config(config: MyConfig) -> Result:
    ...
```

## Common Errors & Fixes

### Type Error: "Argument has incompatible type"
```python
# ❌ Problem
def process(items: List[str]) -> None:
    ...
process([1, 2, 3])  # Type error!

# ✅ Solution
process(["1", "2", "3"])
# Or fix the function signature
def process(items: List[int]) -> None:
    ...
```

### Linting Error: "Unused import"
```python
# ❌ Problem
from typing import List  # Unused!

# ✅ Solution: Remove it
# Or use it
def process() -> List[str]:
    ...
```

### Test Failure: "fixture not found"
```python
# ❌ Problem
def test_something(my_fixture):  # Fixture doesn't exist
    ...

# ✅ Solution: Define in conftest.py
# tests/conftest.py
import pytest

@pytest.fixture
def my_fixture():
    return "test_data"
```

## Project Structure Reference

```
software-builder/
├── src/software_builder/
│   ├── cli.py          # Click commands
│   ├── app.py          # Flask app
│   ├── config.py       # Pydantic settings
│   ├── models/         # SQLAlchemy models
│   ├── routes/         # Flask blueprints
│   ├── agent/          # Agent logic
│   └── evals/          # Evaluation system
├── tests/
│   ├── conftest.py     # Shared fixtures
│   └── test_*.py       # Test files
├── templates/          # Jinja2 templates
├── static/             # CSS, JS
├── config/             # YAML configs
└── pyproject.toml      # Project config
```

## Emergency Fixes

### Fix all linting issues
```bash
uv run ruff check --fix .
uv run ruff format .
```

### Reset database
```bash
rm data/software_builder.db
uv run software-builder init
```

### Clear test cache
```bash
find . -type d -name __pycache__ -exec rm -rf {} +
find . -type d -name .pytest_cache -exec rm -rf {} +
```

### Regenerate lock file
```bash
rm uv.lock
uv sync
```

## Getting Help

1. Check the main [readme.md](../../readme.md)
2. Review [SYSTEM.md](.pi/SYSTEM.md) for detailed guidelines
3. Look at existing code in `src/software_builder/`
4. Check tests in `tests/` for examples
5. Run `uv run software-builder --help` for CLI commands

## Remember

**Small steps → Validate → Commit → Repeat**

Every change must pass: **ty → ruff → pytest**
