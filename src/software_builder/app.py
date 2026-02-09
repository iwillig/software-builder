"""Flask application factory and configuration."""

import structlog
from flask import Flask, render_template

from .config import Settings, get_settings
from .models import init_db


def create_app(settings: Settings | None = None) -> Flask:
    """Create and configure Flask application.

    Args:
        settings: Optional settings instance (defaults to get_settings())

    Returns:
        Configured Flask application

    Example:
        >>> app = create_app()
        >>> app.run()
    """
    if settings is None:
        settings = get_settings()

    # Create Flask app
    app = Flask(
        __name__,
        template_folder="../../templates",
        static_folder="../../static",
    )

    # Configure Flask from settings
    app.config.update(
        SECRET_KEY=settings.app.secret_key.get_secret_value(),
        DEBUG=settings.app.debug,
        ENV=settings.app.environment,
    )

    # Configure structured logging
    configure_logging(app, settings)

    # Initialize database
    init_db(
        database_url=settings.database.url,
        echo=settings.database.echo,
    )

    # For in-memory databases (testing), create tables directly
    if settings.database.url == "sqlite:///:memory:":
        from .models import base

        base.Base.metadata.create_all(bind=base.engine)

    # Register blueprints
    register_blueprints(app)

    # Register error handlers
    register_error_handlers(app)

    # Log application startup
    logger = structlog.get_logger()
    logger.info(
        "flask_app_created",
        environment=settings.app.environment,
        debug=settings.app.debug,
        database_url=settings.database.url,
    )

    return app


def configure_logging(app: Flask, settings: Settings) -> None:
    """Configure structured logging with structlog.

    Args:
        app: Flask application
        settings: Application settings
    """
    import logging
    import sys

    # Configure structlog
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.filter_by_level,
            structlog.stdlib.add_logger_name,
            structlog.stdlib.add_log_level,
            structlog.stdlib.PositionalArgumentsFormatter(),
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.processors.UnicodeDecoder(),
            (
                structlog.processors.JSONRenderer()
                if settings.logging.format == "json"
                else structlog.dev.ConsoleRenderer()
            ),
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )

    # Configure standard logging
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=getattr(logging, settings.logging.level),
    )

    # Disable Flask's default request logging
    log = logging.getLogger("werkzeug")
    log.setLevel(logging.ERROR)


def register_blueprints(app: Flask) -> None:
    """Register Flask blueprints.

    Args:
        app: Flask application
    """
    from .routes import main_bp, sessions_bp, system_prompts_bp

    app.register_blueprint(main_bp)
    app.register_blueprint(sessions_bp)
    app.register_blueprint(system_prompts_bp)


def register_error_handlers(app: Flask) -> None:
    """Register error handlers.

    Args:
        app: Flask application
    """
    logger = structlog.get_logger()

    @app.errorhandler(404)
    def not_found(error):
        """Handle 404 errors."""
        logger.warning("page_not_found", path=error)
        return render_template("errors/404.html"), 404

    @app.errorhandler(500)
    def internal_error(error):
        """Handle 500 errors."""
        logger.error("internal_server_error", error=str(error))
        return render_template("errors/500.html"), 500
