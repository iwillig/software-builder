"""SQLAlchemy models for Software Builder.

This module provides all database models for the application:
- Prompt versioning (SystemPrompt, ToolPrompt)
- Sessions and messages (Session, Message, ToolCall)
- Document storage (Document, MessageDocument)
- Evaluation system (Trace, Evaluation)
- Test datasets (Dataset, DatasetCase)
"""

from .base import Base, get_session, init_db
from .dataset import Dataset, DatasetCase
from .document import Document, MessageDocument
from .prompt import SystemPrompt, ToolPrompt
from .session import Message, Session, ToolCall
from .trace import Evaluation, Trace

__all__ = [
    # Base
    "Base",
    "init_db",
    "get_session",
    # Prompts
    "SystemPrompt",
    "ToolPrompt",
    # Sessions
    "Session",
    "Message",
    "ToolCall",
    # Documents
    "Document",
    "MessageDocument",
    # Traces
    "Trace",
    "Evaluation",
    # Datasets
    "Dataset",
    "DatasetCase",
]
