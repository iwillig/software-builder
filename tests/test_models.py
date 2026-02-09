"""Tests for SQLAlchemy models."""

from datetime import UTC, datetime

from sqlalchemy.orm import Session

from software_builder.models import (
    Dataset,
    DatasetCase,
    Document,
    Evaluation,
    Message,
    MessageDocument,
    SystemPrompt,
    ToolCall,
    ToolPrompt,
    Trace,
)
from software_builder.models import Session as SessionModel


def test_system_prompt_creation(db_session: Session) -> None:
    """Test creating a SystemPrompt."""
    prompt = SystemPrompt(
        version="v1",
        name="Test System Prompt",
        content="You are a helpful coding assistant.",
        is_active=True,
    )
    db_session.add(prompt)
    db_session.commit()

    assert prompt.id is not None
    assert prompt.version == "v1"
    assert prompt.is_active is True
    assert isinstance(prompt.created_at, datetime)


def test_tool_prompt_creation(db_session: Session) -> None:
    """Test creating a ToolPrompt with JSON tools."""
    tools = [
        {
            "type": "function",
            "function": {
                "name": "write_file",
                "description": "Write content to a file",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"},
                    },
                    "required": ["path", "content"],
                },
            },
        }
    ]

    prompt = ToolPrompt(version="v1", name="Test Tool Prompt", tools_json=tools, is_active=True)
    db_session.add(prompt)
    db_session.commit()

    assert prompt.id is not None
    assert isinstance(prompt.tools_json, list)
    assert len(prompt.tools_json) == 1
    assert prompt.tools_json[0]["function"]["name"] == "write_file"


def test_session_with_messages(db_session: Session) -> None:
    """Test creating a Session with Messages."""
    # Create prompts
    system_prompt = SystemPrompt(version="v1", name="System", content="Test", is_active=True)
    tool_prompt = ToolPrompt(version="v1", name="Tools", tools_json=[], is_active=True)
    db_session.add_all([system_prompt, tool_prompt])
    db_session.commit()

    # Create session
    session = SessionModel(
        title="Test Session",
        system_prompt_id=system_prompt.id,
        tool_prompt_id=tool_prompt.id,
        status="active",
    )
    db_session.add(session)
    db_session.commit()

    # Create messages
    msg1 = Message(
        session_id=session.id,
        role="user",
        content="Hello, world!",
        sequence=0,
    )
    msg2 = Message(
        session_id=session.id,
        role="assistant",
        content="Hi there!",
        sequence=1,
    )
    db_session.add_all([msg1, msg2])
    db_session.commit()

    # Verify relationships
    assert len(session.messages) == 2
    assert session.messages[0].content == "Hello, world!"
    assert session.messages[1].role == "assistant"


def test_tool_call_tracking(db_session: Session) -> None:
    """Test ToolCall tracking for a message."""
    # Create required objects
    system_prompt = SystemPrompt(version="v1", name="System", content="Test", is_active=True)
    tool_prompt = ToolPrompt(version="v1", name="Tools", tools_json=[], is_active=True)
    db_session.add_all([system_prompt, tool_prompt])
    db_session.commit()

    session = SessionModel(
        title="Test",
        system_prompt_id=system_prompt.id,
        tool_prompt_id=tool_prompt.id,
    )
    db_session.add(session)
    db_session.commit()

    message = Message(
        session_id=session.id,
        role="assistant",
        content=None,
        tool_calls=[{"id": "call_123", "type": "function", "function": {"name": "write_file"}}],
        sequence=0,
    )
    db_session.add(message)
    db_session.commit()

    # Create tool call
    tool_call = ToolCall(
        message_id=message.id,
        tool_call_id="call_123",
        name="write_file",
        arguments={"path": "test.py", "content": "print('hello')"},
        status="success",
        result="File written successfully",
        completed_at=datetime.now(UTC),
    )
    db_session.add(tool_call)
    db_session.commit()

    # Verify
    assert len(message.tool_call_records) == 1
    assert message.tool_call_records[0].name == "write_file"
    assert message.tool_call_records[0].status == "success"


def test_document_with_message_link(db_session: Session) -> None:
    """Test Document with MessageDocument junction table."""
    # Create document
    doc = Document(
        name="test.py",
        path="/tmp/test.py",
        content="print('hello')",
        content_type="python",
        size_bytes=15,
        checksum="abc123",
        doc_metadata={"language": "python", "lines": 1},
    )
    db_session.add(doc)
    db_session.commit()

    # Create session and message
    system_prompt = SystemPrompt(version="v1", name="System", content="Test", is_active=True)
    tool_prompt = ToolPrompt(version="v1", name="Tools", tools_json=[], is_active=True)
    db_session.add_all([system_prompt, tool_prompt])
    db_session.commit()

    session = SessionModel(
        title="Test",
        system_prompt_id=system_prompt.id,
        tool_prompt_id=tool_prompt.id,
    )
    db_session.add(session)
    db_session.commit()

    message = Message(
        session_id=session.id,
        role="user",
        content="Check this file",
        sequence=0,
    )
    db_session.add(message)
    db_session.commit()

    # Link document to message
    link = MessageDocument(message_id=message.id, document_id=doc.id, relevance=0.95)
    db_session.add(link)
    db_session.commit()

    # Verify
    assert len(message.document_links) == 1
    assert message.document_links[0].document.name == "test.py"
    assert message.document_links[0].relevance == 0.95


def test_trace_and_evaluation(db_session: Session) -> None:
    """Test Trace with Evaluation."""
    # Create session
    system_prompt = SystemPrompt(version="v2", name="System", content="Test", is_active=True)
    tool_prompt = ToolPrompt(version="v2", name="Tools", tools_json=[], is_active=True)
    db_session.add_all([system_prompt, tool_prompt])
    db_session.commit()

    session = SessionModel(
        title="Test",
        system_prompt_id=system_prompt.id,
        tool_prompt_id=tool_prompt.id,
        status="completed",
    )
    db_session.add(session)
    db_session.commit()

    # Create trace
    trace = Trace(
        session_id=session.id,
        prompt_version="v2",
        tool_version="v2",
        input="Create a hello world function",
        output="def hello(): print('Hello')",
        status="success",
        total_tokens=150,
        total_cost=0.002,
        duration_ms=1500,
        trace_metadata={"model": "gpt-4o-mini", "temperature": 0.7},
    )
    db_session.add(trace)
    db_session.commit()

    # Create evaluation
    evaluation = Evaluation(
        trace_id=trace.id,
        judgment="pass",
        notes="Correct implementation",
        error_type=None,
        reviewed_by="test_user",
    )
    db_session.add(evaluation)
    db_session.commit()

    # Verify
    assert trace.evaluations[0].judgment == "pass"
    assert session.trace.id == trace.id


def test_dataset_with_cases(db_session: Session) -> None:
    """Test Dataset with DatasetCase."""
    # Create dataset
    dataset = Dataset(
        name="agent_errors",
        description="Common agent errors",
        version="1.0.0",
    )
    db_session.add(dataset)
    db_session.commit()

    # Create test cases
    case1 = DatasetCase(
        dataset_id=dataset.id,
        input="Create a function",
        expected="Should create valid Python function",
        context={"files": ["main.py"], "requirements": []},
        tags=["function", "basic"],
    )
    case2 = DatasetCase(
        dataset_id=dataset.id,
        input="Fix syntax error",
        expected="Should fix the error without breaking code",
        context={"files": ["broken.py"]},
        tags=["syntax", "error"],
    )
    db_session.add_all([case1, case2])
    db_session.commit()

    # Verify
    assert len(dataset.cases) == 2
    assert dataset.cases[0].input == "Create a function"
    assert dataset.cases[1].tags == ["syntax", "error"]


def test_prompt_versioning_workflow(db_session: Session) -> None:
    """Test complete prompt versioning workflow."""
    # Create v1 prompts
    system_v1 = SystemPrompt(version="v1", name="System V1", content="Old", is_active=True)
    tool_v1 = ToolPrompt(version="v1", name="Tool V1", tools_json=[], is_active=True)
    db_session.add_all([system_v1, tool_v1])
    db_session.commit()

    # Create v2 prompts
    system_v2 = SystemPrompt(version="v2", name="System V2", content="New", is_active=False)
    tool_v2 = ToolPrompt(version="v2", name="Tool V2", tools_json=[], is_active=False)
    db_session.add_all([system_v2, tool_v2])
    db_session.commit()

    # Create session with v1
    session = SessionModel(
        title="Test",
        system_prompt_id=system_v1.id,
        tool_prompt_id=tool_v1.id,
    )
    db_session.add(session)
    db_session.commit()

    # Verify version linking
    assert session.system_prompt.version == "v1"
    assert session.tool_prompt.version == "v1"

    # Switch active version
    system_v1.is_active = False
    system_v2.is_active = True
    db_session.commit()

    # Old session still uses v1
    assert session.system_prompt.version == "v1"
