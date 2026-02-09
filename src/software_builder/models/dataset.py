"""Dataset and DatasetCase models for evaluation test suites."""

from datetime import datetime
from typing import TYPE_CHECKING, Optional

from sqlalchemy import JSON, ForeignKey, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, utcnow

if TYPE_CHECKING:
    from .trace import Trace


class Dataset(Base):
    """Evaluation test suite.

    Collections of test cases for regression testing and evaluation.
    """

    __tablename__ = "datasets"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(
        String(200), nullable=False, unique=True, index=True
    )  # e.g., "agent_errors"
    description: Mapped[str] = mapped_column(Text, nullable=False)
    version: Mapped[str] = mapped_column(String(50), nullable=False)  # Dataset version
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow, nullable=False)

    # Relationships
    cases: Mapped[list["DatasetCase"]] = relationship(
        "DatasetCase", back_populates="dataset", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<Dataset(id={self.id}, name={self.name!r}, version={self.version})>"


class DatasetCase(Base):
    """Individual test case in a dataset.

    Can be created from failed traces or manually defined.
    """

    __tablename__ = "dataset_cases"

    id: Mapped[int] = mapped_column(primary_key=True)
    dataset_id: Mapped[int] = mapped_column(ForeignKey("datasets.id"), nullable=False, index=True)
    trace_id: Mapped[int | None] = mapped_column(
        ForeignKey("traces.id"), nullable=True, index=True
    )  # Optional: origin trace
    input: Mapped[str] = mapped_column(Text, nullable=False)  # Test input
    expected: Mapped[str] = mapped_column(Text, nullable=False)  # Expected behavior/output
    context: Mapped[dict | None] = mapped_column(
        JSON, nullable=True
    )  # Files, env, requirements, etc.
    tags: Mapped[dict | None] = mapped_column(JSON, nullable=True)  # Categories as array
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)

    # Relationships
    dataset: Mapped["Dataset"] = relationship("Dataset", back_populates="cases")
    trace: Mapped[Optional["Trace"]] = relationship("Trace", foreign_keys=[trace_id])

    def __repr__(self) -> str:
        return (
            f"<DatasetCase(id={self.id}, dataset_id={self.dataset_id}, trace_id={self.trace_id})>"
        )


# Indexes for performance
Index("idx_datasetcase_dataset", DatasetCase.dataset_id)
Index("idx_datasetcase_trace", DatasetCase.trace_id)
