# Software Builder - Configuration Guide

Software Builder uses **pydantic-settings** for type-safe, validated configuration management with support for YAML files and environment variables.

## Features

✅ **Type-Safe**: Full validation with Pydantic models  
✅ **Multiple Sources**: YAML files + environment variables  
✅ **Environment-Specific**: Different configs for dev/prod/test  
✅ **Nested Configuration**: Organized settings by domain  
✅ **Secret Management**: Proper handling of API keys  
✅ **Hot Reload**: Reload configuration at runtime  
✅ **IDE Support**: Full autocomplete with type hints  

---

## Quick Start

### 1. Basic Usage

```python
from software_builder.config import get_settings

# Get settings (singleton)
settings = get_settings()

# Access configuration
print(settings.database.url)
print(settings.llm.default_provider)
print(settings.web.port)
```

### 2. Environment Variables

Create a `.env` file (copy from `.env.example`):

```bash
cp .env.example .env
```

Edit `.env` with your values:

```bash
# Required: Set your API keys
SB_LLM__OPENAI__API_KEY=sk-your-key-here
SB_LLM__ANTHROPIC__API_KEY=sk-ant-your-key-here

# Optional: Override other settings
SB_WEB__PORT=8080
SB_DATABASE__URL=sqlite:///custom.db
```

### 3. Run Application

```bash
# Load .env automatically with python-dotenv
just serve

# Or export environment variables
export SB_LLM__OPENAI__API_KEY=sk-...
just serve
```

---

## Configuration Sources

Settings are loaded in **priority order** (highest to lowest):

1. **Environment Variables** (highest priority)
   - Prefix: `SB_`
   - Nested with `__` (double underscore)
   - Example: `SB_DATABASE__URL=sqlite:///custom.db`

2. **Environment-Specific YAML**
   - File: `config/config.{environment}.yaml`
   - Example: `config/config.development.yaml`

3. **Base YAML**
   - File: `config/config.yaml`
   - Shared defaults across all environments

4. **Code Defaults** (lowest priority)
   - Defined in `src/software_builder/config.py`

---

## Configuration Structure

### Application Settings (`app`)

```yaml
app:
  name: "Software Builder"
  version: "0.1.0"
  environment: "development"  # development, production, test
  debug: false
  secret_key: "change-in-production"
```

**Environment Variables**:
```bash
SB_APP__NAME="My App"
SB_APP__ENVIRONMENT=production
SB_APP__DEBUG=false
SB_APP__SECRET_KEY=secure-key-here
```

**Python Access**:
```python
settings.app.name
settings.app.environment
settings.app.debug
settings.app.secret_key.get_secret_value()  # SecretStr
```

### Web Server Settings (`web`)

```yaml
web:
  host: "127.0.0.1"
  port: 5000
  workers: 1
```

**Environment Variables**:
```bash
SB_WEB__HOST=0.0.0.0
SB_WEB__PORT=8080
SB_WEB__WORKERS=4
```

**Constraints**:
- `port`: 1-65535
- `workers`: 1-32

### Database Settings (`database`)

```yaml
database:
  url: "sqlite:///data/software_builder.db"
  echo: false
  pool_size: 5
  pool_timeout: 30
```

**Environment Variables**:
```bash
SB_DATABASE__URL=sqlite:///custom.db
SB_DATABASE__ECHO=true
SB_DATABASE__POOL_SIZE=10
```

**Constraints**:
- `pool_size`: 1-100
- `pool_timeout`: >= 1 second

### LLM Provider Settings (`llm`)

```yaml
llm:
  default_provider: "openai"  # or "anthropic"
  
  openai:
    model: "gpt-4o-mini"
    api_key: null  # Set via environment variable
    api_base: null  # Optional: for proxies
    temperature: 0.7
    max_tokens: 2048
    timeout: 60
  
  anthropic:
    model: "claude-3-5-sonnet-20241022"
    api_key: null  # Set via environment variable
    temperature: 0.7
    max_tokens: 4096
    timeout: 60
```

**Environment Variables**:
```bash
# Provider selection
SB_LLM__DEFAULT_PROVIDER=anthropic

# OpenAI
SB_LLM__OPENAI__API_KEY=sk-...
SB_LLM__OPENAI__MODEL=gpt-4o
SB_LLM__OPENAI__TEMPERATURE=0.5
SB_LLM__OPENAI__MAX_TOKENS=4096

# Anthropic
SB_LLM__ANTHROPIC__API_KEY=sk-ant-...
SB_LLM__ANTHROPIC__MODEL=claude-3-5-sonnet-20241022
SB_LLM__ANTHROPIC__TEMPERATURE=0.7
```

**Constraints**:
- `temperature`: 0.0-2.0
- `max_tokens`: 1-128000
- `timeout`: >= 1 second

**Python Access**:
```python
# Get current provider
provider = settings.llm.default_provider  # "openai" or "anthropic"

# Access provider config
openai_config = settings.llm.openai
api_key = openai_config.api_key.get_secret_value()  # SecretStr

# Use in code
if settings.llm.default_provider == "openai":
    config = settings.llm.openai
else:
    config = settings.llm.anthropic

print(f"Using {config.model}")
```

### Logging Settings (`logging`)

```yaml
logging:
  level: "INFO"  # DEBUG, INFO, WARNING, ERROR, CRITICAL
  format: "console"  # console, json, logfmt
  structured: true
  file: null  # Optional: path to log file
```

**Environment Variables**:
```bash
SB_LOGGING__LEVEL=DEBUG
SB_LOGGING__FORMAT=json
SB_LOGGING__FILE=logs/app.log
```

### Agent Settings (`agent`)

```yaml
agent:
  max_iterations: 10
  timeout: 300
  tools:
    - "file_operations"
    - "code_execution"
```

**Environment Variables**:
```bash
SB_AGENT__MAX_ITERATIONS=20
SB_AGENT__TIMEOUT=600
```

**Constraints**:
- `max_iterations`: 1-100
- `timeout`: >= 1 second

### Evaluation Settings (`eval`)

```yaml
eval:
  trace_collection: true
  auto_review_threshold: 0.7
  dataset_dir: "tests/evals/datasets"
```

**Environment Variables**:
```bash
SB_EVAL__TRACE_COLLECTION=false
SB_EVAL__AUTO_REVIEW_THRESHOLD=0.8
SB_EVAL__DATASET_DIR=custom/path
```

**Constraints**:
- `auto_review_threshold`: 0.0-1.0

### Prompts Settings (`prompts`)

```yaml
prompts:
  registry_path: "prompts/prompt_registry.yaml"
  default_version: "current"
```

**Environment Variables**:
```bash
SB_PROMPTS__REGISTRY_PATH=custom/prompts.yaml
SB_PROMPTS__DEFAULT_VERSION=v2
```

---

## Environment-Specific Configuration

Software Builder supports different configurations per environment:

### Development (`config/config.development.yaml`)

```yaml
app:
  environment: "development"
  debug: true

database:
  echo: true  # Log SQL queries

logging:
  level: "DEBUG"
  format: "console"

agent:
  max_iterations: 5  # Shorter for testing
```

**Usage**:
```bash
# Default environment is development
just serve

# Or explicitly set
export SB_APP__ENVIRONMENT=development
just serve
```

### Production (`config/config.production.yaml`)

```yaml
app:
  environment: "production"
  debug: false

web:
  host: "0.0.0.0"  # Bind to all interfaces
  workers: 4

database:
  pool_size: 20

logging:
  level: "INFO"
  format: "json"  # Structured logs
  file: "logs/software_builder.log"

eval:
  auto_review_threshold: 0.8  # Higher threshold
```

**Usage**:
```bash
export SB_APP__ENVIRONMENT=production
export SB_APP__SECRET_KEY=secure-production-key
just serve
```

### Test (`config/config.test.yaml`)

```yaml
app:
  environment: "test"

database:
  url: "sqlite:///:memory:"  # In-memory for tests

logging:
  level: "WARNING"  # Less noise
  structured: false

agent:
  max_iterations: 3  # Fast tests
  timeout: 10

eval:
  trace_collection: false  # Disable during tests
```

**Usage**:
```bash
# Tests automatically use test config
just test
```

---

## Advanced Usage

### Reload Configuration

```python
from software_builder.config import reload_settings

# Force reload from files and environment
settings = reload_settings()
```

### Access Nested Settings

```python
settings = get_settings()

# Direct access
db_url = settings.database.url
model = settings.llm.openai.model

# Dynamic provider selection
provider_name = settings.llm.default_provider
provider_config = getattr(settings.llm, provider_name)
print(f"Using {provider_config.model}")
```

### Secret Key Handling

```python
# SecretStr prevents accidental logging
secret = settings.app.secret_key
print(secret)  # Output: SecretStr('**********')

# Explicitly get value when needed
actual_value = secret.get_secret_value()
print(actual_value)  # Output: actual-secret-key
```

### Validation Errors

```python
from pydantic import ValidationError
from software_builder.config import Settings

try:
    settings = Settings(web={"port": 99999})  # Invalid
except ValidationError as e:
    print(e.errors())
    # [{'loc': ('web', 'port'), 'msg': '...', 'type': 'less_than_equal'}]
```

---

## Environment Variable Examples

### Complete .env File

```bash
# ============================================
# Software Builder Configuration
# ============================================

# Application
SB_APP__ENVIRONMENT=development
SB_APP__DEBUG=true
SB_APP__SECRET_KEY=dev-secret-key-change-me

# Web Server
SB_WEB__HOST=127.0.0.1
SB_WEB__PORT=5000
SB_WEB__WORKERS=1

# Database
SB_DATABASE__URL=sqlite:///data/software_builder.db
SB_DATABASE__ECHO=false
SB_DATABASE__POOL_SIZE=5

# LLM - OpenAI
SB_LLM__DEFAULT_PROVIDER=openai
SB_LLM__OPENAI__API_KEY=sk-your-openai-key-here
SB_LLM__OPENAI__MODEL=gpt-4o-mini
SB_LLM__OPENAI__TEMPERATURE=0.7
SB_LLM__OPENAI__MAX_TOKENS=2048

# LLM - Anthropic
SB_LLM__ANTHROPIC__API_KEY=sk-ant-your-anthropic-key-here
SB_LLM__ANTHROPIC__MODEL=claude-3-5-sonnet-20241022
SB_LLM__ANTHROPIC__TEMPERATURE=0.7
SB_LLM__ANTHROPIC__MAX_TOKENS=4096

# Logging
SB_LOGGING__LEVEL=INFO
SB_LOGGING__FORMAT=console
SB_LOGGING__STRUCTURED=true

# Agent
SB_AGENT__MAX_ITERATIONS=10
SB_AGENT__TIMEOUT=300

# Evaluation
SB_EVAL__TRACE_COLLECTION=true
SB_EVAL__AUTO_REVIEW_THRESHOLD=0.7

# Prompts
SB_PROMPTS__REGISTRY_PATH=prompts/prompt_registry.yaml
SB_PROMPTS__DEFAULT_VERSION=current
```

### Production Environment

```bash
# Production environment variables
export SB_APP__ENVIRONMENT=production
export SB_APP__DEBUG=false
export SB_APP__SECRET_KEY=$(openssl rand -hex 32)

export SB_WEB__HOST=0.0.0.0
export SB_WEB__PORT=5000
export SB_WEB__WORKERS=4

export SB_DATABASE__URL=sqlite:///data/software_builder.db
export SB_DATABASE__POOL_SIZE=20

export SB_LLM__OPENAI__API_KEY=sk-prod-key-here
export SB_LLM__ANTHROPIC__API_KEY=sk-ant-prod-key-here

export SB_LOGGING__LEVEL=INFO
export SB_LOGGING__FORMAT=json
export SB_LOGGING__FILE=logs/software_builder.log
```

---

## Testing Configuration

### Unit Tests

```python
def test_with_custom_config():
    """Test with custom configuration."""
    from software_builder.config import Settings
    
    settings = Settings(
        database={"url": "sqlite:///:memory:"},
        llm={"default_provider": "anthropic"}
    )
    
    assert settings.database.url == "sqlite:///:memory:"
    assert settings.llm.default_provider == "anthropic"
```

### Integration Tests

```python
def test_with_environment_variables(monkeypatch):
    """Test with environment variable overrides."""
    monkeypatch.setenv("SB_DATABASE__URL", "sqlite:///test.db")
    monkeypatch.setenv("SB_WEB__PORT", "8080")
    
    from software_builder.config import Settings
    settings = Settings()
    
    assert settings.database.url == "sqlite:///test.db"
    assert settings.web.port == 8080
```

---

## Troubleshooting

### Issue: Environment variables not being picked up

**Solution**: Check naming and nesting:
```bash
# ❌ Wrong
SB_DATABASE_URL=...  # Single underscore

# ✅ Correct
SB_DATABASE__URL=...  # Double underscore for nesting
```

### Issue: YAML file not found

**Solution**: Ensure `config/config.yaml` exists:
```bash
ls config/config.yaml
```

If missing, the application will use code defaults.

### Issue: Validation errors on startup

**Solution**: Check the error message:
```python
pydantic_core._pydantic_core.ValidationError: 2 validation errors for Settings
llm.openai.temperature
  Input should be less than or equal to 2.0 [type=less_than_equal, ...]
```

Fix the invalid value in YAML or environment variable.

### Issue: Secret keys exposed in logs

**Solution**: Use `SecretStr.get_secret_value()`:
```python
# ❌ Don't do this
print(settings.app.secret_key)  # Logs: SecretStr('**********')

# ✅ Do this when needed
actual_key = settings.app.secret_key.get_secret_value()
```

---

## Best Practices

### 1. Never Commit Secrets

```bash
# ✅ Committed
config/config.yaml
config/config.development.yaml
config/config.production.yaml
.env.example

# ❌ Never commit
.env
.env.local
.env.production
```

### 2. Use Environment Variables for Secrets

```yaml
# config/config.yaml
llm:
  openai:
    api_key: null  # Don't set here

# Set via environment
SB_LLM__OPENAI__API_KEY=sk-...
```

### 3. Validate Configuration at Startup

```python
from software_builder.config import get_settings

def main():
    # Load and validate configuration
    settings = get_settings()
    
    # Check required settings
    if not settings.llm.openai.api_key:
        raise ValueError("OpenAI API key not set!")
    
    # Continue with application...
```

### 4. Use Type Hints

```python
from software_builder.config import Settings

def initialize_llm(settings: Settings) -> LLMClient:
    """Initialize LLM with settings."""
    # Full IDE autocomplete!
    provider = settings.llm.default_provider
    config = settings.llm.openai if provider == "openai" else settings.llm.anthropic
    
    return LLMClient(
        model=config.model,
        api_key=config.api_key.get_secret_value(),
        temperature=config.temperature,
    )
```

### 5. Document Custom Settings

If you add new settings, update this guide and `.env.example`.

---

## See Also

- [pydantic-settings documentation](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)
- [Pydantic validation](https://docs.pydantic.dev/latest/concepts/validators/)
- [python-dotenv documentation](https://github.com/theskumar/python-dotenv)

---

**Questions?** Check the [tests/test_config.py](tests/test_config.py) file for usage examples.
