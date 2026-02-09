# Software Builder - Just Commands
# Run `just` or `just --list` to see all available commands

# Default recipe - show help
default:
    @just --list

# Run all validation checks (type, lint, format, test)
validate:
    @echo "🔍 Software Builder - Code Validation"
    @echo "======================================"
    @echo ""
    @just type-check
    @echo ""
    @just lint
    @echo ""
    @just format-check
    @echo ""
    @just test
    @echo ""
    @echo "======================================"
    @echo "🎉 All validation checks passed!"
    @echo "======================================"
    @echo ""
    @echo "Your code is ready to commit. Changes validated:"
    @echo "  ✅ Type safety (ty)"
    @echo "  ✅ Code quality (ruff lint)"
    @echo "  ✅ Formatting (ruff format)"
    @echo "  ✅ Tests (pytest)"
    @echo ""

# Type check with ty
type-check:
    @echo "▶ Type checking with ty..."
    @uv run ty check
    @echo "✅ Type checking passed"

# Lint with ruff
lint:
    @echo "▶ Linting with ruff..."
    @uv run ruff check .
    @echo "✅ Linting passed"

# Check formatting with ruff
format-check:
    @echo "▶ Checking formatting with ruff..."
    @uv run ruff format --check .
    @echo "✅ Formatting check passed"

# Format code with ruff
format:
    @echo "▶ Formatting code with ruff..."
    @uv run ruff format .
    @echo "✅ Code formatted"

# Auto-fix linting issues
fix:
    @echo "▶ Auto-fixing linting issues..."
    @uv run ruff check --fix .
    @echo "✅ Issues fixed"

# Run all tests
test:
    @echo "▶ Running tests with pytest..."
    @uv run pytest -x
    @echo "✅ All tests passed"

# Run tests with verbose output
test-verbose:
    @echo "▶ Running tests (verbose)..."
    @uv run pytest -xvs

# Run tests with coverage report
test-coverage:
    @echo "▶ Running tests with coverage..."
    @uv run pytest --cov=src/software_builder --cov-report=term-missing

# Run specific test file
test-file FILE:
    @echo "▶ Running tests in {{FILE}}..."
    @uv run pytest {{FILE}} -xvs

# Run tests matching a pattern
test-match PATTERN:
    @echo "▶ Running tests matching '{{PATTERN}}'..."
    @uv run pytest -k "{{PATTERN}}" -xvs

# Run evaluation tests only
test-evals:
    @echo "▶ Running evaluation tests..."
    @uv run pytest tests/evals/ -xvs

# Quick validation (type + lint only, no tests)
quick:
    @echo "⚡ Quick validation (type + lint)..."
    @just type-check
    @just lint
    @echo "✅ Quick checks passed"

# Full validation + fix issues
validate-fix:
    @echo "🔧 Validating and fixing issues..."
    @just fix
    @just format
    @just type-check
    @just test
    @echo "✅ All issues fixed and validated"

# Start development server
serve PORT="5000":
    @echo "🌐 Starting web server on port {{PORT}}..."
    @uv run software-builder serve --port {{PORT}}

# Start development server with auto-reload
serve-dev:
    @echo "🌐 Starting dev server with auto-reload..."
    @FLASK_DEBUG=1 uv run software-builder serve

# Initialize database with Alembic migrations
db-init:
    @echo "💾 Initializing database with Alembic..."
    @mkdir -p data
    @uv run alembic upgrade head
    @echo "✅ Database initialized"

# Reset database (WARNING: destroys data)
db-reset:
    @echo "⚠️  Resetting database (this will delete all data)..."
    @rm -f data/software_builder.db
    @just db-init

# Create a new database migration
db-migrate MESSAGE:
    @echo "📝 Creating migration: {{MESSAGE}}"
    @uv run alembic revision --autogenerate -m "{{MESSAGE}}"
    @uv run ruff format alembic/versions/
    @echo "✅ Migration created and formatted"

# Upgrade database to latest version
db-upgrade:
    @echo "⬆️  Upgrading database to latest version..."
    @uv run alembic upgrade head
    @echo "✅ Database upgraded"

# Downgrade database by one revision
db-downgrade:
    @echo "⬇️  Downgrading database by 1 revision..."
    @uv run alembic downgrade -1
    @echo "✅ Database downgraded"

# Show migration history
db-history:
    @echo "📜 Database migration history:"
    @uv run alembic history --verbose

# Show current database revision
db-current:
    @echo "📍 Current database revision:"
    @uv run alembic current --verbose

# Start TUI (Phase 2)
tui:
    @echo "⌨️  Starting Terminal UI..."
    @uv run software-builder tui

# Start TUI in development mode
tui-dev:
    @echo "⌨️  Starting Terminal UI (dev mode)..."
    @textual run --dev tui/app.py

# Clean Python cache files
clean:
    @echo "🧹 Cleaning cache files..."
    @find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
    @find . -type d -name .pytest_cache -exec rm -rf {} + 2>/dev/null || true
    @find . -type d -name .ruff_cache -exec rm -rf {} + 2>/dev/null || true
    @find . -type f -name "*.pyc" -delete 2>/dev/null || true
    @echo "✅ Cache cleaned"

# Clean everything (cache + database + lock file)
clean-all: clean
    @echo "🧹 Deep cleaning..."
    @rm -f data/*.db 2>/dev/null || true
    @rm -f uv.lock 2>/dev/null || true
    @echo "✅ Deep clean complete"

# Install dependencies
install:
    @echo "📦 Installing dependencies..."
    @uv sync
    @echo "✅ Dependencies installed"

# Update dependencies
update:
    @echo "📦 Updating dependencies..."
    @uv lock --upgrade
    @uv sync
    @echo "✅ Dependencies updated"

# Add a new dependency
add PACKAGE:
    @echo "📦 Adding {{PACKAGE}}..."
    @uv add {{PACKAGE}}

# Add a new dev dependency
add-dev PACKAGE:
    @echo "📦 Adding {{PACKAGE}} (dev)..."
    @uv add --dev {{PACKAGE}}

# Show project info
info:
    @echo "📊 Software Builder - Project Info"
    @echo "======================================"
    @echo ""
    @echo "Python version:"
    @uv run python --version
    @echo ""
    @echo "Dependencies:"
    @uv pip list
    @echo ""
    @echo "Project structure:"
    @tree -L 2 -I '__pycache__|*.pyc|.pytest_cache|.ruff_cache' . || ls -la

# Run evaluation review interface
eval-review:
    @echo "📊 Opening evaluation review..."
    @uv run software-builder eval review

# Export evaluation dataset
eval-export DATASET:
    @echo "📊 Exporting dataset: {{DATASET}}"
    @uv run software-builder eval export --dataset {{DATASET}}

# Show evaluation stats
eval-stats:
    @echo "📊 Evaluation statistics:"
    @uv run software-builder eval stats

# List prompt versions
prompt-list:
    @echo "📝 Prompt versions:"
    @uv run software-builder prompt list

# Compare prompt versions
prompt-compare V1 V2:
    @echo "📝 Comparing prompts {{V1}} vs {{V2}}..."
    @uv run software-builder prompt compare {{V1}} {{V2}}

# Watch for changes and re-run validation
watch:
    @echo "👀 Watching for changes..."
    @echo "Run this in a separate terminal:"
    @echo "  find src tests -name '*.py' | entr -c just validate"

# Pre-commit hook - run before committing
pre-commit: validate
    @echo "✅ Pre-commit checks passed - safe to commit!"

# CI simulation - what GitHub Actions will run
ci: clean validate test-coverage
    @echo "✅ CI simulation complete"

# Development workflow helper
dev-loop:
    @echo "🔄 Development Loop"
    @echo "===================="
    @echo ""
    @echo "1. Make your changes"
    @echo "2. Run: just validate"
    @echo "3. Fix any issues"
    @echo "4. Run: just validate (again)"
    @echo "5. Commit when all checks pass ✅"
    @echo ""
    @echo "Quick commands:"
    @echo "  just quick       - Fast validation (no tests)"
    @echo "  just test-file   - Test specific file"
    @echo "  just fix         - Auto-fix linting issues"
    @echo "  just format      - Format code"

# Show commonly used commands
help:
    @echo "Software Builder - Common Commands"
    @echo "===================================="
    @echo ""
    @echo "Validation:"
    @echo "  just validate        - Full validation (type, lint, format, test)"
    @echo "  just quick           - Quick validation (type + lint only)"
    @echo "  just validate-fix    - Validate and auto-fix issues"
    @echo ""
    @echo "Individual Checks:"
    @echo "  just type-check      - Type check with ty"
    @echo "  just lint            - Lint with ruff"
    @echo "  just format          - Format code with ruff"
    @echo "  just test            - Run all tests"
    @echo ""
    @echo "Testing:"
    @echo "  just test-verbose    - Run tests with detailed output"
    @echo "  just test-coverage   - Run tests with coverage report"
    @echo "  just test-file FILE  - Run specific test file"
    @echo "  just test-evals      - Run evaluation tests only"
    @echo ""
    @echo "Development:"
    @echo "  just serve           - Start web server"
    @echo "  just serve-dev       - Start server with auto-reload"
    @echo "  just clean           - Clean cache files"
    @echo ""
    @echo "For all commands: just --list"
