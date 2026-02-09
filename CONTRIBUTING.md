# Contributing to Software Builder

Thank you for your interest in contributing! This project is designed to be developed with LLM assistance, so whether you're a human or an AI assistant, these guidelines will help you contribute effectively.

## Quick Start

1. **Read the system prompt**: [`.pi/SYSTEM.md`](.pi/SYSTEM.md)
2. **Check the quick reference**: [`.pi/QUICK_REFERENCE.md`](.pi/QUICK_REFERENCE.md)
3. **Use the validation script**: `.pi/validate.sh`

## Development Workflow

### The Golden Rule

**Every code change must pass all validation checks:**

```bash
just validate
```

This runs:
1. ✅ Type checking (ty)
2. ✅ Linting (ruff check)
3. ✅ Formatting (ruff format)
4. ✅ Testing (pytest)

**Do not commit** code that fails validation.

**Quick commands**:
- `just validate` - Full validation
- `just quick` - Fast validation (skip tests)
- `just validate-fix` - Auto-fix and validate

### Making Changes

1. **Fork and clone** the repository

2. **Create a branch** for your feature/fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make small, focused changes**:
   - One feature/fix per commit
   - Keep changes small (< 100 lines when possible)
   - Write tests first (TDD approach)

4. **Add type hints to all new code**:
   ```python
   # ✅ GOOD
   def process_trace(trace_id: int, config: EvalConfig) -> TraceResult:
       """Process evaluation trace."""
       ...

   # ❌ BAD
   def process_trace(trace_id, config):
       ...
   ```

5. **Write tests for new functionality**:
   ```python
   def test_trace_collection(agent, db_session):
       """Verify trace collection works correctly."""
       result = agent.run("test prompt")
       traces = db_session.query(Trace).all()
       assert len(traces) == 1
       assert traces[0].prompt == "test prompt"
   ```

6. **Validate your changes**:
   ```bash
   just validate
   ```

7. **Fix any issues** and re-validate until all checks pass
   ```bash
   # Auto-fix and validate
   just validate-fix
   ```

8. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Add trace export functionality"
   ```

9. **Push and create a pull request**:
   ```bash
   git push origin feature/your-feature-name
   ```

## Code Standards

### Type Safety

- **All functions must have type hints**
- Use `ty` for type checking (10-100x faster than mypy)
- No `any` types unless absolutely necessary
- Use Pydantic for data validation

### Testing

- Write tests before implementing features (TDD)
- Use pytest fixtures for setup/teardown
- Parametrize tests for multiple inputs
- Aim for >80% code coverage
- Tests must be fast (< 1 second per test)

### Code Style

- Follow PEP 8 (enforced by Ruff)
- Line length: 100 characters
- Use Ruff for formatting (consistent style)
- Add docstrings to public functions
- Use structured logging (structlog, not `print()`)

### Project Phase

**Current Phase**: Phase 1 - Web UI

**In Scope**:
- ✅ Flask routes and templates
- ✅ Datastar reactive UI
- ✅ SQLAlchemy models
- ✅ Evaluation system (web-based)

**Out of Scope** (Phase 2):
- ❌ Textual/TUI features
- ❌ Terminal UI screens
- ❌ TCSS stylesheets

## Pull Request Guidelines

### Before Submitting

- [ ] All validation checks pass (`just validate`)
- [ ] Tests are included for new functionality
- [ ] Documentation is updated (readme.md, docstrings)
- [ ] Commit messages are clear and descriptive
- [ ] No unrelated changes included

### PR Description Template

```markdown
## Description
Brief description of what this PR does.

## Changes
- Added X functionality
- Fixed Y issue
- Updated Z documentation

## Testing
- [ ] Added tests for new functionality
- [ ] All tests pass locally
- [ ] Manual testing completed

## Validation
- [ ] `just validate` passes (or all individual checks below)
- [ ] `just type-check` passes
- [ ] `just lint` passes
- [ ] `just format` passes
- [ ] `just test` passes
```

### Review Process

1. Automated checks run on every PR (CI/CD)
2. Code review by maintainers
3. Validation that all checks pass
4. Merge when approved

## LLM-Assisted Development

If you're an LLM assistant (Claude, ChatGPT, Pi, etc.):

1. **Load the system prompt** at the start of each session:
   - Read [`.pi/SYSTEM.md`](.pi/SYSTEM.md)
   - Follow the workflow defined there

2. **Make incremental changes**:
   - One small change at a time
   - Validate after EACH change
   - Don't proceed if validation fails

3. **Run validation explicitly**:
   ```bash
   just validate
   ```

4. **Report validation results**:
   ```
   Validation results:
   ✅ Type checking passed
   ✅ Linting passed
   ✅ Formatting check passed
   ✅ All tests passed
   
   🎉 All validation checks passed!
   Changes are ready to commit.
   ```

See [`.pi/README.md`](.pi/README.md) for detailed LLM development guidance.

## Common Tasks

### Adding a New Model

1. Create model in `src/software_builder/models/`
2. Add migration (if needed)
3. Write tests in `tests/test_models.py`
4. Validate: `just validate`

### Adding a New Route

1. Create blueprint in `src/software_builder/routes/`
2. Add templates in `templates/`
3. Write route tests in `tests/test_routes.py`
4. Validate: `just validate`

### Adding a New Feature

1. Write test first (TDD): `just test-file tests/test_feature.py`
2. Implement feature
3. Add type hints
4. Validate: `just validate`
5. Update documentation

## Getting Help

- **Documentation**: Read [readme.md](readme.md)
- **System Prompt**: Check [`.pi/SYSTEM.md`](.pi/SYSTEM.md)
- **Quick Reference**: See [`.pi/QUICK_REFERENCE.md`](.pi/QUICK_REFERENCE.md)
- **Issues**: Browse or create [GitHub Issues](../../issues)

## Code of Conduct

- Be respectful and constructive
- Follow the established patterns
- Ask questions when unclear
- Help improve documentation
- Validate before committing

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (TBD).

---

**Remember**: Small steps → Validate → Commit → Repeat

Every contribution should pass: `just validate` ✅

**Quick Reference**:
- `just validate` - Run all checks
- `just quick` - Fast validation (no tests)
- `just --list` - See all commands
