# `.pi/` Directory

This directory contains configuration and helper files for developing Software Builder with LLM assistance (like Pi, Claude, ChatGPT, etc.).

## Files

### `SYSTEM.md`
**System prompt for LLM development assistants**

This file defines how LLMs should assist with Software Builder development. It includes:
- Project context and architecture
- Development workflow emphasizing small steps and validation
- Code quality standards and patterns
- Technology stack reference
- Common patterns and examples
- Constraints and best practices

**When to use**: Load this as context when starting a new LLM coding session to ensure the assistant understands the project structure, standards, and workflow.

### `EXAMPLE_SESSION.md`
**Example of a good development session**

Shows a complete development workflow:
- Writing tests first (TDD)
- Implementing with type hints
- Validating at each step
- Handling errors properly
- Committing when all checks pass

**When to use**: See what good incremental development looks like in practice.

### `QUICK_REFERENCE.md`
**Quick reference guide for common tasks**

A condensed reference containing:
- Validation command checklist
- Quick commands for development
- Code templates for common patterns
- Type hints reference
- Common errors and fixes
- Project structure overview

**When to use**: Quick lookup during development for commands, patterns, or troubleshooting.

### `justfile` (root directory)
**Command runner with structured validation and development tasks**

Provides organized recipes for all development tasks:
- **Validation**: `just validate`, `just quick`, `just validate-fix`
- **Individual checks**: `just type-check`, `just lint`, `just format`, `just test`
- **Testing**: `just test-verbose`, `just test-coverage`, `just test-file`
- **Development**: `just serve`, `just serve-dev`, `just db-init`
- **Utilities**: `just clean`, `just install`, `just info`

**Usage**:
```bash
# Run full validation after making changes
just validate

# See all available commands
just --list

# Get help with common commands
just help
```

Uses the `just` command runner (modern alternative to `make`) for better syntax and features.

See [JUSTFILE_REFERENCE.md](JUSTFILE_REFERENCE.md) for complete documentation of all commands.

### `JUSTFILE_REFERENCE.md`
**Complete reference for all just commands**

Comprehensive documentation for every `just` command including:
- Detailed descriptions of what each command does
- When to use each command
- Examples and common workflows
- Cheat sheet for quick lookup

**When to use**: Detailed reference for understanding all available just commands.

## How to Use with LLMs

### Starting a New Session

1. **Load the system prompt**:
   ```
   "Please read .pi/SYSTEM.md and use it as guidance for this development session."
   ```

2. **Reference the quick guide**:
   ```
   "Check .pi/QUICK_REFERENCE.md for the validation workflow and code patterns."
   ```

### During Development

1. **After every code change**, run:
   ```bash
   just validate
   ```
   
2. **For faster iteration** (skip tests):
   ```bash
   just quick
   ```

3. **Or run individual checks**:
   ```bash
   just type-check    # Type checking
   just lint          # Linting
   just format        # Formatting
   just test          # Testing
   ```

4. **Auto-fix and validate**:
   ```bash
   just validate-fix  # Fix issues and re-validate
   ```

5. **If validation fails**:
   - Fix the issues
   - Run `just validate` again
   - Don't proceed until all checks pass ✅

### Key Principles

The system prompt emphasizes:

✅ **Small Steps**: One change at a time  
✅ **Validate Always**: After every change  
✅ **Type Safety**: Full type hints everywhere  
✅ **Test Coverage**: Write tests first when possible  
✅ **Phase 1 Focus**: Web UI only (no TUI yet)  

## Integration with Pi Coding Agent

If using the [Pi coding agent](https://github.com/mariozechner/pi-coding-agent):

1. Pi automatically reads `.pi/SYSTEM.md` on startup
2. The system prompt is included in every LLM request
3. Validation commands can be run automatically
4. The workflow is enforced by the agent

## Benefits

**For Developers**:
- Consistent code quality across all LLM-assisted changes
- Reduced review time (code is pre-validated)
- Better learning of project patterns
- Faster onboarding for new contributors

**For LLMs**:
- Clear context about the project
- Explicit workflow to follow
- Guardrails against common mistakes
- Better code generation quality

## Updating These Files

As the project evolves, update these files to reflect:
- New patterns and conventions
- Additional validation steps
- Changed project structure
- New technology choices

Keep them in sync with `readme.md` and actual project code.

## Example Workflow

```bash
# 1. Start new feature
git checkout -b feature/trace-export

# 2. Ask LLM to implement (with SYSTEM.md context loaded)
# "Implement trace export functionality following .pi/SYSTEM.md guidelines"

# 3. LLM generates code with tests

# 4. Validate immediately
just validate

# 5. If validation passes ✅
git add .
git commit -m "Add trace export functionality"

# 6. If validation fails ❌
# Fix issues or use: just validate-fix
# Repeat until green ✅
```

## Related Files

- [../readme.md](../readme.md) - Full project documentation
- [../pyproject.toml](../pyproject.toml) - Tool configuration (ruff, ty, pytest)

---

**Remember**: The goal is to ensure every LLM-assisted change is **type-safe**, **well-tested**, and **follows project conventions**. These files make that goal achievable.
