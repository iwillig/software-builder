"""Tests for system prompt management routes."""

import pytest
from flask import Flask
from flask.testing import FlaskClient

from software_builder.app import create_app
from software_builder.config import Settings
from software_builder.models import Session, SystemPrompt, ToolPrompt, get_session


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
def sample_prompt():
    """Create a sample system prompt for testing."""
    with get_session() as db:
        prompt = SystemPrompt(
            version="v1",
            name="Test System Prompt",
            content="You are a helpful assistant.",
            is_active=True,
        )
        db.add(prompt)
        db.commit()
        db.refresh(prompt)
        return prompt.id


def test_list_prompts_empty(client: FlaskClient):
    """Test listing prompts when none exist."""
    response = client.get("/system-prompts/")

    assert response.status_code == 200
    assert b"No system prompts yet" in response.data
    assert b"Create your first system prompt" in response.data


def test_list_prompts_with_data(client: FlaskClient, sample_prompt: int):
    """Test listing prompts with data."""
    response = client.get("/system-prompts/")

    assert response.status_code == 200
    assert b"Test System Prompt" in response.data
    assert b"v1" in response.data
    assert b"Active" in response.data


def test_new_prompt_form(client: FlaskClient):
    """Test new prompt form displays."""
    response = client.get("/system-prompts/new")

    assert response.status_code == 200
    assert b"Create New System Prompt" in response.data
    assert b"Version" in response.data
    assert b"Name" in response.data
    assert b"Prompt Content" in response.data
    assert b"Set as active prompt" in response.data


def test_create_prompt_success(client: FlaskClient):
    """Test creating a prompt successfully."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "v2",
            "name": "My Custom Prompt",
            "content": "You are an expert coder.",
            "is_active": "on",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"My Custom Prompt" in response.data
    assert b"v2" in response.data


def test_create_prompt_missing_version(client: FlaskClient):
    """Test creating prompt without version fails."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "",
            "name": "Test",
            "content": "Test content",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Version is required" in response.data


def test_create_prompt_missing_name(client: FlaskClient):
    """Test creating prompt without name fails."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "v2",
            "name": "",
            "content": "Test content",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Name is required" in response.data


def test_create_prompt_missing_content(client: FlaskClient):
    """Test creating prompt without content fails."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "v2",
            "name": "Test",
            "content": "",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Content is required" in response.data


def test_create_prompt_duplicate_version(client: FlaskClient, sample_prompt: int):
    """Test creating prompt with duplicate version fails."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "v1",  # Already exists
            "name": "Duplicate",
            "content": "Test",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Version &#39;v1&#39; already exists" in response.data


def test_view_prompt(client: FlaskClient, sample_prompt: int):
    """Test viewing a prompt."""
    response = client.get(f"/system-prompts/{sample_prompt}")

    assert response.status_code == 200
    assert b"Test System Prompt" in response.data
    assert b"v1" in response.data
    assert b"You are a helpful assistant." in response.data
    assert b"Prompt Information" in response.data


def test_view_prompt_not_found(client: FlaskClient):
    """Test viewing non-existent prompt."""
    response = client.get("/system-prompts/99999", follow_redirects=True)

    assert response.status_code == 200
    assert b"System prompt not found" in response.data


def test_activate_prompt(client: FlaskClient):
    """Test activating a prompt."""
    # Create two prompts
    with get_session() as db:
        prompt1 = SystemPrompt(
            version="v1",
            name="First",
            content="Content 1",
            is_active=True,
        )
        prompt2 = SystemPrompt(
            version="v2",
            name="Second",
            content="Content 2",
            is_active=False,
        )
        db.add_all([prompt1, prompt2])
        db.commit()
        prompt2_id = prompt2.id

    # Activate prompt2
    response = client.post(
        f"/system-prompts/{prompt2_id}/activate",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"is now active" in response.data

    # Verify prompt2 is active and prompt1 is not
    with get_session() as db:
        p1 = db.query(SystemPrompt).filter_by(version="v1").first()
        p2 = db.query(SystemPrompt).filter_by(version="v2").first()
        assert p1.is_active is False
        assert p2.is_active is True


def test_delete_prompt_unused(client: FlaskClient, sample_prompt: int):
    """Test deleting an unused prompt."""
    response = client.post(
        f"/system-prompts/{sample_prompt}/delete",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"deleted" in response.data

    # Verify prompt is deleted
    with get_session() as db:
        prompt = db.query(SystemPrompt).filter_by(id=sample_prompt).first()
        assert prompt is None


def test_delete_prompt_in_use(client: FlaskClient, sample_prompt: int):
    """Test deleting a prompt that's in use fails."""
    # Create a session using the prompt
    with get_session() as db:
        tool_prompt = ToolPrompt(
            version="v1",
            name="Test Tools",
            tools_json=[],
            is_active=True,
        )
        db.add(tool_prompt)
        db.commit()

        session = Session(
            title="Test Session",
            system_prompt_id=sample_prompt,
            tool_prompt_id=tool_prompt.id,
            status="active",
        )
        db.add(session)
        db.commit()

    response = client.post(
        f"/system-prompts/{sample_prompt}/delete",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"Cannot delete" in response.data
    assert b"session(s) use this prompt" in response.data

    # Verify prompt still exists
    with get_session() as db:
        prompt = db.query(SystemPrompt).filter_by(id=sample_prompt).first()
        assert prompt is not None


def test_delete_prompt_not_found(client: FlaskClient):
    """Test deleting non-existent prompt."""
    response = client.post(
        "/system-prompts/99999/delete",
        follow_redirects=True,
    )

    assert response.status_code == 200
    assert b"System prompt not found" in response.data


def test_system_prompts_blueprint_registered(app: Flask):
    """Test that system_prompts blueprint is registered."""
    assert "system_prompts" in [bp.name for bp in app.blueprints.values()]


def test_create_prompt_as_active_deactivates_others(client: FlaskClient, sample_prompt: int):
    """Test that creating an active prompt deactivates others."""
    response = client.post(
        "/system-prompts/",
        data={
            "version": "v2",
            "name": "New Active",
            "content": "Content",
            "is_active": "on",
        },
        follow_redirects=True,
    )

    assert response.status_code == 200

    # Verify only v2 is active
    with get_session() as db:
        v1 = db.query(SystemPrompt).filter_by(version="v1").first()
        v2 = db.query(SystemPrompt).filter_by(version="v2").first()
        assert v1.is_active is False
        assert v2.is_active is True


def test_prompt_list_ordering(client: FlaskClient):
    """Test that prompts are ordered by created_at desc."""
    with get_session() as db:
        prompt1 = SystemPrompt(version="v1", name="First", content="Content 1", is_active=False)
        prompt2 = SystemPrompt(version="v2", name="Second", content="Content 2", is_active=False)
        db.add_all([prompt1, prompt2])
        db.commit()

    response = client.get("/system-prompts/")

    # v2 should appear before v1 (newer first)
    v2_pos = response.data.find(b"v2")
    v1_pos = response.data.find(b"v1")
    assert v2_pos < v1_pos


def test_view_prompt_shows_session_count(client: FlaskClient, sample_prompt: int):
    """Test that prompt detail shows session count."""
    # Create a session using the prompt
    with get_session() as db:
        tool_prompt = ToolPrompt(version="v1", name="Test Tools", tools_json=[], is_active=True)
        db.add(tool_prompt)
        db.commit()

        session = Session(
            title="Test Session",
            system_prompt_id=sample_prompt,
            tool_prompt_id=tool_prompt.id,
            status="active",
        )
        db.add(session)
        db.commit()

    response = client.get(f"/system-prompts/{sample_prompt}")

    assert response.status_code == 200
    assert b"1 session(s)" in response.data
