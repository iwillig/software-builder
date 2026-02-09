"""Tests for configuration system."""

import os
from pathlib import Path

import pytest
from pydantic import ValidationError

from software_builder.config import (
    Settings,
    get_settings,
    load_settings,
    reload_settings,
)


def test_default_settings() -> None:
    """Test loading default settings without config files."""
    settings = Settings()

    assert settings.app.name == "Software Builder"
    assert settings.app.version == "0.1.0"
    assert settings.database.url == "sqlite:///data/software_builder.db"
    assert settings.llm.default_provider == "openai"
    assert settings.web.port == 5000


def test_database_config_validation() -> None:
    """Test database configuration validation."""
    # Valid config
    settings = Settings(database={"url": "sqlite:///test.db", "pool_size": 10})
    assert settings.database.pool_size == 10

    # Invalid pool_size (too large)
    with pytest.raises(ValidationError) as exc_info:
        Settings(database={"pool_size": 200})
    assert "pool_size" in str(exc_info.value)


def test_llm_config() -> None:
    """Test LLM provider configuration."""
    settings = Settings()

    # Check OpenAI defaults
    assert settings.llm.openai.model == "gpt-4o-mini"
    assert settings.llm.openai.temperature == 0.7
    assert settings.llm.openai.max_tokens == 2048

    # Check Anthropic defaults
    assert settings.llm.anthropic.model == "claude-3-5-sonnet-20241022"
    assert settings.llm.anthropic.max_tokens == 4096


def test_llm_config_with_api_keys() -> None:
    """Test LLM configuration with API keys."""
    settings = Settings(
        llm={
            "openai": {"model": "gpt-4o-mini", "api_key": "sk-test-key-123"},
            "anthropic": {
                "model": "claude-3-5-sonnet-20241022",
                "api_key": "sk-ant-test-key-456",
            },
        }
    )

    # API keys should be SecretStr
    assert settings.llm.openai.api_key is not None
    assert settings.llm.openai.api_key.get_secret_value() == "sk-test-key-123"
    assert settings.llm.anthropic.api_key is not None
    assert settings.llm.anthropic.api_key.get_secret_value() == "sk-ant-test-key-456"


def test_logging_config() -> None:
    """Test logging configuration."""
    settings = Settings()

    assert settings.logging.level == "INFO"
    assert settings.logging.format == "console"
    assert settings.logging.structured is True
    assert settings.logging.file is None


def test_agent_config() -> None:
    """Test agent configuration."""
    settings = Settings()

    assert settings.agent.max_iterations == 10
    assert settings.agent.timeout == 300
    assert "file_operations" in settings.agent.tools
    assert "code_execution" in settings.agent.tools


def test_eval_config() -> None:
    """Test evaluation configuration."""
    settings = Settings()

    assert settings.eval.trace_collection is True
    assert settings.eval.auto_review_threshold == 0.7
    assert settings.eval.dataset_dir == "tests/evals/datasets"


def test_environment_variables_override(monkeypatch: pytest.MonkeyPatch) -> None:
    """Test that environment variables override defaults."""
    # Set environment variables
    monkeypatch.setenv("SB_APP__NAME", "Test App")
    monkeypatch.setenv("SB_DATABASE__URL", "sqlite:///custom.db")
    monkeypatch.setenv("SB_WEB__PORT", "8080")
    monkeypatch.setenv("SB_LLM__DEFAULT_PROVIDER", "anthropic")

    settings = Settings()

    assert settings.app.name == "Test App"
    assert settings.database.url == "sqlite:///custom.db"
    assert settings.web.port == 8080
    assert settings.llm.default_provider == "anthropic"


def test_nested_environment_variables(monkeypatch: pytest.MonkeyPatch) -> None:
    """Test nested environment variable overrides."""
    monkeypatch.setenv("SB_LLM__OPENAI__MODEL", "gpt-4o")
    monkeypatch.setenv("SB_LLM__OPENAI__TEMPERATURE", "0.5")
    monkeypatch.setenv("SB_LLM__OPENAI__MAX_TOKENS", "4096")

    settings = Settings()

    assert settings.llm.openai.model == "gpt-4o"
    assert settings.llm.openai.temperature == 0.5
    assert settings.llm.openai.max_tokens == 4096


def test_get_settings_singleton() -> None:
    """Test that get_settings returns singleton."""
    settings1 = get_settings()
    settings2 = get_settings()

    assert settings1 is settings2


def test_reload_settings(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    """Test reloading settings."""
    # Change to temp directory so no config files are loaded
    original_cwd = Path.cwd()
    os.chdir(tmp_path)

    try:
        settings1 = get_settings()
        initial_name = settings1.app.name

        # Change environment variable
        monkeypatch.setenv("SB_APP__NAME", "Reloaded App")

        # Get settings without reload - should be same
        settings2 = get_settings()
        assert settings2.app.name == initial_name

        # Reload settings - should pick up new value
        settings3 = reload_settings()
        assert settings3.app.name == "Reloaded App"

    finally:
        os.chdir(original_cwd)


def test_yaml_config_loading(tmp_path: Path) -> None:
    """Test loading configuration from YAML files."""
    # Create temporary config directory
    config_dir = tmp_path / "config"
    config_dir.mkdir()

    # Write base config
    base_config = config_dir / "config.yaml"
    base_config.write_text(
        """
app:
  name: "YAML Test App"
  version: "1.0.0"

database:
  url: "sqlite:///yaml_test.db"
  pool_size: 20

logging:
  level: "DEBUG"
"""
    )

    # Write development config
    dev_config = config_dir / "config.development.yaml"
    dev_config.write_text(
        """
database:
  echo: true

logging:
  format: "json"
"""
    )

    # Change to temp directory
    original_cwd = Path.cwd()
    os.chdir(tmp_path)

    try:
        # Load settings (should merge base + development)
        settings = load_settings()

        # Check base config values
        assert settings.app.name == "YAML Test App"
        assert settings.app.version == "1.0.0"
        assert settings.database.url == "sqlite:///yaml_test.db"
        assert settings.database.pool_size == 20
        assert settings.logging.level == "DEBUG"

        # Check development overrides
        assert settings.database.echo is True
        assert settings.logging.format == "json"

    finally:
        os.chdir(original_cwd)


def test_environment_specific_config(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """Test loading environment-specific configuration."""
    # Create config directory
    config_dir = tmp_path / "config"
    config_dir.mkdir()

    # Base config
    base_config = config_dir / "config.yaml"
    base_config.write_text(
        """
app:
  debug: false
database:
  pool_size: 5
"""
    )

    # Production config
    prod_config = config_dir / "config.production.yaml"
    prod_config.write_text(
        """
app:
  debug: false
database:
  pool_size: 20
logging:
  format: "json"
"""
    )

    # Set environment to production
    monkeypatch.setenv("SB_APP__ENVIRONMENT", "production")

    original_cwd = Path.cwd()
    os.chdir(tmp_path)

    try:
        settings = load_settings()

        # Production values should override base
        assert settings.database.pool_size == 20
        assert settings.logging.format == "json"

    finally:
        os.chdir(original_cwd)


def test_secret_key_handling() -> None:
    """Test that secret keys are properly handled as SecretStr."""
    settings = Settings(app={"secret_key": "my-super-secret-key"})

    # Should be SecretStr
    assert str(settings.app.secret_key) != "my-super-secret-key"
    assert "***" in str(settings.app.secret_key) or "SecretStr" in str(settings.app.secret_key)

    # But can be retrieved
    assert settings.app.secret_key.get_secret_value() == "my-super-secret-key"


def test_validation_constraints() -> None:
    """Test that validation constraints are enforced."""
    # Valid temperature
    settings = Settings(llm={"openai": {"model": "gpt-4o-mini", "temperature": 1.0}})
    assert settings.llm.openai.temperature == 1.0

    # Invalid temperature (too high)
    with pytest.raises(ValidationError) as exc_info:
        Settings(llm={"openai": {"model": "gpt-4o-mini", "temperature": 3.0}})
    assert "temperature" in str(exc_info.value).lower()

    # Invalid port (too high)
    with pytest.raises(ValidationError) as exc_info:
        Settings(web={"port": 99999})
    assert "port" in str(exc_info.value).lower()


def test_app_environment_values() -> None:
    """Test that app.environment only accepts valid values."""
    # Valid environments
    for env in ["development", "production", "test"]:
        settings = Settings(app={"environment": env})
        assert settings.app.environment == env

    # Invalid environment
    with pytest.raises(ValidationError) as exc_info:
        Settings(app={"environment": "invalid"})
    assert "environment" in str(exc_info.value).lower()


def test_web_config_ranges() -> None:
    """Test web configuration value ranges."""
    # Valid port
    settings = Settings(web={"port": 8080})
    assert settings.web.port == 8080

    # Invalid port (too low)
    with pytest.raises(ValidationError):
        Settings(web={"port": 0})

    # Valid workers
    settings = Settings(web={"workers": 4})
    assert settings.web.workers == 4

    # Invalid workers (too high)
    with pytest.raises(ValidationError):
        Settings(web={"workers": 100})
