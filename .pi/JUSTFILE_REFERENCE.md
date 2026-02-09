# Justfile Reference

Complete reference for all `just` commands in Software Builder.

## Quick Start

```bash
# See all available commands
just --list

# Run full validation
just validate

# Get help
just help
```

## Validation Commands

### `just validate`
**Run all validation checks** (type, lint, format, test)

This is the **primary command** to run after every code change.

**What it does**:
1. Type checking with ty
2. Linting with ruff check
3. Format checking with ruff format
4. All tests with pytest

**When to use**: After every code change, before committing

**Example**:
```bash
# Make a change
vim src/software_builder/models/trace.py

# Validate
just validate

# If all green ✅, commit
git commit -m "Add trace export method"
```

---

### `just quick`
**Fast validation** (type check + lint only, skip tests)

**When to use**: During rapid iteration when you want quick feedback

**Example**:
```bash
just quick  # ~2 seconds instead of ~30 seconds
```

---

### `just validate-fix`
**Auto-fix issues and validate**

**What it does**:
1. Auto-fix linting issues
2. Format code
3. Type check
4. Run tests

**When to use**: When you have linting or formatting errors

---

## Individual Validation Commands

### `just type-check`
Run ty type checker

**Example**:
```bash
just type-check
```

---

### `just lint`
Run ruff linter (check for issues)

**Example**:
```bash
just lint
```

---

### `just format`
Format code with ruff

**Example**:
```bash
just format
```

---

### `just format-check`
Check if code is formatted correctly (don't modify)

**Example**:
```bash
just format-check
```

---

### `just fix`
Auto-fix linting issues with ruff

**Example**:
```bash
just fix
```

---

## Testing Commands

### `just test`
Run all tests

**Example**:
```bash
just test
```

---

### `just test-verbose`
Run tests with verbose output (-xvs flags)

**Example**:
```bash
just test-verbose
```

---

### `just test-coverage`
Run tests with coverage report

**Example**:
```bash
just test-coverage
```

**Output**:
```
Name                              Stmts   Miss  Cover   Missing
---------------------------------------------------------------
src/software_builder/models.py      45      3    93%   123-125
```

---

### `just test-file FILE`
Run specific test file

**Parameters**:
- `FILE`: Path to test file

**Example**:
```bash
just test-file tests/test_agent.py
```

---

### `just test-match PATTERN`
Run tests matching a pattern

**Parameters**:
- `PATTERN`: String to match in test names

**Example**:
```bash
just test-match "llm"  # Runs test_llm_*, test_*_llm, etc.
```

---

### `just test-evals`
Run evaluation tests only

**Example**:
```bash
just test-evals
```

---

## Development Server Commands

### `just serve`
Start web server on port 5000

**Example**:
```bash
just serve
# Access at: http://localhost:5000
```

**Optional port**:
```bash
just serve 8080
# Access at: http://localhost:8080
```

---

### `just serve-dev`
Start server with auto-reload (development mode)

**Example**:
```bash
just serve-dev
```

---

## Database Commands

### `just db-init`
Initialize database

**Example**:
```bash
just db-init
```

---

### `just db-reset`
Reset database (WARNING: destroys all data)

**Example**:
```bash
just db-reset
```

**Confirmation**: Deletes `data/software_builder.db` and re-initializes

---

## Terminal UI Commands (Phase 2)

### `just tui`
Start Terminal UI

**Example**:
```bash
just tui
```

---

### `just tui-dev`
Start TUI in development mode with live reload

**Example**:
```bash
just tui-dev
```

---

## Utility Commands

### `just clean`
Clean Python cache files

**Removes**:
- `__pycache__` directories
- `.pytest_cache`
- `.ruff_cache`
- `*.pyc` files

**Example**:
```bash
just clean
```

---

### `just clean-all`
Deep clean (cache + database + lock file)

**Removes**:
- All cache files (from `just clean`)
- Database files (`data/*.db`)
- Lock file (`uv.lock`)

**Example**:
```bash
just clean-all
```

---

### `just install`
Install dependencies

**Example**:
```bash
just install
```

---

### `just update`
Update dependencies

**Example**:
```bash
just update
```

---

### `just add PACKAGE`
Add a new dependency

**Parameters**:
- `PACKAGE`: Package name

**Example**:
```bash
just add requests
```

---

### `just add-dev PACKAGE`
Add a new dev dependency

**Parameters**:
- `PACKAGE`: Package name

**Example**:
```bash
just add-dev pytest-mock
```

---

### `just info`
Show project information

**Shows**:
- Python version
- Installed dependencies
- Project structure

**Example**:
```bash
just info
```

---

## Evaluation Commands

### `just eval-review`
Open evaluation review interface

**Example**:
```bash
just eval-review
```

---

### `just eval-export DATASET`
Export evaluation dataset

**Parameters**:
- `DATASET`: Dataset name

**Example**:
```bash
just eval-export agent_errors
```

---

### `just eval-stats`
Show evaluation statistics

**Example**:
```bash
just eval-stats
```

---

## Prompt Commands

### `just prompt-list`
List all prompt versions

**Example**:
```bash
just prompt-list
```

---

### `just prompt-compare V1 V2`
Compare two prompt versions

**Parameters**:
- `V1`: First version
- `V2`: Second version

**Example**:
```bash
just prompt-compare v2 v3
```

---

## Workflow Helper Commands

### `just watch`
Instructions for watching files and re-running validation

**Example**:
```bash
just watch
```

**Output**:
```
👀 Watching for changes...
Run this in a separate terminal:
  find src tests -name '*.py' | entr -c just validate
```

---

### `just pre-commit`
Run all checks before committing (alias for `just validate`)

**Example**:
```bash
just pre-commit
```

---

### `just ci`
Simulate CI/CD pipeline (clean + validate + coverage)

**Example**:
```bash
just ci
```

---

### `just dev-loop`
Show development workflow instructions

**Example**:
```bash
just dev-loop
```

---

### `just help`
Show commonly used commands

**Example**:
```bash
just help
```

---

### `just` (default)
Show all available commands (alias for `just --list`)

**Example**:
```bash
just
```

---

## Common Workflows

### Starting a New Feature

```bash
# 1. Create branch
git checkout -b feature/my-feature

# 2. Make changes
vim src/software_builder/...

# 3. Validate quickly during development
just quick

# 4. When done, full validation
just validate

# 5. Commit if all green ✅
git commit -m "Add my feature"
```

### Fixing Issues

```bash
# 1. Make changes
vim src/software_builder/...

# 2. Auto-fix and validate
just validate-fix

# 3. If still failing, iterate
just test-verbose  # See detailed errors
# Fix issues
just validate      # Try again
```

### Running Specific Tests

```bash
# Test one file
just test-file tests/test_models.py

# Test by pattern
just test-match "export"

# Test with coverage
just test-coverage
```

### Pre-Commit Checklist

```bash
# 1. Clean up
just clean

# 2. Full validation
just validate

# 3. Check coverage
just test-coverage

# 4. All green? ✅ Commit!
git commit
```

## Tips

### Faster Iteration

Use `just quick` during development for faster feedback:
```bash
# Rapid development cycle
just quick  # ~2 seconds
# Make more changes
just quick
# When ready, full validation
just validate
```

### Aliases

Add to your shell config (`.bashrc`, `.zshrc`):
```bash
alias v='just validate'
alias vq='just quick'
alias vf='just validate-fix'
alias jt='just test-verbose'
```

### Debugging Test Failures

```bash
# Run specific failing test with verbose output
just test-file tests/test_agent.py

# Or match test name
just test-match "test_export"

# See full output
just test-verbose
```

### Before Every Commit

Make it a habit:
```bash
just validate && git commit -m "Your message"
```

Or set up a Git hook (`.git/hooks/pre-commit`):
```bash
#!/bin/bash
just validate || exit 1
```

---

## Cheat Sheet

| Task | Command |
|------|---------|
| **Full validation** | `just validate` |
| **Quick check** | `just quick` |
| **Auto-fix** | `just validate-fix` |
| **Run tests** | `just test` |
| **Test one file** | `just test-file FILE` |
| **Start server** | `just serve` |
| **Clean cache** | `just clean` |
| **Install deps** | `just install` |
| **See all commands** | `just --list` |

---

**Remember**: `just validate` before every commit! ✅
