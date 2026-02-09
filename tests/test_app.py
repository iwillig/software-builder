"""Tests for Flask application."""

import pytest
from flask import Flask
from flask.testing import FlaskClient

from software_builder.app import create_app
from software_builder.config import Settings


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


def test_app_creation() -> None:
    """Test creating Flask application."""
    app = create_app()
    assert app is not None
    assert isinstance(app, Flask)


def test_app_config() -> None:
    """Test Flask app configuration from settings."""
    settings = Settings(
        app={"secret_key": "test-secret-key", "debug": True},
        database={"url": "sqlite:///:memory:"},
    )
    app = create_app(settings)

    assert app.config["SECRET_KEY"] == "test-secret-key"
    assert app.config["DEBUG"] is True


def test_index_route(client: FlaskClient) -> None:
    """Test home page route."""
    response = client.get("/")

    assert response.status_code == 200
    assert b"Software Builder" in response.data
    assert b"Local LLM coding agent" in response.data


def test_health_route(client: FlaskClient) -> None:
    """Test health check endpoint."""
    response = client.get("/health")

    assert response.status_code == 200
    assert response.is_json

    data = response.get_json()
    assert data["status"] == "healthy"
    assert "app" in data
    assert "version" in data
    assert "environment" in data


def test_about_route(client: FlaskClient) -> None:
    """Test about page route."""
    response = client.get("/about")

    assert response.status_code == 200
    assert b"About Software Builder" in response.data
    assert b"Technology Stack" in response.data


def test_404_error(client: FlaskClient) -> None:
    """Test 404 error handler."""
    response = client.get("/nonexistent-page")

    assert response.status_code == 404
    assert b"404" in response.data
    assert b"Page Not Found" in response.data


def test_blueprints_registered(app: Flask) -> None:
    """Test that blueprints are registered."""
    blueprint_names = [bp.name for bp in app.blueprints.values()]
    assert "main" in blueprint_names


def test_error_handlers_registered(app: Flask) -> None:
    """Test that error handlers are registered."""
    assert 404 in app.error_handler_spec[None]
    assert 500 in app.error_handler_spec[None]


def test_templates_rendered(client: FlaskClient) -> None:
    """Test that templates contain proper structure."""
    response = client.get("/")

    # Check for base template elements
    assert b"<!DOCTYPE html>" in response.data
    assert b"<nav" in response.data
    assert b"<main" in response.data
    assert b"<footer" in response.data

    # Check for Pico CSS
    assert b"picocss" in response.data

    # Check for Datastar
    assert b"datastar" in response.data


def test_navigation_links(client: FlaskClient) -> None:
    """Test that navigation links are present."""
    response = client.get("/")

    assert b'href="/"' in response.data
    assert b'href="/sessions"' in response.data
    assert b'href="/traces"' in response.data
    assert b'href="/evals"' in response.data
    assert b'href="/about"' in response.data


def test_health_endpoint_json_structure(client: FlaskClient) -> None:
    """Test health endpoint returns correct JSON structure."""
    response = client.get("/health")
    data = response.get_json()

    required_fields = ["status", "app", "version", "environment"]
    for field in required_fields:
        assert field in data, f"Missing field: {field}"

    assert isinstance(data["status"], str)
    assert isinstance(data["app"], str)
    assert isinstance(data["version"], str)
    assert isinstance(data["environment"], str)


def test_app_with_custom_settings() -> None:
    """Test app creation with custom settings."""

    settings = Settings(
        app={
            "name": "Custom App",
            "version": "1.0.0",
            "environment": "test",
        },
        database={"url": "sqlite:///:memory:"},
    )

    # Create app with custom settings
    app = create_app(settings)

    with app.test_client() as client:
        # The health endpoint gets settings from the singleton
        # So we need to check the Flask config instead
        assert app.config["ENV"] == "test"
        assert app.config["DEBUG"] is False  # Default for custom settings

        response = client.get("/health")
        data = response.get_json()

        # Note: health endpoint uses get_settings() which is a singleton
        # This test verifies the app was created successfully with custom settings
        assert data["status"] == "healthy"
        assert "app" in data
        assert "version" in data
