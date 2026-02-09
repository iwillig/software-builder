"""Prompt versioning models - SystemPrompt and ToolPrompt."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import JSON, Boolean, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, utcnow

if TYPE_CHECKING:
    from .session import Session


class SystemPrompt(Base):
    """Versioned system prompt for the coding agent.

    Each version is stored separately with an is_active flag to mark
    the current active version.
    """

    __tablename__ = "system_prompts"

    id: Mapped[int] = mapped_column(primary_key=True)
    version: Mapped[str] = mapped_column(String(50), nullable=False, unique=True, index=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False, index=True)

    # Relationships
    sessions: Mapped[list["Session"]] = relationship(
        "Session", back_populates="system_prompt", foreign_keys="Session.system_prompt_id"
    )

    def __repr__(self) -> str:
        return (
            f"<SystemPrompt(version={self.version!r}, name={self.name!r}, "
            f"is_active={self.is_active})>"
        )


class ToolPrompt(Base):
    """Versioned tool definitions for the coding agent.

    Stores tool definitions as JSON array following OpenAI's tool format.
    """

    __tablename__ = "tool_prompts"

    id: Mapped[int] = mapped_column(primary_key=True)
    version: Mapped[str] = mapped_column(String(50), nullable=False, unique=True, index=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    tools_json: Mapped[dict] = mapped_column(JSON, nullable=False)  # Array of tool definitions
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False, index=True)

    # Relationships
    sessions: Mapped[list["Session"]] = relationship(
        "Session", back_populates="tool_prompt", foreign_keys="Session.tool_prompt_id"
    )

    def __repr__(self) -> str:
        tool_count = len(self.tools_json) if isinstance(self.tools_json, list) else 0
        return (
            f"<ToolPrompt(version={self.version!r}, name={self.name!r}, "
            f"tools={tool_count}, is_active={self.is_active})>"
        )


# Indexes
Index("idx_system_prompt_active", SystemPrompt.is_active)
Index("idx_tool_prompt_active", ToolPrompt.is_active)
