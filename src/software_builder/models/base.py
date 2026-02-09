"""Base SQLAlchemy configuration and utilities."""

from datetime import UTC, datetime
from typing import Any

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy models."""

    pass


def utcnow() -> datetime:
    """Return current UTC datetime with timezone info."""
    return datetime.now(UTC)


# Database connection (will be configured via settings)
engine: Any = None
SessionLocal: Any = None


def init_db(database_url: str, echo: bool = False) -> None:
    """Initialize database connection.

    Note: This only creates the engine and session factory.
    Use Alembic migrations to create/update database schema:
        alembic upgrade head

    Args:
        database_url: SQLAlchemy database URL
        echo: Whether to log SQL statements
    """
    global engine, SessionLocal

    engine = create_engine(database_url, echo=echo)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_session() -> Session:
    """Get a new database session.

    Returns:
        SQLAlchemy session

    Raises:
        RuntimeError: If database is not initialized
    """
    if SessionLocal is None:
        raise RuntimeError("Database not initialized. Call init_db() first.")

    return SessionLocal()
