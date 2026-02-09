"""Configuration management with pydantic-settings.

Supports multiple configuration sources (priority order):
1. Environment variables (highest priority)
2. Environment-specific YAML (config/config.{env}.yaml)
3. Base YAML (config/config.yaml)
4. Default values (lowest priority)

Example:
    >>> from software_builder.config import get_settings
    >>> settings = get_settings()
    >>> print(settings.database.url)
    'sqlite:///data/software_builder.db'
"""

import os
from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class DatabaseConfig(BaseSettings):
    """Database configuration settings."""

    url: str = Field(
        default="sqlite:///data/software_builder.db",
        description="SQLAlchemy database URL",
    )
    echo: bool = Field(
        default=False,
        description="Log SQL statements to console",
    )
    pool_size: int = Field(
        default=5,
        ge=1,
        le=100,
        description="Database connection pool size",
    )
    pool_timeout: int = Field(
        default=30,
        ge=1,
        description="Database connection pool timeout in seconds",
    )


class LLMProviderConfig(BaseSettings):
    """Configuration for a single LLM provider."""

    model: str = Field(
        description="Model identifier (e.g., 'gpt-4o-mini', 'claude-3-5-sonnet-20241022')"
    )
    api_key: SecretStr | None = Field(
        default=None,
        description="API key for the provider",
    )
    api_base: str | None = Field(
        default=None,
        description="Custom API base URL (for local models or proxies)",
    )
    temperature: float = Field(
        default=0.7,
        ge=0.0,
        le=2.0,
        description="Sampling temperature",
    )
    max_tokens: int = Field(
        default=2048,
        ge=1,
        le=128000,
        description="Maximum tokens in response",
    )
    timeout: int = Field(
        default=60,
        ge=1,
        description="Request timeout in seconds",
    )


class LLMConfig(BaseSettings):
    """LLM integration settings."""

    default_provider: Literal["openai", "anthropic"] = Field(
        default="openai",
        description="Default LLM provider to use",
    )
    openai: LLMProviderConfig = Field(
        default_factory=lambda: LLMProviderConfig(model="gpt-4o-mini", max_tokens=2048)
    )
    anthropic: LLMProviderConfig = Field(
        default_factory=lambda: LLMProviderConfig(
            model="claude-3-5-sonnet-20241022", max_tokens=4096
        )
    )


class LoggingConfig(BaseSettings):
    """Logging configuration settings."""

    level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = Field(
        default="INFO",
        description="Logging level",
    )
    format: Literal["json", "console", "logfmt"] = Field(
        default="console",
        description="Log output format",
    )
    structured: bool = Field(
        default=True,
        description="Enable structured logging with structlog",
    )
    file: str | None = Field(
        default=None,
        description="Log file path (None = stdout only)",
    )


class AppConfig(BaseSettings):
    """Application settings."""

    name: str = Field(
        default="Software Builder",
        description="Application name",
    )
    version: str = Field(
        default="0.1.0",
        description="Application version",
    )
    environment: Literal["development", "production", "test"] = Field(
        default="development",
        description="Runtime environment",
    )
    debug: bool = Field(
        default=False,
        description="Enable debug mode",
    )
    secret_key: SecretStr = Field(
        default=SecretStr("dev-secret-key-change-in-production"),
        description="Secret key for sessions and security",
    )


class WebConfig(BaseSettings):
    """Web server configuration."""

    host: str = Field(
        default="127.0.0.1",
        description="Server bind address",
    )
    port: int = Field(
        default=5000,
        ge=1,
        le=65535,
        description="Server port",
    )
    workers: int = Field(
        default=1,
        ge=1,
        le=32,
        description="Number of worker processes",
    )


class AgentConfig(BaseSettings):
    """Agent behavior configuration."""

    max_iterations: int = Field(
        default=10,
        ge=1,
        le=100,
        description="Maximum agent iterations per task",
    )
    timeout: int = Field(
        default=300,
        ge=1,
        description="Agent task timeout in seconds",
    )
    tools: list[str] = Field(
        default_factory=lambda: ["file_operations", "code_execution"],
        description="Enabled agent tools",
    )


class EvalConfig(BaseSettings):
    """Evaluation system configuration."""

    trace_collection: bool = Field(
        default=True,
        description="Enable automatic trace collection",
    )
    auto_review_threshold: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="Confidence threshold for auto-review",
    )
    dataset_dir: str = Field(
        default="tests/evals/datasets",
        description="Directory for evaluation datasets",
    )


class PromptsConfig(BaseSettings):
    """Prompt management configuration."""

    registry_path: str = Field(
        default="prompts/prompt_registry.yaml",
        description="Path to prompt registry file",
    )
    default_version: str = Field(
        default="current",
        description="Default prompt version to use",
    )


class Settings(BaseSettings):
    """Complete application settings.

    Loads configuration from:
    1. config/config.yaml (base configuration)
    2. config/config.{environment}.yaml (environment-specific overrides)
    3. Environment variables with SB_ prefix

    Environment variables use double underscore for nesting:
        SB_DATABASE__URL=sqlite:///custom.db
        SB_LLM__DEFAULT_PROVIDER=anthropic
        SB_LLM__OPENAI__API_KEY=sk-...
    """

    model_config = SettingsConfigDict(
        env_prefix="SB_",
        env_nested_delimiter="__",
        case_sensitive=False,
        extra="ignore",
    )

    app: AppConfig = Field(default_factory=AppConfig)
    web: WebConfig = Field(default_factory=WebConfig)
    database: DatabaseConfig = Field(default_factory=DatabaseConfig)
    llm: LLMConfig = Field(default_factory=LLMConfig)
    logging: LoggingConfig = Field(default_factory=LoggingConfig)
    agent: AgentConfig = Field(default_factory=AgentConfig)
    eval: EvalConfig = Field(default_factory=EvalConfig)
    prompts: PromptsConfig = Field(default_factory=PromptsConfig)


# Global settings instance
_settings: Settings | None = None


def get_settings(reload: bool = False) -> Settings:
    """Get application settings (singleton pattern).

    Args:
        reload: Force reload settings from files/environment

    Returns:
        Settings instance

    Example:
        >>> settings = get_settings()
        >>> print(settings.database.url)
    """
    global _settings

    if _settings is None or reload:
        _settings = load_settings()

    return _settings


def load_settings() -> Settings:
    """Load settings from YAML files and environment variables.

    Loads in order:
    1. config/config.yaml (base)
    2. config/config.{environment}.yaml (environment-specific)
    3. Environment variables (SB_*)

    Returns:
        Settings instance with merged configuration
    """
    # Determine environment
    environment = os.environ.get("SB_APP__ENVIRONMENT", "development")

    # Load base config
    config_data: dict = {}
    base_config = Path("config/config.yaml")
    if base_config.exists():
        import yaml

        with open(base_config) as f:
            base_data = yaml.safe_load(f) or {}
            config_data.update(base_data)

    # Load environment-specific config
    env_config = Path(f"config/config.{environment}.yaml")
    if env_config.exists():
        import yaml

        with open(env_config) as f:
            env_data = yaml.safe_load(f) or {}
            # Deep merge environment config
            _deep_merge(config_data, env_data)

    # Create settings (environment variables will override)
    return Settings(**config_data)


def _deep_merge(base: dict, override: dict) -> None:
    """Deep merge override dict into base dict (in-place).

    Args:
        base: Base dictionary to merge into
        override: Dictionary with values to override
    """
    for key, value in override.items():
        if key in base and isinstance(base[key], dict) and isinstance(value, dict):
            _deep_merge(base[key], value)
        else:
            base[key] = value


def reload_settings() -> Settings:
    """Force reload settings from files and environment.

    Returns:
        Fresh Settings instance
    """
    return get_settings(reload=True)
