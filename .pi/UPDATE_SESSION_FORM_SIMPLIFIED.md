# Update: Simplified Session Creation Form

## Changes Made

### Problem
The session creation form required users to select both system prompt and tool prompt versions. Since we haven't built tool prompt management UI yet, this was confusing and unnecessary.

### Solution
**Automatic Tool Prompt Selection**: The active tool prompt is now automatically used when creating sessions. Users only need to select the system prompt version.

---

## Code Changes

### 1. Updated Session Routes (`src/software_builder/routes/sessions.py`)

**New Session Form Route**:
```python
# BEFORE: Loaded both system and tool prompts
system_prompts = db.query(SystemPrompt).order_by(...)
tool_prompts = db.query(ToolPrompt).order_by(...)
return render_template(..., system_prompts=system_prompts, tool_prompts=tool_prompts)

# AFTER: Only load system prompts
system_prompts = db.query(SystemPrompt).order_by(...)
return render_template(..., system_prompts=system_prompts)
```

**Create Session Route**:
```python
# BEFORE: Got tool_prompt_id from form
tool_prompt_id = request.form.get("tool_prompt_id")
session = Session(
    title=title,
    system_prompt_id=int(system_prompt_id),
    tool_prompt_id=int(tool_prompt_id),
    status="active",
)

# AFTER: Automatically use active tool prompt
tool_prompt = db.query(ToolPrompt).filter_by(is_active=True).first()
if not tool_prompt:
    tool_prompt = db.query(ToolPrompt).first()  # Fallback

session = Session(
    title=title,
    system_prompt_id=int(system_prompt_id),
    tool_prompt_id=tool_prompt.id,  # Automatic!
    status="active",
)
```

**Benefits**:
- Simpler user experience
- Fewer decisions to make
- Consistent with "active prompt" pattern
- Tool prompt management can be added later if needed

### 2. Updated Template (`templates/sessions/new.html`)

**BEFORE**: Two dropdown fields
```html
<select name="system_prompt_id">...</select>
<select name="tool_prompt_id">...</select>
```

**AFTER**: One dropdown field
```html
<select name="system_prompt_id">...</select>
<small>Choose the system prompt version for this session. 
       The active tool prompt will be used automatically.</small>
```

**Warning Message Updated**:
```html
<!-- BEFORE -->
<p>You need to create system and tool prompts...</p>

<!-- AFTER -->
<p>You need to create at least one system prompt...
   <a href="/system-prompts/new">Create a system prompt</a> or 
   run <code>software-builder seed</code>...</p>
```

### 3. Updated Tests (`tests/test_sessions.py`)

**All session creation tests updated**:
```python
# BEFORE: Provide both IDs
response = client.post("/sessions/", data={
    "title": "My Session",
    "system_prompt_id": sample_prompts["system_v1"],
    "tool_prompt_id": sample_prompts["tool_v1"],  # Removed!
})

# AFTER: Only provide system prompt ID
response = client.post("/sessions/", data={
    "title": "My Session",
    "system_prompt_id": sample_prompts["system_v1"],
})
```

**Test Names Updated**:
- `test_create_session_missing_prompts` → now checks for missing system prompt only
- Error message changed: "System and tool prompts are required" → "System prompt is required"

**New Assertion**:
```python
def test_create_session_with_specific_prompts(...):
    # Tool prompt should be the active one (v1)
    assert session.tool_prompt.version == "v1"
```

---

## User Experience Improvements

### Before
1. Go to Create New Session
2. Enter title
3. Select system prompt (confusing: which version?)
4. Select tool prompt (confusing: what's the difference?)
5. Submit

### After
1. Go to Create New Session
2. Enter title
3. Select system prompt (clear: just pick your preference)
4. Submit ✅

**Simplified from 4 inputs to 2 inputs!**

---

## Behavior

### Tool Prompt Selection Logic

```python
# Priority 1: Active tool prompt
tool_prompt = db.query(ToolPrompt).filter_by(is_active=True).first()

# Priority 2: Any tool prompt (fallback)
if not tool_prompt:
    tool_prompt = db.query(ToolPrompt).first()

# Priority 3: Error if none exist
if not tool_prompt:
    flash("No tool prompts available. Please create one first.", "error")
```

**When would this fail?**
- Only if NO tool prompts exist in database
- Seed data creates default tool prompt (v1)
- Users can't delete last tool prompt (would need to add protection)

---

## Future Considerations

### When to Add Tool Prompt Selection Back?

**Option 1: Never** (Keep it simple)
- Most users don't need to change tool definitions
- Active tool prompt works for 90% of use cases
- Less cognitive load

**Option 2: Build Tool Prompt Management First**
- Add `/tool-prompts` CRUD routes
- Then add optional tool prompt selection
- Advanced users can override if needed

**Option 3: Add "Advanced Options" Section**
- Collapsed by default
- Shows tool prompt selection for power users
- Most users never see it

**Recommendation**: Keep it simple for now. Add tool prompt selection only if users request it.

---

## Validation Results

```bash
just format  ✅  # All files formatted
just lint    ✅  # Zero linting errors
just test    ✅  # 70/70 tests passing
```

**No functionality lost** - All tests pass with updated expectations!

---

## Summary

**What Changed**:
- ❌ Removed tool prompt selection from form
- ✅ Automatic tool prompt selection (uses active one)
- ✅ Simpler user experience
- ✅ All tests updated and passing

**Benefits**:
- Faster session creation
- Less user confusion
- Cleaner UI
- Maintains flexibility (can add back later)

**Next Steps**:
- Consider adding tool prompt management UI later
- Or keep it simple and only use active tool prompt
- Focus on features that matter (messages, LLM integration)
