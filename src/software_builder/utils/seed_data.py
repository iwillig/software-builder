"""Seed database with initial data."""

import structlog

from ..models import SystemPrompt, ToolPrompt, get_session

logger = structlog.get_logger()


def seed_prompts() -> None:
    """Create initial system and tool prompts if they don't exist."""
    with get_session() as db:
        # Check if prompts already exist
        existing_system = db.query(SystemPrompt).first()
        existing_tool = db.query(ToolPrompt).first()

        if existing_system and existing_tool:
            logger.info("prompts_already_exist")
            return

        # Create default system prompt
        if not existing_system:
            system_prompt = SystemPrompt(
                version="v1",
                name="Default System Prompt",
                content="""You are a helpful coding assistant. Your role is to:
- Help users write, debug, and improve code
- Explain technical concepts clearly
- Suggest best practices and design patterns
- Answer programming questions accurately

Be concise, clear, and practical in your responses.""",
                is_active=True,
            )
            db.add(system_prompt)
            logger.info("system_prompt_created", version="v1")

        # Create default tool prompt
        if not existing_tool:
            tool_prompt = ToolPrompt(
                version="v1",
                name="Default Tool Definitions",
                tools_json=[
                    {
                        "type": "function",
                        "function": {
                            "name": "write_file",
                            "description": "Write content to a file",
                            "parameters": {
                                "type": "object",
                                "properties": {
                                    "path": {
                                        "type": "string",
                                        "description": "File path relative to project root",
                                    },
                                    "content": {
                                        "type": "string",
                                        "description": "Content to write to the file",
                                    },
                                },
                                "required": ["path", "content"],
                            },
                        },
                    },
                    {
                        "type": "function",
                        "function": {
                            "name": "read_file",
                            "description": "Read content from a file",
                            "parameters": {
                                "type": "object",
                                "properties": {
                                    "path": {
                                        "type": "string",
                                        "description": "File path relative to project root",
                                    }
                                },
                                "required": ["path"],
                            },
                        },
                    },
                ],
                is_active=True,
            )
            db.add(tool_prompt)
            logger.info("tool_prompt_created", version="v1")

        db.commit()
        logger.info("seed_data_complete")


if __name__ == "__main__":
    seed_prompts()
    print("✅ Seed data created successfully!")
