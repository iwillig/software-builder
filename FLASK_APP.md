# Flask Web Application

Software Builder's web UI is built with Flask + Datastar + Pico.css for a modern, reactive experience without complex JavaScript frameworks.

## Architecture

### Flask App Factory Pattern

```python
from software_builder.app import create_app
from software_builder.config import get_settings

# Create app with default settings
app = create_app()

# Or with custom settings
settings = get_settings()
app = create_app(settings)
```

**Benefits**:
- Easy testing with different configurations
- Multiple app instances possible
- Clean separation of concerns
- Configuration validation at startup

### Project Structure

```
src/software_builder/
├── app.py              # Flask app factory
├── routes/             # Route blueprints
│   ├── __init__.py
│   └── main.py         # Main routes (/, /health, /about)
├── config.py           # Configuration system
└── models/             # Database models

templates/
├── base.html           # Base template with Pico.css + Datastar
├── index.html          # Home page
├── about.html          # About page
└── errors/             # Error pages
    ├── 404.html
    └── 500.html

static/                 # Static assets (CSS, JS, images)
```

---

## Features

### ✅ Configuration Integration

The Flask app automatically loads configuration from:
- YAML files (`config/config.yaml`)
- Environment-specific overrides
- Environment variables (`SB_*`)

```python
# Flask config is set from Settings
app.config['SECRET_KEY'] = settings.app.secret_key.get_secret_value()
app.config['DEBUG'] = settings.app.debug
app.config['ENV'] = settings.app.environment
```

### ✅ Structured Logging (structlog)

All requests and errors are logged with structured data:

```python
import structlog

logger = structlog.get_logger()
logger.info("user_action", user_id=123, action="created_session")
logger.error("database_error", error=str(e), query="SELECT...")
```

**Log Formats**:
- **Development**: Console with colors
- **Production**: JSON for log aggregation

### ✅ Database Integration

Database is initialized automatically on app creation:

```python
def create_app(settings):
    # Initialize database with config
    init_db(
        database_url=settings.database.url,
        echo=settings.database.echo
    )
```

### ✅ Blueprint Architecture

Routes are organized into blueprints for modularity:

```python
# Main blueprint
from .routes import main_bp
app.register_blueprint(main_bp)

# Future blueprints
# app.register_blueprint(sessions_bp)
# app.register_blueprint(traces_bp)
# app.register_blueprint(evals_bp)
```

### ✅ Error Handling

Custom error pages with structured logging:

- **404**: Page not found
- **500**: Internal server error

```python
@app.errorhandler(404)
def not_found(error):
    logger.warning("page_not_found", path=error)
    return render_template("errors/404.html"), 404
```

---

## Running the Server

### Development Mode

```bash
# Using just (recommended)
just serve

# Or with custom settings
just serve --port 8080

# Direct Python
uv run software-builder serve
```

**Features**:
- Auto-reload on code changes
- Debug toolbar
- Detailed error pages
- SQL query logging (if `echo: true`)

### Production Mode

```bash
# Set environment
export SB_APP__ENVIRONMENT=production
export SB_APP__SECRET_KEY=your-secure-key

# Run server
just serve
```

**Differences**:
- No debug mode
- JSON logging
- Error pages without details
- Better performance

---

## Routes

### Main Routes

| Route | Method | Description |
|-------|--------|-------------|
| `/` | GET | Home page with feature overview |
| `/health` | GET | JSON health check endpoint |
| `/about` | GET | About page with technology stack |

### Health Check Endpoint

```bash
curl http://localhost:5000/health
```

**Response**:
```json
{
  "status": "healthy",
  "app": "Software Builder",
  "version": "0.1.0",
  "environment": "development"
}
```

**Use Cases**:
- Docker health checks
- Load balancer monitoring
- CI/CD deployment verification
- Uptime monitoring

---

## Templates

### Base Template (`templates/base.html`)

All pages extend the base template which includes:

**Features**:
- ✅ Pico.css for semantic, accessible styling
- ✅ Datastar for reactive UI (Server-Sent Events)
- ✅ Responsive navigation
- ✅ Footer with version info
- ✅ Custom CSS variables for theming

**Structure**:
```html
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <!-- Pico CSS -->
    <link rel="stylesheet" href="...">
    
    <!-- Datastar -->
    <script type="module" defer src="..."></script>
</head>
<body>
    <nav><!-- Navigation --></nav>
    <main>{% block content %}{% endblock %}</main>
    <footer><!-- Version info --></footer>
</body>
</html>
```

### Custom Styles

The base template includes custom CSS for:
- Hero sections
- Card components
- Badge elements
- Consistent spacing

---

## Datastar Integration

### What is Datastar?

Datastar is a hypermedia framework that enables reactive web UIs without complex JavaScript:

- **Server-Sent Events (SSE)**: Real-time updates from server
- **Reactive Signals**: Data binding with `data-*` attributes
- **Backend-Driven**: Server controls UI updates
- **No Build Step**: Plain HTML + attributes

### Example Datastar Usage

**Server (Python)**:
```python
from datastar import ServerSentEventGenerator

@app.route("/stream-data")
def stream_data():
    sse = ServerSentEventGenerator()
    
    # Send data updates
    data = {"count": 42, "status": "active"}
    sse.merge_fragments({"#data-display": render_template("_data.html", **data)})
    
    return sse.response()
```

**Client (HTML)**:
```html
<!-- Initial state -->
<div id="data-display" data-store='{"count": 0}'>
    <p>Count: <span data-text="$count"></span></p>
</div>

<!-- Button to trigger update -->
<button data-on-click="$get('/stream-data')">
    Update
</button>
```

**Benefits**:
- No JavaScript frameworks (React, Vue, etc.)
- Server controls the UI
- Real-time updates out of the box
- Simpler to reason about

### Future Datastar Features

We'll use Datastar for:
- **Live LLM Streaming**: Stream agent responses as they generate
- **Trace Updates**: Real-time trace collection display
- **Form Validation**: Server-side validation with instant feedback
- **Multi-Panel Updates**: Update multiple UI sections simultaneously

---

## Testing

### Running Tests

```bash
# All tests
just test

# Flask tests only
just test-file tests/test_app.py

# Verbose output
just test-verbose
```

### Test Structure

```python
import pytest
from flask import Flask
from flask.testing import FlaskClient

@pytest.fixture
def app() -> Flask:
    """Create Flask app for testing."""
    settings = Settings(
        database={"url": "sqlite:///:memory:"}
    )
    return create_app(settings)

@pytest.fixture
def client(app: Flask) -> FlaskClient:
    """Create test client."""
    return app.test_client()

def test_index_route(client: FlaskClient):
    """Test home page."""
    response = client.get("/")
    assert response.status_code == 200
    assert b"Software Builder" in response.data
```

### Test Coverage

**Current**: 12 Flask tests (all passing ✅)

```
✅ test_app_creation
✅ test_app_config
✅ test_index_route
✅ test_health_route
✅ test_about_route
✅ test_404_error
✅ test_blueprints_registered
✅ test_error_handlers_registered
✅ test_templates_rendered
✅ test_navigation_links
✅ test_health_endpoint_json_structure
✅ test_app_with_custom_settings
```

---

## Adding New Routes

### 1. Create Route in Blueprint

```python
# src/software_builder/routes/sessions.py
from flask import Blueprint, render_template
import structlog

sessions_bp = Blueprint("sessions", __name__, url_prefix="/sessions")
logger = structlog.get_logger()

@sessions_bp.route("/")
def list_sessions():
    """List all sessions."""
    logger.info("sessions_list_accessed")
    
    # Query database
    from ..models import Session, get_session
    with get_session() as db:
        sessions = db.query(Session).all()
    
    return render_template("sessions/list.html", sessions=sessions)
```

### 2. Register Blueprint

```python
# src/software_builder/app.py
def register_blueprints(app: Flask):
    from .routes import main_bp, sessions_bp
    
    app.register_blueprint(main_bp)
    app.register_blueprint(sessions_bp)
```

### 3. Create Template

```html
<!-- templates/sessions/list.html -->
{% extends "base.html" %}

{% block title %}Sessions{% endblock %}

{% block content %}
<h1>Sessions</h1>

<table>
    <thead>
        <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Created</th>
            <th>Status</th>
        </tr>
    </thead>
    <tbody>
        {% for session in sessions %}
        <tr>
            <td>{{ session.id }}</td>
            <td><a href="/sessions/{{ session.id }}">{{ session.title }}</a></td>
            <td>{{ session.created_at }}</td>
            <td>{{ session.status }}</td>
        </tr>
        {% endfor %}
    </tbody>
</table>
{% endblock %}
```

### 4. Add Tests

```python
# tests/test_sessions.py
def test_sessions_list(client):
    """Test sessions list page."""
    response = client.get("/sessions")
    assert response.status_code == 200
```

---

## Development Workflow

### 1. Start Server

```bash
just serve
```

### 2. Make Changes

Edit routes, templates, or models. Flask auto-reloads in debug mode.

### 3. Check Logs

Structured logs appear in console:

```
[info     ] index_page_accessed
[warning  ] page_not_found         path=/old-url
[error    ] database_error         error=...
```

### 4. Run Tests

```bash
just test
```

### 5. Format & Lint

```bash
just format
just lint
```

---

## Performance Considerations

### Development

- **Debug mode**: Enabled for development
- **Auto-reload**: Restart on code changes
- **SQL logging**: Echo queries if configured

### Production

- **Workers**: Use `gunicorn` or `uwsgi`
- **Static files**: Serve with nginx
- **Caching**: Add Redis for session storage
- **Monitoring**: Use health endpoint

**Example Production Setup**:

```bash
# gunicorn with 4 workers
gunicorn -w 4 -b 0.0.0.0:5000 "software_builder.app:create_app()"
```

---

## Troubleshooting

### Issue: "Secret key must be set"

**Solution**: Set `SB_APP__SECRET_KEY` environment variable:
```bash
export SB_APP__SECRET_KEY=$(openssl rand -hex 32)
```

### Issue: "Database not initialized"

**Solution**: Run database initialization:
```bash
just db-init
```

### Issue: "Port already in use"

**Solution**: Use different port:
```bash
just serve --port 8080
```

### Issue: "Template not found"

**Solution**: Check template_folder in app factory:
```python
app = Flask(__name__, template_folder="../../templates")
```

---

## Next Steps

### Planned Features

1. **Session Management** - CRUD for coding sessions
2. **Trace Viewer** - Browse and filter traces
3. **Evaluation UI** - Annotate traces with pass/fail
4. **Datastar Streaming** - Real-time LLM responses
5. **Prompt Comparison** - Side-by-side version comparison

### Adding Datastar Features

See the [Datastar documentation](https://data-star.dev/) for:
- Server-Sent Events patterns
- Reactive signals
- Form handling
- Real-time updates

---

## Resources

- [Flask Documentation](https://flask.palletsprojects.com/)
- [Datastar Guide](https://data-star.dev/guide)
- [Pico.css Documentation](https://picocss.com/docs)
- [structlog Documentation](https://www.structlog.org/)

---

**Summary**: Flask application is production-ready with configuration integration, structured logging, error handling, and comprehensive tests. Ready to add feature-specific routes and Datastar integration!
