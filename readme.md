# Software Builder

An advanced LLM coding agent focused on software engineering and prompt evaluation. This is a **local application** with **dual interfaces**: a web-based UI for rich interactions and a terminal UI for lightweight, keyboard-driven workflows.

## Overview

Software Builder provides two complementary interfaces for interacting with Large Language Models:

1. **Web UI (Phase 1 - Priority)**: Full-featured browser interface with Datastar for real-time updates, rich visualizations, and comprehensive evaluation dashboards
2. **Terminal UI (Phase 2 - Future)**: Lightweight TUI built with Textual for fast, keyboard-driven coding workflows directly in the terminal

Both interfaces share the same backend, database, and configuration. The application runs entirely on your machine and stores all data locally.

## LLM-Assisted Development

This project is designed to be developed **with LLM assistance** (Claude, ChatGPT, Pi, etc.). The `.pi/` directory contains system prompts and development guidelines.

**Key workflow**:
1. Make a small, focused change
2. Run `just validate` to verify all checks
3. Fix any issues until all checks pass ✅
4. Commit and repeat

**Quick Commands**:
- `just validate` - Run all validation checks
- `just quick` - Fast validation (skip tests)
- `just test-file FILE` - Test specific file
- `just --list` - See all available commands

See [`.pi/README.md`](.pi/README.md) for detailed guidance on LLM-assisted development.

## Technology Stack

### Core Framework
- **[uv](https://github.com/astral-sh/uv)** - Extremely fast Python package and project manager (written in Rust)
- **[Ruff](https://github.com/astral-sh/ruff)** - Fast Python linter and code formatter (written in Rust)
- **[ty](https://github.com/astral-sh/ty)** - Extremely fast Python type checker and language server (written in Rust, 10-100x faster than mypy/Pyright)
- **[Click](https://click.palletsprojects.com/)** - Command-line interface creation toolkit
- **[pytest](https://pytest.org/)** - Feature-rich testing framework with fixtures, parametrization, and extensive plugin ecosystem
- **[Pydantic](https://docs.pydantic.dev/)** & **[pydantic-settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)** - Type-safe data validation and YAML configuration management

### Backend
- **[Flask](https://flask.palletsprojects.com/)** - Lightweight WSGI web framework for the API and server
- **[SQLAlchemy](https://www.sqlalchemy.org/)** - SQL toolkit and Object-Relational Mapping (ORM)
- **[Alembic](https://alembic.sqlalchemy.org/)** - Database migration tool for version-controlled schema changes
- **[SQLite](https://www.sqlite.org/)** - Local file-based database for storing projects, conversations, and configurations
- **[LLM](https://llm.datasette.io/)** - CLI tool and Python library for interacting with Large Language Models (OpenAI, Anthropic Claude, Google Gemini, Meta Llama, and more)
- **[structlog](https://www.structlog.org/)** - Production-ready structured logging with context binding and multiple output formats (JSON, console, logfmt)

### Frontend - Web UI (Phase 1)
- **[Datastar](https://data-star.dev/)** - Hypermedia framework for building reactive web applications without complex JavaScript
  - Uses Server-Sent Events (SSE) for real-time updates
  - Reactive signals with `data-*` attributes
  - Backend-driven frontend updates
- **[datastar-py](https://pypi.org/project/datastar-py/)** - Official Python SDK for Datastar with Flask integration
  - ServerSentEventGenerator for creating SSE events
  - Response helpers for Flask routes
  - Signal reading from frontend
  - HTML attribute generation with IDE completion
- **[Pico.css](https://picocss.com/)** - Minimal CSS framework for clean, semantic styling

### Frontend - Terminal UI (Phase 2)
- **[Textual](https://textual.textualize.io/)** - Modern Python framework for building sophisticated terminal user interfaces
  - Rapid Application Development with a simple Python API
  - CSS-like styling system (TCSS) for terminal layouts
  - Rich widgets: DataTable, Tree, ListView, Input, TextArea, and more
  - Reactive programming model with data binding
  - Mouse support, keyboard navigation, and animations
  - Testing framework built-in for TUI applications
  - Cross-platform (works in terminal OR web browser via textual-web)
  - Built by the creators of Rich (Will McGugan's Textualize)

### Content Rendering
- **[Python-Markdown](https://github.com/Python-Markdown/markdown)** - Markdown to HTML conversion
- **[markdown-full-yaml-metadata](https://pypi.org/project/markdown-full-yaml-metadata/)** - YAML frontmatter extraction from markdown files
- **[Pygments](https://pygments.org/)** - Syntax highlighting for code blocks in the application

## Project Structure

```
software-builder/
├── pyproject.toml              # Project configuration (uv, ruff, ty, pytest)
├── justfile                    # Command runner (validation, testing, development tasks)
├── uv.lock                     # Dependency lock file
├── .env.example                # Example environment variables
├── readme.md
├── .pi/                        # LLM development assistance
│   ├── SYSTEM.md               # System prompt for LLM assistants
│   ├── QUICK_REFERENCE.md      # Quick reference guide
│   ├── EXAMPLE_SESSION.md      # Example development session
│   └── README.md               # Guide to LLM-assisted development
├── config/                     # Configuration files
│   ├── config.yaml             # Main configuration
│   ├── config.development.yaml # Development overrides
│   └── config.production.yaml  # Production overrides
├── src/
│   └── software_builder/       # Main package
│       ├── cli.py              # Click CLI entry point
│       ├── app.py              # Flask application
│       ├── config.py           # Configuration and logging setup
│       ├── models/             # SQLAlchemy database models
│       │   ├── trace.py        # Trace/execution records
│       │   ├── evaluation.py   # Eval results and annotations
│       │   ├── prompt.py       # Prompt versions
│       │   └── dataset.py      # Test datasets
│       ├── routes/             # Flask routes (UI, API, SSE)
│       │   ├── evals.py        # Evaluation UI routes
│       │   └── traces.py       # Trace viewing routes
│       ├── agent/              # Coding agent logic
│       │   └── tracer.py       # Automatic trace collection
│       ├── evals/              # Evaluation system
│       │   ├── __init__.py
│       │   ├── collectors.py   # Trace collection utilities
│       │   ├── annotators.py   # Manual annotation helpers
│       │   ├── evaluators.py   # Automated evaluator functions
│       │   └── datasets.py     # Dataset management
│       └── utils/              # Utilities (markdown, syntax highlighting)
├── static/                     # Static assets (CSS, JS) - Web UI
├── templates/                  # Jinja2 templates with Datastar - Web UI
│   ├── evals/                  # Evaluation UI templates
│   │   ├── trace_review.html  # Trace annotation interface
│   │   ├── dataset_view.html  # Dataset management
│   │   └── prompt_compare.html # Prompt version comparison
│   └── ...
├── tui/                        # Terminal UI (Textual) - Phase 2
│   ├── __init__.py
│   ├── app.py                  # Main Textual app
│   ├── screens/                # TUI screens
│   │   ├── agent_chat.py       # Agent interaction screen
│   │   ├── trace_viewer.py     # View execution traces
│   │   ├── eval_review.py      # Quick eval annotations
│   │   └── settings.py         # Configuration screen
│   ├── widgets/                # Custom TUI widgets
│   │   ├── llm_output.py       # Streaming LLM output widget
│   │   ├── trace_tree.py       # Hierarchical trace viewer
│   │   └── code_viewer.py      # Syntax-highlighted code
│   └── styles/                 # TCSS stylesheets
│       └── app.tcss            # Main TUI styles
├── data/                       # Local SQLite database
│   ├── software_builder.db     # Main database
│   └── traces/                 # Archived trace data
├── prompts/                    # Versioned prompt templates
│   ├── v1_agent_system.md
│   ├── v2_agent_system.md
│   └── prompt_registry.yaml
└── tests/                      # pytest test suite
    ├── conftest.py             # pytest fixtures and configuration
    ├── test_cli.py             # CLI tests
    ├── test_agent.py           # Agent logic tests
    ├── test_routes.py          # Flask route tests
    ├── test_models.py          # Database model tests
    ├── test_llm.py             # LLM integration tests
    └── evals/                  # Evaluation tests
        ├── test_evaluators.py  # Automated evaluator tests
        └── datasets/           # Test datasets
            └── agent_errors.json
```

## Dual-UI Strategy

Software Builder uniquely provides **two complementary interfaces** for different workflows and user preferences:

### 🌐 Web UI (Phase 1 - Current Priority)

**Technology**: Flask + Datastar + Pico.css

**Best for**:
- Rich visualizations and dashboards
- Evaluation trace review and annotation
- Complex multi-panel layouts
- Data tables and charts
- Mouse-driven interactions
- Split-screen comparisons
- Embedding images and rich media

**Use Cases**:
- Reviewing agent traces with detailed context
- Annotating evaluation datasets
- Comparing prompt versions side-by-side
- Viewing comprehensive metrics dashboards
- Managing configurations through forms
- Browsing dataset collections

### ⌨️ Terminal UI (Phase 2 - Future Development)

**Technology**: Textual (Python TUI framework)

**Best for**:
- Fast, keyboard-driven workflows
- SSH sessions and remote servers
- Lightweight resource usage
- Distraction-free coding environment
- Terminal-native developers
- Quick agent interactions

**Use Cases**:
- Running agent tasks from the terminal
- Quick file edits and code generation
- Monitoring agent activity in real-time
- Reviewing logs and traces inline
- Rapid prompt testing iterations
- Command palette for all actions

### 🔄 Shared Foundation

Both UIs share:
- ✅ Same SQLite database
- ✅ Same Flask backend API
- ✅ Same configuration system
- ✅ Same evaluation framework
- ✅ Same LLM integration
- ✅ Same trace collection
- ✅ Same authentication/permissions

**Development Phases**:
1. **Phase 1 (Current)**: Build complete Web UI with all features
2. **Phase 2 (Future)**: Add Terminal UI as alternative interface
3. **Benefit**: Users can choose their preferred interface or switch between them

### Why Two UIs?

**Web UI Strengths**:
- Better for complex visualizations and data-heavy interfaces
- Easier to build rich layouts with CSS frameworks
- More familiar for non-technical users
- Better for collaborative review sessions

**Terminal UI Strengths**:
- Native environment for terminal-focused developers
- Zero browser overhead, pure Python
- Works over SSH without port forwarding
- Faster startup and lower resource usage
- Keyboard-first workflows (Vim-style navigation)
- Integrates with existing terminal tooling

## Key Features (Planned)

> **📍 Development Strategy**: Phase 1 focuses exclusively on the **Web UI** to validate core features and workflows. Phase 2 will add the **Terminal UI** as an alternative interface using the same backend.

### Core Features (All Phases)

- 🤖 **Local LLM Integration** - Connect to various LLM providers or use local models
- 💾 **Local Storage** - All data stored locally in SQLite database
- 🎯 **Coding Agent** - Specialized prompts and tools for software engineering tasks
- 📝 **Built-in Eval System** - Comprehensive evaluation framework for continuous prompt and agent improvement:
  - **Trace Collection**: Automatic capture of all LLM interactions, tool calls, and agent steps
  - **Error Analysis Dashboard**: Review and annotate agent outputs with pass/fail judgments
  - **Dataset Management**: Build test cases from real failures, version control for eval datasets
  - **Prompt Versioning**: Track prompt changes with linked evaluations and performance metrics
  - **Automated Evaluators**: pytest integration for CI/CD testing of agent behaviors
  - **Manual Review Interface**: Custom UI for domain experts to review traces efficiently
  - **Binary Evaluations**: Simple pass/fail judgments for clarity and actionability
- ⚡ **Fast Tooling** - Built with modern Python tools (uv, Ruff, ty)
- 📊 **Structured Logging** - Production-ready logging with structlog for debugging and monitoring

### Web UI Features (Phase 1 - Priority)

- 🌐 **Real-time Updates** - Server-Sent Events (SSE) via Datastar for live LLM streaming
- 📊 **Rich Dashboards** - Evaluation metrics, trace timelines, prompt comparison views
- 🖱️ **Mouse-Friendly** - Point-and-click interface for reviewing and annotating
- 📋 **Data Tables** - Sortable, filterable tables for traces, datasets, and evaluations
- 🎨 **Responsive Design** - Clean, accessible UI with Pico.css
- 🔄 **Multi-Panel Layouts** - Split views for comparing prompts and outputs
- 📈 **Visualizations** - Charts and graphs for eval metrics over time

### Terminal UI Features (Phase 2 - Future)

- ⌨️ **Keyboard-Driven** - Vim-style navigation and command palette for fast workflows
- 🚀 **Lightning Fast** - Instant startup, low resource usage, pure Python
- 🌲 **Tree Navigation** - Hierarchical browsing of files, traces, and eval datasets
- 💻 **SSH-Friendly** - Full functionality over SSH without port forwarding
- 📟 **Live Streaming** - Watch LLM responses stream with syntax highlighting
- 🔍 **Fuzzy Search** - Quick navigation to any trace, prompt, or dataset
- 📱 **Split Panes** - Side-by-side code comparison and prompt testing
- 🎭 **Multiple Themes** - Light, dark, and custom color schemes

## Prerequisites

### Install Just (Command Runner)

This project uses [just](https://github.com/casey/just) as a command runner for development tasks.

**Installation**:

```bash
# macOS
brew install just

# Linux (using cargo)
cargo install just

# Or download binary from: https://github.com/casey/just/releases
```

**Verify installation**:
```bash
just --version
```

See all available commands:
```bash
just --list
```

### Install uv (Package Manager)

```bash
# macOS/Linux
curl -LsSf https://astral.sh/uv/install.sh | sh

# Or via pip
pip install uv
```

## Development Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/software-builder.git
cd software-builder

# Install dependencies with uv
uv sync --all-extras

# Initialize database with Alembic migrations
just db-init

# Verify setup
just test

# Start the web server (Phase 1)
just serve

# Or with custom settings
just serve --port 8080 --debug

# Access web UI at http://localhost:5000
```

**See [FLASK_APP.md](FLASK_APP.md) for**:
- Complete Flask application guide
- Route structure
- Datastar integration
- Template system
- Testing strategy

### Database Migrations

Software Builder uses **Alembic** for database schema migrations:

```bash
# Initialize database (first time)
just db-init

# Create new migration after modifying models
just db-migrate "Description of changes"

# Apply pending migrations
just db-upgrade

# Show current migration status
just db-current

# See all migrations
just db-history
```

See [MIGRATIONS.md](MIGRATIONS.md) for detailed migration guide.

## Usage (Planned)

### Web UI Commands
```bash
# Start the web UI
software-builder serve

# Specify port
software-builder serve --port 8080

# Open browser automatically
software-builder serve --open
```

### Terminal UI Commands (Phase 2)
```bash
# Start the TUI
software-builder tui

# Launch in development mode with live reload
software-builder tui --dev

# Open specific screen
software-builder tui --screen agent-chat

# Initialize or reset database
software-builder init

# Open a specific project
software-builder open /path/to/project

# Evaluation commands
software-builder eval review              # Open trace review interface
software-builder eval export --dataset agent_errors  # Export dataset
software-builder eval import dataset.json  # Import dataset
software-builder eval stats               # Show eval metrics
software-builder prompt list              # List prompt versions
software-builder prompt compare v2 v3     # Compare prompt performance
```

## Development Tools

### All-in-One Validation (Recommended)

**Run after every code change**:
```bash
just validate
```

This runs all validation tools in order:
1. ✅ Type checking (ty)
2. ✅ Linting (ruff check)
3. ✅ Formatting (ruff format)
4. ✅ Testing (pytest)

**Other validation commands**:
```bash
just quick          # Fast validation (skip tests)
just validate-fix   # Auto-fix issues and validate
just --list         # See all available commands
```

### Individual Tools

#### Type Checking
```bash
# Type check all Python files
just type-check
# Or directly: uv run ty check

# Type check with watch mode
uv run ty check --watch

# Check specific files or directories
uv run ty check src/software_builder/
```

#### Linting and Formatting
```bash
# Format code
just format
# Or directly: uv run ruff format .

# Check for linting issues
just lint
# Or directly: uv run ruff check .

# Auto-fix linting issues
just fix
# Or directly: uv run ruff check --fix .
```

#### Testing
```bash
# Run all tests
just test
# Or directly: uv run pytest

# Run with verbose output
just test-verbose
# Or directly: uv run pytest -xvs

# Run with coverage report
just test-coverage
# Or directly: uv run pytest --cov=src/software_builder --cov-report=term-missing

# Run specific test file
just test-file tests/test_agent.py
# Or directly: uv run pytest tests/test_agent.py -xvs

# Run tests matching a pattern
just test-match "llm"
# Or directly: uv run pytest -k "test_llm" -xvs

# Run only evaluation tests
just test-evals
# Or directly: uv run pytest tests/evals/ -xvs
```

#### TUI Development (Phase 2)
```bash
# Start TUI
just tui
# Or directly: uv run software-builder tui

# Run TUI in development mode with live reload
just tui-dev
# Or directly: textual run --dev tui/app.py

# Launch textual devtools console (in separate terminal)
textual console

# Take screenshot for documentation
textual run --screenshot tui/screenshots/main.svg tui/app.py

# Run TUI tests
just test-file tests/test_tui.py
```

## Tool Configuration (pyproject.toml)

All development tools (uv, ruff, ty, pytest) are configured in a single `pyproject.toml` file for consistency and maintainability.

```toml
[project]
name = "software-builder"
version = "0.1.0"
description = "Local LLM coding agent with built-in evaluation system"
requires-python = ">=3.11"
dependencies = [
    # Core
    "click>=8.1.0",
    "sqlalchemy>=2.0.0",
    "llm>=0.15.0",
    "pydantic>=2.0.0",
    "pydantic-settings>=2.0.0",
    "pyyaml>=6.0.0",
    "python-dotenv>=1.0.0",
    "structlog>=24.1.0",
    
    # Web UI (Phase 1)
    "flask>=3.0.0",
    "datastar-py>=0.3.0",
    
    # Terminal UI (Phase 2)
    "textual>=0.80.0",
    "textual-dev>=1.6.0",
    
    # Content rendering (shared by both UIs)
    "markdown>=3.5.0",
    "markdown-full-yaml-metadata>=2.2.0",
    "pygments>=2.17.0",
]

[project.optional-dependencies]
dev = [
    "ruff>=0.6.0",
    "ty>=0.1.0",
    "pytest>=8.0.0",
    "pytest-cov>=4.0.0",
    "pytest-flask>=1.3.0",
    "pytest-asyncio>=0.23.0",
]

[project.scripts]
software-builder = "software_builder.cli:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

# Ruff configuration - linting and formatting
[tool.ruff]
target-version = "py311"
line-length = 100

[tool.ruff.lint]
# Enable pycodestyle (E), Pyflakes (F), isort (I), and more
select = [
    "E",   # pycodestyle errors
    "W",   # pycodestyle warnings  
    "F",   # Pyflakes
    "I",   # isort
    "N",   # pep8-naming
    "UP",  # pyupgrade
    "B",   # flake8-bugbear
    "C4",  # flake8-comprehensions
    "SIM", # flake8-simplify
]
ignore = []

[tool.ruff.format]
quote-style = "double"
indent-style = "space"

# ty configuration - type checking
[tool.ty]
python-version = "3.11"

# Enable strict type checking
strict = true

# Type checking rules
[tool.ty.lint]
# Enable all recommended rules
select = [
    "TY001",  # Missing type annotation
    "TY002",  # Incompatible types
    "TY003",  # Unreachable code
    "TY004",  # Type narrowing
]

# Exclude certain files from type checking
exclude = [
    "migrations/",
    "tests/fixtures/",
]

# Per-module configuration
[tool.ty.overrides]
# Allow untyped definitions in test files
module = "tests.*"
disallow_untyped_defs = false

# pytest configuration
[tool.pytest.ini_options]
testpaths = ["tests"]
python_files = ["test_*.py", "*_test.py"]
python_classes = ["Test*"]
python_functions = ["test_*"]

# Markers for different test types
markers = [
    "unit: Unit tests",
    "integration: Integration tests",
    "eval: LLM evaluation tests",
    "slow: Slow-running tests",
]

# Coverage settings
addopts = [
    "--strict-markers",
    "--strict-config",
    "-ra",  # Show summary of all test outcomes
]

# Logging
log_cli = true
log_cli_level = "INFO"
log_cli_format = "%(asctime)s [%(levelname)8s] %(message)s"
log_cli_date_format = "%Y-%m-%d %H:%M:%S"

# Coverage
[tool.coverage.run]
source = ["src/software_builder"]
omit = [
    "*/tests/*",
    "*/migrations/*",
]

[tool.coverage.report]
exclude_lines = [
    "pragma: no cover",
    "def __repr__",
    "raise AssertionError",
    "raise NotImplementedError",
    "if __name__ == .__main__.:",
    "if TYPE_CHECKING:",
]
```

### Running All Tools Together

```bash
# Full development check (type check, lint, format, test)
uv run ty check && uv run ruff format . && uv run ruff check . && uv run pytest

# Or create a pre-commit script
# .git/hooks/pre-commit
#!/bin/bash
set -e
uv run ty check
uv run ruff format --check .
uv run ruff check .
uv run pytest --maxfail=1
```

## Configuration Management

Software Builder uses **pydantic-settings** for type-safe, validated configuration with multiple sources:

✅ **YAML Files** - Base and environment-specific configurations  
✅ **Environment Variables** - Override any setting with `SB_*` vars  
✅ **Type Validation** - Full Pydantic validation at startup  
✅ **Secret Management** - Secure handling of API keys with `SecretStr`  
✅ **Hot Reload** - Reload configuration at runtime  

See **[CONFIGURATION.md](CONFIGURATION.md)** for complete guide.

### Quick Configuration Example

```python
from software_builder.config import get_settings

# Get settings (singleton)
settings = get_settings()

# Access configuration
print(settings.database.url)
print(settings.llm.default_provider)
print(settings.web.port)
```

**Environment Variables**:
```bash
# Set API keys
export SB_LLM__OPENAI__API_KEY=sk-your-key-here
export SB_LLM__ANTHROPIC__API_KEY=sk-ant-your-key-here

# Override other settings
export SB_WEB__PORT=8080
export SB_DATABASE__URL=sqlite:///custom.db
```

**See [CONFIGURATION.md](CONFIGURATION.md) for**:
- Complete configuration reference
- Environment-specific configurations
- Secret management
- Validation rules
- Troubleshooting

---

## Evaluation System Design

Software Builder includes a built-in evaluation system based on industry best practices for LLM application development. This approach is inspired by [Hamel Husain and Shreya Shankar's work on LLM Evals](https://hamel.dev/blog/posts/evals-faq/).

### Core Principles

1. **Error Analysis First**: Spend 60-80% of development time reviewing actual outputs, not building infrastructure
2. **Traces Over Metrics**: Collect complete execution traces (inputs, outputs, tool calls, intermediate steps)
3. **Binary Evaluations**: Use simple pass/fail judgments instead of complex rating scales
4. **Domain Expertise**: Empower developers/engineers to make quality decisions, not external annotators
5. **Iterative Dataset Building**: Create test cases from real failures discovered through error analysis
6. **Cost-Benefit Automation**: Only build automated evaluators when the ROI justifies it

### Key Components

#### 1. **Trace Collection System**
Automatically captures every agent interaction:
```python
# Every agent execution is automatically traced
@trace_execution
def run_coding_task(prompt: str) -> Result:
    # All LLM calls, tool uses, and intermediate steps are logged
    return agent.execute(prompt)
```

#### 2. **Manual Review Interface**
Datastar-powered UI for reviewing traces:
- Side-by-side comparison of prompt versions
- Quick pass/fail annotation with optional notes
- Filter by error types, time ranges, prompt versions
- Real-time updates as new traces arrive

#### 3. **Dataset Management**
- Store annotated traces as test cases
- Version control for eval datasets
- Export/import for sharing with team
- Automatic dataset generation from production errors

#### 4. **Prompt Versioning**
```yaml
# prompts/prompt_registry.yaml
agent_system_prompt:
  current: v3
  versions:
    v1:
      file: v1_agent_system.md
      created: 2025-01-15
      metrics: {pass_rate: 0.65, avg_tokens: 450}
    v2:
      file: v2_agent_system.md
      created: 2025-01-20
      metrics: {pass_rate: 0.72, avg_tokens: 380}
    v3:
      file: v3_agent_system.md
      created: 2025-01-25
      metrics: {pass_rate: 0.78, avg_tokens: 350}
```

#### 5. **Automated Evaluators (pytest)**
```python
# tests/evals/test_evaluators.py
import pytest
from software_builder.evals import load_dataset

@pytest.mark.parametrize("case", load_dataset("agent_errors"))
def test_no_hallucinated_files(case):
    """Ensure agent doesn't reference non-existent files"""
    result = run_agent(case.input)
    mentioned_files = extract_file_references(result.output)
    assert all(f in case.context.files for f in mentioned_files)
```

### Workflow

1. **Develop** → Agent generates outputs
2. **Trace** → All executions automatically logged to SQLite
3. **Review** → Domain expert reviews traces in web UI (15-30 min daily)
4. **Annotate** → Mark as pass/fail, add notes about failure modes
5. **Dataset** → Failed cases become test cases
6. **Evaluate** → Run pytest on dataset before deploying changes
7. **Improve** → Fix issues, adjust prompts, repeat

### Integration with Development

- **CI/CD**: pytest runs eval suite on every commit
- **Local Dev**: Quick `software-builder eval review` command
- **Production**: Continuous trace collection, periodic manual review
- **Team Sharing**: Export datasets, share prompt versions via git

## Why These Technologies?

### uv & Ruff
Both tools are written in Rust and provide 10-100x speed improvements over traditional Python tools. They work seamlessly together and use the same `pyproject.toml` configuration.

### ty (Type Checker)
**ty** is Astral's ultra-fast Python type checker and language server, completing the Rust-based development toolchain (uv + Ruff + ty).

**Why ty for Software Builder:**

✅ **Blazing Fast**: 10-100x faster than mypy and Pyright - type checking takes milliseconds, not seconds  
✅ **AI Code Safety**: Essential for a coding agent that generates and validates Python code  
✅ **Real-time IDE Feedback**: Language server provides instant type hints, completions, and error detection  
✅ **Advanced Type System**: Sophisticated features perfect for complex agent logic:
  - First-class intersection types for precise code validation
  - Advanced type narrowing for control flow analysis  
  - Reachability analysis to detect unreachable code paths
  - Support for partially typed code (gradual typing)
✅ **Production Ready**: Backed by Astral, maintained by the creators of uv and Ruff  
✅ **Developer Experience**: Rich diagnostics with contextual information help debug agent-generated code  
✅ **Configuration**: Configurable rule levels, per-file overrides, suppression comments  

**For a Coding Agent Project:**
- **Generate Valid Code**: Type check agent outputs before execution
- **Catch Errors Early**: Validate LLM-generated code against type signatures
- **Safe Refactoring**: Confidently modify agent code with instant feedback
- **Documentation**: Type hints serve as machine-readable documentation
- **Code Quality**: Ensure the agent follows Python type conventions

ty integrates seamlessly with the existing Astral toolchain and works with the same `pyproject.toml` configuration.

### Datastar & datastar-py (Web UI - Phase 1)
Datastar is a modern hypermedia framework that enables reactive, real-time web applications without the complexity of traditional JavaScript frameworks. Perfect for server-side Python applications that need interactive UIs.

**datastar-py** is the official Python SDK that makes Datastar integration seamless:
- **Flask-Ready**: Built-in Flask helpers for SSE responses (`DatastarResponse`, `datastar_response` decorator)
- **Event Generation**: Simple API for creating Datastar events (`ServerSentEventGenerator`)
- **Signal Management**: Easy reading of frontend state via `read_signals()`
- **Attribute Helpers**: Generate `data-*` HTML attributes with IDE completion and type checking
- **Framework Agnostic Core**: Can work with any Python web framework, with specific helpers for Flask, Django, FastAPI, and more
- **Production Ready**: Official SDK maintained by the Datastar team

### Textual (Terminal UI - Phase 2)
**Textual** is a modern Python framework for building sophisticated, beautiful terminal user interfaces with a simple Python API. Built by Textualize (creators of the popular Rich library).

**Why Textual for Software Builder:**

✅ **Rapid Development**: Build complex TUIs as fast as web UIs - reactive widgets, layouts, and styling  
✅ **Modern Python**: Async/await support, type hints, and Pythonic API design  
✅ **CSS-like Styling**: TCSS (Textual CSS) - familiar web-style layouts in the terminal  
✅ **Rich Widget Library**: DataTable, Tree, ListView, Input, TextArea, Markdown, LoadingIndicator, and more  
✅ **Reactive Programming**: Data binding and reactive variables for automatic UI updates  
✅ **Mouse + Keyboard**: Full mouse support alongside powerful keyboard shortcuts  
✅ **Testing Framework**: Built-in testing tools for TUI applications  
✅ **Cross-Platform**: Works in any terminal, and even in web browsers via textual-web  
✅ **Developer Experience**: Live reload with `textual run --dev`, built-in devtools console  
✅ **Battle-Tested**: Powers production apps like Posting (Postman alternative), Harlequin (SQL IDE), and many more  

**For a Coding Agent TUI:**
- **Fast Workflows**: Keyboard-driven agent interactions for terminal power users
- **Streaming Output**: Watch LLM responses stream in real-time with Rich rendering
- **Syntax Highlighting**: Built-in code viewer with syntax highlighting via Pygments
- **Tree Navigation**: Browse file structures, traces, and eval datasets hierarchically
- **Split Layouts**: Side-by-side code comparison and prompt testing
- **Command Palette**: Quick access to all agent commands (Ctrl+P style)
- **Async Operations**: Non-blocking LLM calls while maintaining responsive UI
- **Terminal Native**: No browser required, works over SSH, low resource usage

**Textual vs Alternatives:**

| Framework | Pros | Cons | Why Not? |
|-----------|------|------|----------|
| **Textual** ✅ | Modern API, CSS styling, Rich widgets, Testing | Relatively new (2021) | **CHOSEN** |
| **curses** | Built-in standard library | Low-level, complex API, poor Windows support | Too difficult |
| **urwid** | Mature, stable | Old API design, limited widgets | Less developer-friendly |
| **py-cui** | Simple API | Limited features, abandoned | Not maintained |
| **blessed** | Pythonic, simple | No widget system, manual layout | Too basic |
| **Rich** | Beautiful output | Not a TUI framework, limited interactivity | Display-only |

**Development Experience:**
```python
# Example Textual app structure
from textual.app import App, ComposeResult
from textual.widgets import Header, Footer, DataTable

class SoftwareBuilderTUI(App):
    CSS_PATH = "app.tcss"  # CSS-like styling
    
    def compose(self) -> ComposeResult:
        yield Header()
        yield DataTable()  # Rich widget library
        yield Footer()
    
    async def on_mount(self) -> None:
        # Async operations built-in
        table = self.query_one(DataTable)
        await self.load_traces(table)
```

**Live Development:**
```bash
# Auto-reload on code changes
textual run --dev software_builder/tui/app.py

# Debug console built-in
textual console

# Screenshot testing
textual run --screenshot trace_viewer.svg
```

**Why Phase 2:**
- Web UI covers 80% of use cases (visualizations, dashboards, forms)
- TUI complements for terminal-focused workflows
- Same backend, different frontend - architectural separation
- Can develop TUI after validating core features with Web UI
- Textual's modern design makes it easy to add later

### Click
Industry-standard Python CLI framework with excellent composability and testing support. Ideal for building the command-line interface.

### LLM (llm.datasette.io)
Provides a unified interface for multiple LLM providers, making it easy to switch between models or support multiple models simultaneously.

### SQLite
Perfect for local applications - no server setup required, file-based storage, and excellent Python support through SQLAlchemy.

### structlog
Production-ready structured logging library that has been battle-tested since 2013. Unlike traditional logging, structlog:
- Uses structured data (dictionaries) instead of string interpolation
- Supports context binding for request tracking and debugging
- Offers multiple output formats (pretty console for development, JSON for production)
- Integrates seamlessly with Flask and the standard library's logging
- Provides excellent performance through lazy evaluation and processors
- Works naturally with modern Python features like contextvars and type hints

### Pydantic & pydantic-settings
**Pydantic** is Python's most popular data validation library, using Python type hints for runtime validation and serialization. **pydantic-settings** extends this for configuration management:
- **Type-Safe Config**: Validate config at startup using Python type hints
- **Multiple Sources**: Load from YAML, JSON, TOML, environment variables, .env files, and CLI
- **Automatic Coercion**: Convert strings to appropriate types (int, bool, lists, etc.)
- **Secret Management**: Dedicated `SecretStr` type for sensitive data
- **IDE Support**: Full autocomplete and type checking
- **Validation**: Custom validators for complex business rules
- **Environment Override**: `APP__DATABASE__URL` automatically overrides nested config
- **Battle-Tested**: Used by FastAPI, typer, and thousands of production apps
- **Perfect for this project**: Seamless integration with Flask, SQLAlchemy, and our tech stack

### pytest
The industry-standard Python testing framework that makes writing and running tests simple and scalable:
- **Simple & Readable**: Uses plain `assert` statements instead of `self.assertEqual()` methods
- **Auto-discovery**: Automatically finds test files and functions following simple naming conventions
- **Powerful Fixtures**: Dependency injection system for managing test setup, teardown, and shared resources
- **Parametrization**: Run the same test with different inputs easily - perfect for LLM evaluation datasets
- **Rich Plugin Ecosystem**: Over 1,300+ plugins including pytest-cov (coverage), pytest-flask (Flask testing), pytest-asyncio (async support)
- **Detailed Failure Reports**: Shows exactly what failed with helpful context and variable values
- **Flexible**: Can run unittest tests, supports both unit and integration testing
- **Perfect for this project**: Excellent for both traditional testing AND LLM output evaluation
  - Parametrized tests can run eval datasets through automated evaluators
  - Built-in assertion introspection perfect for checking LLM outputs
  - Easy CI/CD integration for regression testing on prompt changes

## Contributing

We welcome contributions! This project is designed for **LLM-assisted development**.

**Quick Start**:
1. Read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
2. Load [`.pi/SYSTEM.md`](.pi/SYSTEM.md) as context for LLM sessions
3. Use `just validate` after every change
4. Follow the workflow: Test → Code → Validate → Commit

**Key Requirements**:
- ✅ All code must have type hints (validated with `just type-check`)
- ✅ All code must pass linting (`just lint`)
- ✅ All code must be formatted (`just format`)
- ✅ All code must have tests (`just test`)

**All-in-one**: `just validate` runs all checks

See [`.pi/EXAMPLE_SESSION.md`](.pi/EXAMPLE_SESSION.md) for a complete development workflow example.

## License

TBD

## Resources

### Framework & Tools
**Web UI**:
- [Datastar Documentation](https://data-star.dev/guide)
- [datastar-py Documentation](https://github.com/starfederation/datastar-python)
- [Flask Documentation](https://flask.palletsprojects.com/)
- [Pico.css Documentation](https://picocss.com/docs)

**Terminal UI**:
- [Textual Documentation](https://textual.textualize.io/)
- [Textual GitHub Repository](https://github.com/Textualize/textual)
- [Textual Widget Gallery](https://textual.textualize.io/widget_gallery/)
- [Textual Blog & Tutorials](https://textual.textualize.io/blog/)
- [Rich Documentation](https://rich.readthedocs.io/) (Used by Textual for rendering)

**Core Tools**:
- [LLM Documentation](https://llm.datasette.io/)
- [uv Documentation](https://docs.astral.sh/uv/)
- [Ruff Documentation](https://docs.astral.sh/ruff/)
- [ty Documentation](https://docs.astral.sh/ty/)
- [ty GitHub Repository](https://github.com/astral-sh/ty)
- [ty Playground](https://play.ty.dev)
- [Click Documentation](https://click.palletsprojects.com/)
- [structlog Documentation](https://www.structlog.org/)
- [pytest Documentation](https://docs.pytest.org/)
- [Pydantic Documentation](https://docs.pydantic.dev/)
- [Pydantic Settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)

### LLM Evaluation Best Practices
- [LLM Evals: Everything You Need to Know](https://hamel.dev/blog/posts/evals-faq/) - Comprehensive FAQ by Hamel Husain & Shreya Shankar
- [Your AI Product Needs Evals](https://hamel.dev/blog/posts/evals/) - Introduction to eval systems
- [Creating a LLM-as-a-Judge](https://hamel.dev/blog/posts/llm-judge/) - Building automated evaluators
- [A Field Guide to Rapidly Improving AI Products](https://hamel.dev/blog/posts/field-guide/) - Error analysis and iteration strategies
