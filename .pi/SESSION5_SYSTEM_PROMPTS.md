# Session 5: System Prompt Management (2026-02-08)

## ✅ Completed

### 1. System Prompt Management Routes

Created comprehensive CRUD for system prompts (`src/software_builder/routes/system_prompts.py`):

**Routes Implemented**:
- ✅ `GET /system-prompts/` - List all system prompts (ordered by created_at desc)
- ✅ `GET /system-prompts/new` - Form to create new system prompt
- ✅ `POST /system-prompts/` - Create new system prompt with validation
- ✅ `GET /system-prompts/<id>` - View system prompt details
- ✅ `POST /system-prompts/<id>/activate` - Set prompt as active (deactivates others)
- ✅ `POST /system-prompts/<id>/delete` - Delete prompt (only if unused)

**Features**:
- Blueprint architecture (`/system-prompts` prefix)
- Structured logging on all actions
- Flash messages for user feedback
- Form validation (version, name, content required)
- Version uniqueness check
- Activation system (only one active at a time)
- Deletion protection (can't delete if used by sessions)
- Session count display

**Code Example**:
```python
@system_prompts_bp.route("/", methods=["POST"])
def create_prompt():
    """Create a new system prompt."""
    version = request.form.get("version", "").strip()
    name = request.form.get("name", "").strip()
    content = request.form.get("content", "").strip()
    is_active = request.form.get("is_active") == "on"

    # If setting as active, deactivate others
    if is_active:
        db.query(SystemPrompt).update({"is_active": False})

    prompt = SystemPrompt(
        version=version,
        name=name,
        content=content,
        is_active=is_active,
    )
    db.add(prompt)
    db.commit()
```

### 2. Template System

Created 3 comprehensive templates for system prompt management:

#### A. System Prompt List (`templates/system_prompts/list.html`)
**Features**:
- Table display with all prompt details
- Status badges (Active/Inactive with color coding)
- Session count per prompt
- Created timestamp
- View and Activate actions
- "No prompts" empty state
- Responsive table layout
- Help text explaining active prompts

**Columns**:
- Version (clickable to detail view)
- Name
- Status badge (Active/Inactive)
- Created timestamp
- Session count
- Action buttons (View, Activate)

#### B. New System Prompt Form (`templates/system_prompts/new.html`)
**Features**:
- Version input field
- Name input field
- Large textarea for prompt content (15 rows)
- Checkbox for "Set as active"
- Helpful descriptions for each field
- Tips section for writing good prompts
- Cancel button

**Validation**:
- Required fields enforced (HTML5)
- Server-side validation in route
- Version uniqueness check
- Flash messages for errors

**Tips Section**:
- Be specific
- Set expectations
- Include guidelines
- Consider tone
- Test iteratively

#### C. System Prompt Detail (`templates/system_prompts/detail.html`)
**Features**:
- Prompt header with version and name
- Status badge (Active/Inactive)
- Information card (version, name, status, created date)
- Session count with link to sessions
- Full prompt content display (pre-formatted)
- Activate button (if not active)
- Delete button (if not in use)
- Protection messages (can't delete if used)
- Back to list button

**Content Display**:
- Pre-wrapped text for readability
- Bordered box with background
- Full prompt visible

### 3. Active Prompt System

**Key Feature**: Only one system prompt can be active at a time

**Implementation**:
```python
# When activating a prompt
db.query(SystemPrompt).update({"is_active": False})  # Deactivate all
prompt.is_active = True  # Activate selected
db.commit()

# When creating a prompt as active
if is_active:
    db.query(SystemPrompt).update({"is_active": False})
```

**Benefits**:
- Clear which prompt is default
- New sessions automatically use active prompt
- Easy to switch between prompt versions
- Compare performance across versions

### 4. Deletion Protection

**Feature**: Prevent deletion of prompts in use

**Implementation**:
```python
session_count = db.query(Session).filter_by(system_prompt_id=prompt_id).count()
if session_count > 0:
    flash(f"Cannot delete: {session_count} session(s) use this prompt", "error")
    return redirect(...)
```

**UI Feedback**:
- Delete button disabled if prompt in use
- Message explains why (X sessions use this)
- Session count displayed in detail view

### 5. Session Count Display

**Feature**: Show how many sessions use each prompt

**Implementation**:
```python
# In list route
session_counts = {}
for prompt in prompts:
    count = db.query(Session).filter_by(system_prompt_id=prompt.id).count()
    session_counts[prompt.id] = count

# Pass to template
return render_template(..., session_counts=session_counts)
```

**Avoids DetachedInstanceError**:
- Count computed within DB session
- Passed as separate dict to template
- No lazy loading of relationships after session closes

### 6. Navigation Update

Updated base template to include System Prompts:

```html
<li><a href="/">Home</a></li>
<li><a href="/sessions">Sessions</a></li>
<li><a href="/system-prompts">Prompts</a></li>  <!-- NEW -->
<li><a href="/traces">Traces</a></li>
<li><a href="/evals">Evaluations</a></li>
<li><a href="/about">About</a></li>
```

### 7. Comprehensive Testing

Created 18 system prompt tests (`tests/test_system_prompts.py`):

**Tests**:
```
✅ test_list_prompts_empty                        - Empty state
✅ test_list_prompts_with_data                    - Display prompts
✅ test_new_prompt_form                           - Form renders
✅ test_create_prompt_success                     - Create works
✅ test_create_prompt_missing_version             - Validation
✅ test_create_prompt_missing_name                - Validation
✅ test_create_prompt_missing_content             - Validation
✅ test_create_prompt_duplicate_version           - Uniqueness check
✅ test_view_prompt                               - Detail view
✅ test_view_prompt_not_found                     - 404 handling
✅ test_activate_prompt                           - Activation works
✅ test_delete_prompt_unused                      - Deletion works
✅ test_delete_prompt_in_use                      - Protection works
✅ test_delete_prompt_not_found                   - 404 handling
✅ test_system_prompts_blueprint_registered       - Blueprint loaded
✅ test_create_prompt_as_active_deactivates_others - Active switching
✅ test_prompt_list_ordering                      - Newest first
✅ test_view_prompt_shows_session_count           - Count display
```

**Test Coverage**:
- All CRUD operations
- Validation scenarios
- Active prompt switching
- Deletion protection
- Error handling
- Session count display

### 📊 Statistics

**Files Created**: 5
- 1 Routes module (`system_prompts.py` - 180 lines)
- 3 Templates (500 lines total)
- 1 Test file (400 lines)

**Lines of Code**: ~1,100+

**Test Coverage**: 70/70 tests passing ✅
- System Prompt tests: 18 (new)
- Session tests: 15
- Flask tests: 12
- Config tests: 17
- Model tests: 8

### 🎯 Key Features

1. **Complete CRUD**
   - List prompts with status
   - Create prompts with validation
   - View prompts with details
   - Activate prompts (one at a time)
   - Delete prompts (with protection)

2. **Version Management**
   - Unique version identifiers
   - Version history
   - Easy switching between versions
   - Track which sessions use which versions

3. **Active Prompt System**
   - Only one active at a time
   - New sessions use active prompt
   - Visual indicators (badges)
   - Easy activation

4. **Safety Features**
   - Can't delete prompts in use
   - Version uniqueness enforcement
   - Validation on all fields
   - Clear error messages

5. **User Experience**
   - Status badges with colors
   - Session count display
   - Tips for writing prompts
   - Empty states
   - Confirmation messages

### ✅ Validation Status

```bash
just format  ✅  # All files formatted
just lint    ✅  # Zero linting errors
just test    ✅  # 70/70 tests passing
```

### 🏗️ Architecture Decisions

1. **Only One Active Prompt**
   - Simplifies UI (no confusion about which to use)
   - New sessions automatically use active
   - Easy to compare versions (switch active, test)

2. **Session Count in List**
   - Computed within DB session
   - Passed as separate dict
   - Avoids DetachedInstanceError

3. **Deletion Protection**
   - Prevents data integrity issues
   - Clear messaging to user
   - Shows session count

4. **Version Uniqueness**
   - Prevents confusion
   - Makes referencing easy
   - Enforced at DB level (unique constraint)

5. **Large Textarea for Content**
   - Prompts can be long
   - 15 rows gives good starting size
   - Resizable by user

### 📈 Usage Workflow

1. **Create System Prompt**:
   ```bash
   # Navigate to /system-prompts
   # Click "New System Prompt"
   # Enter version: v2
   # Enter name: Concise Code Assistant
   # Enter content: You are a concise...
   # Check "Set as active" if default
   # Submit
   ```

2. **View Prompts**:
   - See all prompts in table
   - Status badges show active/inactive
   - Session count shows usage
   - Click to view details

3. **Activate Prompt**:
   - Click "Activate" button
   - Confirmation message
   - Other prompts automatically deactivated

4. **Delete Prompt**:
   - Only if not used by any sessions
   - Confirmation dialog
   - Clear error if in use

### 🔄 Comparison with Sessions

**Similarities**:
- Blueprint architecture
- List/Create/View/Delete routes
- Flash messages
- Form validation
- Comprehensive tests

**Differences**:
- System prompts have activation system
- System prompts have deletion protection
- System prompts show session count
- Sessions link to prompts (relationship)

### 🚀 Next Steps

With system prompt management complete:

#### Option 1: Tool Prompt Management
- Mirror system prompt CRUD
- `/tool-prompts` routes
- JSON editor for tools
- Same activation system

#### Option 2: Message Creation
- Add messages to sessions
- User input form
- Assistant response (manual)
- Real-time updates

#### Option 3: LLM Integration
- Connect to OpenAI/Anthropic
- Use active prompts automatically
- Generate responses
- Token tracking

#### Option 4: Prompt Comparison
- Side-by-side view
- Performance metrics
- A/B testing interface
- Version diff view

#### Option 5: Prompt Templates
- Pre-built prompt library
- One-click install
- Community sharing
- Categories

### 🎓 What We Learned

1. **Active Pattern**: Only one active record is common pattern
2. **Deletion Protection**: Essential for data integrity
3. **Session Counting**: Compute in DB session, not in template
4. **Status Badges**: Visual indicators improve UX
5. **Version Management**: Unique identifiers simplify tracking

### ⚠️ Considerations

1. **No Edit Feature**: Create new versions instead (immutability)
2. **No Diff View**: Could add to compare versions
3. **No Search**: Will add when many prompts exist
4. **No Categories**: Could add for organization
5. **No Import/Export**: Could add for sharing

### 📚 Benefits for Users

**Version Control**:
- Track all prompt versions
- Compare performance
- Easy rollback (just activate old version)

**Safety**:
- Can't accidentally delete active prompts
- Protection for prompts in use
- Clear error messages

**Experimentation**:
- Create multiple versions
- Switch between them easily
- Test and iterate quickly

**Organization**:
- See all prompts at a glance
- Status badges for clarity
- Session count shows usage

---

## Combined Progress (Sessions 1-5)

### 📦 Project Status

**Total Sessions**: 5  
**Total Files Created**: 80+  
**Total Lines of Code**: ~8,700+  
**Total Tests**: 70 (all passing ✅)  
**Features Complete**: 2 (Sessions, System Prompts)  

### ✅ Completed Components

1. ✅ **Project Setup** - uv, pyproject.toml, justfile
2. ✅ **Data Model** - 10 SQLAlchemy models
3. ✅ **Database Migrations** - Alembic
4. ✅ **Configuration System** - pydantic-settings
5. ✅ **Flask Web Application** - Routes, templates, logging
6. ✅ **Session Management** - Complete CRUD
7. ✅ **System Prompt Management** - Complete CRUD with activation
8. ✅ **Testing Infrastructure** - 70 comprehensive tests
9. ✅ **Seed Data** - Default prompts
10. ✅ **Documentation** - 7 comprehensive guides

### 🎯 Features Complete

**Feature 1**: ✅ Session Management
- Create/view/list/delete sessions
- Prompt version selection
- Message display

**Feature 2**: ✅ System Prompt Management
- Create/view/list/activate/delete prompts
- Version management
- Active prompt system
- Deletion protection

**Ready for**: Tool prompts, Message creation, LLM integration

---

**Session Duration**: ~1.5 hours  
**Validation Status**: ✅ All checks passing  
**Test Status**: ✅ 70/70 tests passing  
**Server Status**: ✅ Running with 2 complete features  
**Documentation**: ✅ Comprehensive  
**Feature Status**: ✅ System Prompt Management Complete!
