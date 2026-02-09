"""Session, Message, and ToolCall models for agent conversations."""

from datetime import datetime
from typing import TYPE_CHECKING, Optional

from sqlalchemy import (
    JSON,
    Enum,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, utcnow

if TYPE_CHECKING:
    from .document import MessageDocument
    from .prompt import SystemPrompt, ToolPrompt
    from .trace import Trace


class Session(Base):
    """A coding conversation session.

    Links to specific versions of system and tool prompts to enable
    evaluation and comparison across prompt versions.
    """

    __tablename__ = "sessions"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    system_prompt_id: Mapped[int] = mapped_column(ForeignKey("system_prompts.id"), nullable=False)
    tool_prompt_id: Mapped[int] = mapped_column(ForeignKey("tool_prompts.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow, nullable=False)
    status: Mapped[str] = mapped_column(
        Enum("active", "completed", "failed", name="session_status"),
        default="active",
        nullable=False,
        index=True,
    )

    # Relationships
    system_prompt: Mapped["SystemPrompt"] = relationship(
        "SystemPrompt", back_populates="sessions", foreign_keys=[system_prompt_id]
    )
    tool_prompt: Mapped["ToolPrompt"] = relationship(
        "ToolPrompt", back_populates="sessions", foreign_keys=[tool_prompt_id]
    )
    messages: Mapped[list["Message"]] = relationship(
        "Message", back_populates="session", cascade="all, delete-orphan"
    )
    trace: Mapped[Optional["Trace"]] = relationship(
        "Trace", back_populates="session", uselist=False
    )

    def __repr__(self) -> str:
        return f"<Session(id={self.id}, title={self.title!r}, status={self.status})>"


class Message(Base):
    """A message in a conversation session (OpenAI format).

    Follows OpenAI's message format with role, content, tool_calls, etc.
    Stores tool_calls as JSON for flexibility.
    """

    __tablename__ = "messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    session_id: Mapped[int] = mapped_column(ForeignKey("sessions.id"), nullable=False)
    role: Mapped[str] = mapped_column(
        Enum("system", "user", "assistant", "tool", name="message_role"),
        nullable=False,
        index=True,
    )
    content: Mapped[str | None] = mapped_column(Text, nullable=True)  # Null for tool calls
    name: Mapped[str | None] = mapped_column(String(200), nullable=True)  # For tool role messages
    tool_calls: Mapped[dict | None] = mapped_column(
        JSON, nullable=True
    )  # Array of tool call objects
    tool_call_id: Mapped[str | None] = mapped_column(
        String(200), nullable=True, index=True
    )  # For tool response messages
    sequence: Mapped[int] = mapped_column(Integer, nullable=False)  # Order within session
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)

    # Relationships
    session: Mapped["Session"] = relationship("Session", back_populates="messages")
    tool_call_records: Mapped[list["ToolCall"]] = relationship(
        "ToolCall", back_populates="message", cascade="all, delete-orphan"
    )
    document_links: Mapped[list["MessageDocument"]] = relationship(
        "MessageDocument", back_populates="message", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<Message(id={self.id}, role={self.role}, sequence={self.sequence})>"


class ToolCall(Base):
    """Individual tool invocation with tracking.

    Tracks execution details, results, timing, and status for each tool call.
    """

    __tablename__ = "tool_calls"

    id: Mapped[int] = mapped_column(primary_key=True)
    message_id: Mapped[int] = mapped_column(ForeignKey("messages.id"), nullable=False)
    tool_call_id: Mapped[str] = mapped_column(
        String(200), nullable=False, index=True
    )  # OpenAI's call ID
    name: Mapped[str] = mapped_column(String(200), nullable=False, index=True)  # Tool/function name
    arguments: Mapped[dict] = mapped_column(JSON, nullable=False)  # Tool arguments as JSON
    result: Mapped[str | None] = mapped_column(Text, nullable=True)  # Execution result
    status: Mapped[str] = mapped_column(
        Enum("pending", "success", "failed", name="tool_call_status"),
        default="pending",
        nullable=False,
        index=True,
    )
    error: Mapped[str | None] = mapped_column(Text, nullable=True)  # Error message if failed
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    completed_at: Mapped[datetime | None] = mapped_column(nullable=True)

    # Relationships
    message: Mapped["Message"] = relationship("Message", back_populates="tool_call_records")

    def __repr__(self) -> str:
        return f"<ToolCall(id={self.id}, name={self.name!r}, status={self.status})>"


# Indexes for performance
Index("idx_message_session_sequence", Message.session_id, Message.sequence)
Index("idx_toolcall_message", ToolCall.message_id)
Index("idx_toolcall_status", ToolCall.status)
Index("idx_toolcall_name", ToolCall.name)
