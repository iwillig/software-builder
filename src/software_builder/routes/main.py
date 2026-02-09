"""Main application routes."""

import structlog
from flask import Blueprint, jsonify, render_template

from ..config import get_settings

main_bp = Blueprint("main", __name__)
logger = structlog.get_logger()


@main_bp.route("/")
def index():
    """Home page."""
    settings = get_settings()
    logger.info("index_page_accessed")

    return render_template(
        "index.html",
        app_name=settings.app.name,
        app_version=settings.app.version,
    )


@main_bp.route("/health")
def health():
    """Health check endpoint.

    Returns:
        JSON response with application status
    """
    settings = get_settings()

    return jsonify(
        {
            "status": "healthy",
            "app": settings.app.name,
            "version": settings.app.version,
            "environment": settings.app.environment,
        }
    )


@main_bp.route("/about")
def about():
    """About page."""
    settings = get_settings()
    logger.info("about_page_accessed")

    return render_template(
        "about.html",
        app_name=settings.app.name,
        app_version=settings.app.version,
    )
