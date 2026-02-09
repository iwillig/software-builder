"""Trace and Evaluation models for the evaluation system."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import JSON, Enum, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, utcnow

if TYPE_CHECKING:
    from .session import Session


class Trace(Base):
    """Complete execution trace for evaluation.

    Captures immutable snapshot of a session execution including
    prompt versions, inputs, outputs, and metadata.
    """

    __tablename__ = "traces"

    id: Mapped[int] = mapped_column(primary_key=True)
    session_id: Mapped[int] = mapped_column(
        ForeignKey("sessions.id"), nullable=False, unique=True, index=True
    )
    prompt_version: Mapped[str] = mapped_column(
        String(50), nullable=False, index=True
    )  # Snapshot of system_prompt version
    tool_version: Mapped[str] = mapped_column(
        String(50), nullable=False, index=True
    )  # Snapshot of tool_prompt version
    input: Mapped[str] = mapped_column(Text, nullable=False)  # Initial user request
    output: Mapped[str] = mapped_column(Text, nullable=False)  # Final agent response
    status: Mapped[str] = mapped_column(
        Enum("success", "failed", "timeout", name="trace_status"),
        nullable=False,
        index=True,
    )
    error: Mapped[str | None] = mapped_column(Text, nullable=True)  # Error message if failed
    total_tokens: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    total_cost: Mapped[float] = mapped_column(default=0.0, nullable=False)  # Cost in USD
    duration_ms: Mapped[int] = mapped_column(Integer, nullable=False)  # Execution duration
    trace_metadata: Mapped[dict | None] = mapped_column(
        JSON, nullable=True
    )  # Model, temperature, etc.
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)

    # Relationships
    session: Mapped["Session"] = relationship("Session", back_populates="trace")
    evaluations: Mapped[list["Evaluation"]] = relationship(
        "Evaluation", back_populates="trace", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return (
            f"<Trace(id={self.id}, status={self.status}, prompt_version={self.prompt_version!r})>"
        )


class Evaluation(Base):
    """Manual annotation/judgment of a trace.

    Simple binary pass/fail evaluations following best practices from
    Hamel Husain's LLM evaluation guide.
    """

    __tablename__ = "evaluations"

    id: Mapped[int] = mapped_column(primary_key=True)
    trace_id: Mapped[int] = mapped_column(ForeignKey("traces.id"), nullable=False, index=True)
    judgment: Mapped[str] = mapped_column(
        Enum("pass", "fail", name="evaluation_judgment"),
        nullable=False,
        index=True,
    )
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)  # Why it failed, issues found
    error_type: Mapped[str | None] = mapped_column(
        String(100), nullable=True, index=True
    )  # hallucination, syntax, logic, etc.
    reviewed_by: Mapped[str | None] = mapped_column(
        String(200), nullable=True
    )  # Who reviewed (optional)
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)

    # Relationships
    trace: Mapped["Trace"] = relationship("Trace", back_populates="evaluations")

    def __repr__(self) -> str:
        return f"<Evaluation(id={self.id}, trace_id={self.trace_id}, judgment={self.judgment})>"


# Indexes for performance
Index("idx_trace_session", Trace.session_id)
Index("idx_trace_prompt_version", Trace.prompt_version)
Index("idx_trace_status", Trace.status)
Index("idx_evaluation_trace", Evaluation.trace_id)
Index("idx_evaluation_judgment", Evaluation.judgment)
Index("idx_evaluation_error_type", Evaluation.error_type)
