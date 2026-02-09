"""Command-line interface for Software Builder."""

import click

from .app import create_app
from .config import get_settings


@click.group()
@click.version_option(version="0.1.0")
def main() -> None:
    """Software Builder - Local LLM coding agent with built-in evaluation."""
    pass


@main.command()
@click.option("--seed", is_flag=True, help="Seed database with initial data")
def init(seed: bool) -> None:
    """Initialize the database."""
    click.echo("Initializing database...")
    from .models import init_db

    settings = get_settings()
    init_db(settings.database.url, echo=settings.database.echo)
    click.echo("✅ Database initialized successfully!")

    if seed:
        click.echo("Seeding database with initial data...")
        from .utils import seed_prompts

        seed_prompts()
        click.echo("✅ Seed data created!")


@main.command()
def seed() -> None:
    """Seed database with initial data (prompts, etc.)."""
    click.echo("Seeding database...")
    from .utils import seed_prompts

    seed_prompts()
    click.echo("✅ Seed data created successfully!")


@main.command()
@click.option("--host", default=None, help="Host to bind to (default: from config)")
@click.option("--port", default=None, type=int, help="Port to bind to (default: from config)")
@click.option("--debug", is_flag=True, help="Enable debug mode")
def serve(host: str | None, port: int | None, debug: bool) -> None:
    """Start the web UI server."""
    settings = get_settings()

    # Use CLI args if provided, otherwise use config
    host = host or settings.web.host
    port = port or settings.web.port
    debug = debug or settings.app.debug

    click.echo(f"🌐 Starting {settings.app.name} v{settings.app.version}")
    click.echo(f"📍 Environment: {settings.app.environment}")
    click.echo(f"🔧 Debug mode: {debug}")
    click.echo(f"🌍 Listening on http://{host}:{port}")
    click.echo("")
    click.echo("Press CTRL+C to stop")

    app = create_app(settings)
    app.run(host=host, port=port, debug=debug)


if __name__ == "__main__":
    main()
