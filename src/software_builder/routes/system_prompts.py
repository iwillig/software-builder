"""System prompt management routes."""

import structlog
from flask import Blueprint, flash, redirect, render_template, request, url_for

from ..models import Session, SystemPrompt, get_session

system_prompts_bp = Blueprint("system_prompts", __name__, url_prefix="/system-prompts")
logger = structlog.get_logger()


@system_prompts_bp.route("/")
def list_prompts():
    """List all system prompts."""
    logger.info("system_prompts_list_accessed")

    with get_session() as db:
        prompts = db.query(SystemPrompt).order_by(SystemPrompt.created_at.desc()).all()

        # Get session counts for each prompt
        session_counts = {}
        for prompt in prompts:
            count = db.query(Session).filter_by(system_prompt_id=prompt.id).count()
            session_counts[prompt.id] = count

    return render_template(
        "system_prompts/list.html", prompts=prompts, session_counts=session_counts
    )


@system_prompts_bp.route("/new")
def new_prompt():
    """Show form to create new system prompt."""
    logger.info("system_prompts_new_form_accessed")
    return render_template("system_prompts/new.html")


@system_prompts_bp.route("/", methods=["POST"])
def create_prompt():
    """Create a new system prompt."""
    version = request.form.get("version", "").strip()
    name = request.form.get("name", "").strip()
    content = request.form.get("content", "").strip()
    is_active = request.form.get("is_active") == "on"

    # Validation
    if not version:
        flash("Version is required", "error")
        return redirect(url_for("system_prompts.new_prompt"))

    if not name:
        flash("Name is required", "error")
        return redirect(url_for("system_prompts.new_prompt"))

    if not content:
        flash("Content is required", "error")
        return redirect(url_for("system_prompts.new_prompt"))

    try:
        with get_session() as db:
            # Check if version already exists
            existing = db.query(SystemPrompt).filter_by(version=version).first()
            if existing:
                flash(f"Version '{version}' already exists", "error")
                return redirect(url_for("system_prompts.new_prompt"))

            # If setting as active, deactivate others
            if is_active:
                db.query(SystemPrompt).update({"is_active": False})

            # Create prompt
            prompt = SystemPrompt(
                version=version,
                name=name,
                content=content,
                is_active=is_active,
            )
            db.add(prompt)
            db.commit()
            db.refresh(prompt)

            logger.info(
                "system_prompt_created",
                prompt_id=prompt.id,
                version=version,
                is_active=is_active,
            )

            flash(f"System prompt '{version}' created successfully", "success")
            return redirect(url_for("system_prompts.view_prompt", prompt_id=prompt.id))

    except Exception as e:
        logger.error("system_prompt_creation_failed", error=str(e))
        flash("Failed to create system prompt", "error")
        return redirect(url_for("system_prompts.new_prompt"))


@system_prompts_bp.route("/<int:prompt_id>")
def view_prompt(prompt_id: int):
    """View system prompt details."""
    logger.info("system_prompt_view_accessed", prompt_id=prompt_id)

    with get_session() as db:
        prompt = db.query(SystemPrompt).filter_by(id=prompt_id).first()

        if not prompt:
            logger.warning("system_prompt_not_found", prompt_id=prompt_id)
            flash("System prompt not found", "error")
            return redirect(url_for("system_prompts.list_prompts"))

        # Get sessions using this prompt
        session_count = db.query(Session).filter_by(system_prompt_id=prompt_id).count()

    return render_template(
        "system_prompts/detail.html",
        prompt=prompt,
        session_count=session_count,
    )


@system_prompts_bp.route("/<int:prompt_id>/activate", methods=["POST"])
def activate_prompt(prompt_id: int):
    """Set a system prompt as active."""
    logger.info("system_prompt_activate_requested", prompt_id=prompt_id)

    try:
        with get_session() as db:
            prompt = db.query(SystemPrompt).filter_by(id=prompt_id).first()

            if not prompt:
                flash("System prompt not found", "error")
                return redirect(url_for("system_prompts.list_prompts"))

            # Deactivate all prompts
            db.query(SystemPrompt).update({"is_active": False})

            # Activate this prompt
            prompt.is_active = True
            db.commit()

            logger.info("system_prompt_activated", prompt_id=prompt_id, version=prompt.version)
            flash(f"System prompt '{prompt.version}' is now active", "success")

    except Exception as e:
        logger.error("system_prompt_activation_failed", prompt_id=prompt_id, error=str(e))
        flash("Failed to activate system prompt", "error")

    return redirect(url_for("system_prompts.view_prompt", prompt_id=prompt_id))


@system_prompts_bp.route("/<int:prompt_id>/delete", methods=["POST"])
def delete_prompt(prompt_id: int):
    """Delete a system prompt."""
    logger.info("system_prompt_delete_requested", prompt_id=prompt_id)

    try:
        with get_session() as db:
            prompt = db.query(SystemPrompt).filter_by(id=prompt_id).first()

            if not prompt:
                flash("System prompt not found", "error")
                return redirect(url_for("system_prompts.list_prompts"))

            # Check if any sessions use this prompt
            session_count = db.query(Session).filter_by(system_prompt_id=prompt_id).count()
            if session_count > 0:
                flash(f"Cannot delete: {session_count} session(s) use this prompt", "error")
                return redirect(url_for("system_prompts.view_prompt", prompt_id=prompt_id))

            version = prompt.version
            db.delete(prompt)
            db.commit()

            logger.info("system_prompt_deleted", prompt_id=prompt_id, version=version)
            flash(f"System prompt '{version}' deleted", "success")

    except Exception as e:
        logger.error("system_prompt_deletion_failed", prompt_id=prompt_id, error=str(e))
        flash("Failed to delete system prompt", "error")

    return redirect(url_for("system_prompts.list_prompts"))
