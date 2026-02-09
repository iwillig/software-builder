"""Session management routes."""

import structlog
from flask import Blueprint, flash, redirect, render_template, request, url_for
from sqlalchemy.orm import joinedload

from ..models import Message, Session, SystemPrompt, ToolPrompt, get_session

sessions_bp = Blueprint("sessions", __name__, url_prefix="/sessions")
logger = structlog.get_logger()


@sessions_bp.route("/")
def list_sessions():
    """List all sessions."""
    logger.info("sessions_list_accessed")

    with get_session() as db:
        sessions = (
            db.query(Session)
            .options(joinedload(Session.system_prompt), joinedload(Session.tool_prompt))
            .order_by(Session.updated_at.desc())
            .all()
        )

    return render_template("sessions/list.html", sessions=sessions)


@sessions_bp.route("/new")
def new_session():
    """Show form to create new session."""
    logger.info("sessions_new_form_accessed")

    with get_session() as db:
        # Get system prompts for selection
        system_prompts = db.query(SystemPrompt).order_by(SystemPrompt.created_at.desc()).all()

    return render_template(
        "sessions/new.html",
        system_prompts=system_prompts,
    )


@sessions_bp.route("/", methods=["POST"])
def create_session():
    """Create a new session."""
    title = request.form.get("title", "").strip()
    system_prompt_id = request.form.get("system_prompt_id")

    # Validation
    if not title:
        flash("Title is required", "error")
        return redirect(url_for("sessions.new_session"))

    if not system_prompt_id:
        flash("System prompt is required", "error")
        return redirect(url_for("sessions.new_session"))

    try:
        with get_session() as db:
            # Get active tool prompt (automatically use it)
            tool_prompt = db.query(ToolPrompt).filter_by(is_active=True).first()
            if not tool_prompt:
                # Fallback: use any tool prompt
                tool_prompt = db.query(ToolPrompt).first()

            if not tool_prompt:
                flash("No tool prompts available. Please create one first.", "error")
                return redirect(url_for("sessions.new_session"))

            # Create session
            session = Session(
                title=title,
                system_prompt_id=int(system_prompt_id),
                tool_prompt_id=tool_prompt.id,
                status="active",
            )
            db.add(session)
            db.commit()
            db.refresh(session)

            logger.info(
                "session_created",
                session_id=session.id,
                title=title,
                tool_prompt_version=tool_prompt.version,
            )

            flash(f"Session '{title}' created successfully", "success")
            return redirect(url_for("sessions.view_session", session_id=session.id))

    except Exception as e:
        logger.error("session_creation_failed", error=str(e))
        flash("Failed to create session", "error")
        return redirect(url_for("sessions.new_session"))


@sessions_bp.route("/<int:session_id>")
def view_session(session_id: int):
    """View session details with messages."""
    logger.info("session_view_accessed", session_id=session_id)

    with get_session() as db:
        session = (
            db.query(Session)
            .options(joinedload(Session.system_prompt), joinedload(Session.tool_prompt))
            .filter_by(id=session_id)
            .first()
        )

        if not session:
            logger.warning("session_not_found", session_id=session_id)
            flash("Session not found", "error")
            return redirect(url_for("sessions.list_sessions"))

        # Get messages for this session
        messages = (
            db.query(Message).filter_by(session_id=session_id).order_by(Message.sequence).all()
        )

    return render_template(
        "sessions/detail.html",
        session=session,
        messages=messages,
    )


@sessions_bp.route("/<int:session_id>/delete", methods=["POST"])
def delete_session(session_id: int):
    """Delete a session."""
    logger.info("session_delete_requested", session_id=session_id)

    try:
        with get_session() as db:
            session = db.query(Session).filter_by(id=session_id).first()

            if not session:
                flash("Session not found", "error")
                return redirect(url_for("sessions.list_sessions"))

            title = session.title
            db.delete(session)
            db.commit()

            logger.info("session_deleted", session_id=session_id, title=title)
            flash(f"Session '{title}' deleted", "success")

    except Exception as e:
        logger.error("session_deletion_failed", session_id=session_id, error=str(e))
        flash("Failed to delete session", "error")

    return redirect(url_for("sessions.list_sessions"))
