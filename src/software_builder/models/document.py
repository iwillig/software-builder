"""Document storage and message-document relationship models."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import JSON, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, utcnow

if TYPE_CHECKING:
    from .session import Message


class Document(Base):
    """Stored document with full content and metadata.

    Stores complete document content (not just paths) for portability.
    Uses checksum for deduplication.
    """

    __tablename__ = "documents"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(500), nullable=False)
    path: Mapped[str] = mapped_column(String(1000), nullable=False)  # File path or identifier
    content: Mapped[str] = mapped_column(Text, nullable=False)  # Full text content
    content_type: Mapped[str] = mapped_column(
        String(100), nullable=False, index=True
    )  # markdown, python, text, etc.
    size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    checksum: Mapped[str] = mapped_column(
        String(64), nullable=False, unique=True, index=True
    )  # SHA256
    doc_metadata: Mapped[dict | None] = mapped_column(JSON, nullable=True)  # Language, tags, etc.
    created_at: Mapped[datetime] = mapped_column(default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow, nullable=False)

    # Relationships
    message_links: Mapped[list["MessageDocument"]] = relationship(
        "MessageDocument", back_populates="document", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<Document(id={self.id}, name={self.name!r}, content_type={self.content_type})>"


class MessageDocument(Base):
    """Junction table linking messages to documents.

    Allows many-to-many relationship with optional relevance scoring.
    """

    __tablename__ = "message_documents"

    message_id: Mapped[int] = mapped_column(
        ForeignKey("messages.id"), primary_key=True, nullable=False
    )
    document_id: Mapped[int] = mapped_column(
        ForeignKey("documents.id"), primary_key=True, nullable=False
    )
    relevance: Mapped[float | None] = mapped_column(
        nullable=True
    )  # Optional 0.0-1.0 relevance score

    # Relationships
    message: Mapped["Message"] = relationship("Message", back_populates="document_links")
    document: Mapped["Document"] = relationship("Document", back_populates="message_links")

    def __repr__(self) -> str:
        return (
            f"<MessageDocument(message_id={self.message_id}, "
            f"document_id={self.document_id}, relevance={self.relevance})>"
        )


# Indexes for performance
Index("idx_document_checksum", Document.checksum)
Index("idx_document_content_type", Document.content_type)
