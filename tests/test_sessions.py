"""Tests for session management routes."""

import pytest
from flask import Flask
from flask.testing import FlaskClient

from software_builder.app import create_app
from software_builder.config import Settings
from software_builder.models import Message, Session, SystemPrompt, ToolPrompt, get_session


@pytest.fixture
def app() -> Flask:
    """Create Flask app for testing."""
    settings = Settings(
        app={"environment": "test", "debug": True},
        database={"url": "sqlite:///:memory:"},
        logging={"level": "WARNING"},
    )
    return create_app(settings)


@pytest.fixture
def client(app: Flask) -> FlaskClient:
    """Create test client."""
    return app.test_client()


@pytest.fixture
def sample_prompts():
    """Create sample prompts for testing."""
    with get_session() as db:
        # Create system prompts
        system_prompt_v1 = SystemPrompt(
            version="v1",
            name="Test System Prompt V1",
            content="You are a helpful assistant.",
            is_active=True,
        )
        system_prompt_v2 = SystemPrompt(
            version="v2",
            name="Test System Prompt V2",
            content="You are a coding assistant.",
            is_active=False,
        )

        # Create tool prompts
        tool_prompt_v1 = ToolPrompt(
            version="v1",
            name="Test Tool Prompt V1",
            tools_json=[{"name": "test_tool"}],
            is_active=True,
        )
        tool_prompt_v2 = ToolPrompt(
            version="v2",
            name="Test Tool Prompt V2",
            tools_json=[{"name": "test_tool_v2"}],
            is_active=False,
        )

        db.add_all([system_prompt_v1, system_prompt_v2, tool_prompt_v1, tool_prompt_v2])
        db.commit()

        return {
            "system_v1": system_prompt_v1.id,
            "system_v2": system_prompt_v2.id,
            "tool_v1": tool_prompt_v1.id,
            "tool_v2": tool_prompt_v2.id,
        }


@pytest.fixture
def sample_session(sample_prompts):
    """Create a sample session for testing."""
    with get_session() as db:
        session = Session(
            title="Test Session",
            system_prompt_id=sample_prompts["system_v1"],
            tool_prompt_id=sample_prompts["tool_v1"],
            status="active",
        )
        db.add(session)
        db.commit()
        db.refresh(session)
        return session.id


def test_list_sessions_empty(client: FlaskClient):
    """Test listing sessions when none exist."""
    response = client.get("/sessions/")

    assert response.status_code == 200
    assert b"No sessions yet" in response.data
    assert b"Create your first session" in response.data


def test_list_sessions_with_data(client: FlaskClient, sample_session: int):
    """Test listing sessions with data."""
    response = client.get("/sessions/")

    assert response.status_code == 200
    assert b"Test Session" in response.data
    assert b"active" in response.data


def test_new_session_form(client: FlaskClient, sample_prompts: dict):
    """Test new session form displays."""
    response = client.get("/sessions/new")

    assert response.status_code == 200
    assert b"Create New Session" in response.data
    assert b"Session Title" in response.data
    assert b"System Prompt Version" in response.data
    assert b"Test System Prompt V1" in response.data
    assert b"automatically" in response.data  # Mentions tool prompt is automatic


def test_create_session_success(client: FlaskClient, sample_prompts: dict):
    """Test creating a session successfully."""
    response = client.post(
        "/sessions/",
        data={
            "title": "My New Session",
            "system_prompt_id": sample_prompts["system_v1"],
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"My New Session" in response.data
    assert b"Session Information" in response.data


def test_create_session_missing_title(client: FlaskClient, sample_prompts: dict):
    """Test creating session without title fails."""
    response = client.post(
        "/sessions/",
        data={
            "title": "",
            "system_prompt_id": sample_prompts["system_v1"],
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Title is required" in response.data


def test_create_session_missing_prompts(client: FlaskClient):
    """Test creating session without system prompt fails."""
    response = client.post(
        "/sessions/",
        data={
            "title": "Test Session",
            "system_prompt_id": "",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"System prompt is required" in response.data


def test_view_session(client: FlaskClient, sample_session: int):
    """Test viewing a session."""
    response = client.get(f"/sessions/{sample_session}")

    assert response.status_code == 200
    assert b"Test Session" in response.data
    assert b"Session Information" in response.data
    assert b"Messages" in response.data


def test_view_session_not_found(client: FlaskClient):
    """Test viewing non-existent session."""
    response = client.get("/sessions/99999", follow_redirects=True)

    assert response.status_code == 200
    assert b"Session not found" in response.data


def test_view_session_with_messages(client: FlaskClient, sample_session: int):
    """Test viewing session with messages."""
    # Add messages to session
    with get_session() as db:
        msg1 = Message(
            session_id=sample_session,
            role="user",
            content="Hello, can you help me?",
            sequence=0,
        )
        msg2 = Message(
            session_id=sample_session,
            role="assistant",
            content="Of course! How can I assist you?",
            sequence=1,
        )
        db.add_all([msg1, msg2])
        db.commit()

    response = client.get(f"/sessions/{sample_session}")

    assert response.status_code == 200
    assert b"Messages (2)" in response.data
    assert b"Hello, can you help me?" in response.data
    assert b"Of course! How can I assist you?" in response.data
    assert b"user" in response.data
    assert b"assistant" in response.data


def test_delete_session(client: FlaskClient, sample_session: int):
    """Test deleting a session."""
    response = client.post(
        f"/sessions/{sample_session}/delete",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"deleted" in response.data

    # Verify session is deleted
    with get_session() as db:
        session = db.query(Session).filter_by(id=sample_session).first()
        assert session is None


def test_delete_session_not_found(client: FlaskClient):
    """Test deleting non-existent session."""
    response = client.post(
        "/sessions/99999/delete",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Session not found" in response.data


def test_sessions_blueprint_registered(app: Flask):
    """Test that sessions blueprint is registered."""
    assert "sessions" in [bp.name for bp in app.blueprints.values()]


def test_session_list_ordering(client: FlaskClient, sample_prompts: dict):
    """Test that sessions are ordered by updated_at desc."""
    # Create multiple sessions
    with get_session() as db:
        session1 = Session(
            title="First Session",
            system_prompt_id=sample_prompts["system_v1"],
            tool_prompt_id=sample_prompts["tool_v1"],
            status="active",
        )
        session2 = Session(
            title="Second Session",
            system_prompt_id=sample_prompts["system_v1"],
            tool_prompt_id=sample_prompts["tool_v1"],
            status="active",
        )
        db.add_all([session1, session2])
        db.commit()

    response = client.get("/sessions/")

    # Second session should appear before first session (newer first)
    second_pos = response.data.find(b"Second Session")
    first_pos = response.data.find(b"First Session")
    assert second_pos < first_pos


def test_session_detail_shows_prompt_versions(client: FlaskClient, sample_session: int):
    """Test that session detail shows prompt versions."""
    response = client.get(f"/sessions/{sample_session}")

    assert response.status_code == 200
    assert b"v1" in response.data  # System prompt version
    assert b"Test System Prompt V1" in response.data
    assert b"Test Tool Prompt V1" in response.data


def test_create_session_with_specific_prompts(client: FlaskClient, sample_prompts: dict):
    """Test creating session with specific (non-active) system prompt version."""
    response = client.post(
        "/sessions/",
        data={
            "title": "Session with V2 System Prompt",
            "system_prompt_id": sample_prompts["system_v2"],
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Session with V2 System Prompt" in response.data

    # Verify correct prompts are linked
    with get_session() as db:
        session = db.query(Session).filter_by(title="Session with V2 System Prompt").first()
        assert session.system_prompt.version == "v2"
        # Tool prompt should be the active one (v1)
        assert session.tool_prompt.version == "v1"
