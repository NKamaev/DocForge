# MCP Tools Reference

The MCP server exposes 12 tools for Claude Code agents to manage tasks without touching files directly.

## Setup

Add to `~/.claude/mcp.json`:

```json
{
  "mcpServers": {
    "task-tracker": {
      "command": "java",
      "args": ["-jar", "/path/to/task-tracker-mcp.jar"],
      "env": {
        "TASKTRACKER_BASE_URL": "http://localhost:8765/api/v1",
        "TASKTRACKER_ACTOR": "mcp:claude-code"
      }
    }
  }
}
```

The backend must be running (`java -jar task-tracker.jar`) before the MCP server can serve requests.

---

## Tools

### `task_list`

List tasks with optional filters.

| Parameter | Type | Description |
|---|---|---|
| `status` | string? | TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED, CANCELLED |
| `type` | string? | feature, bug, refactor, research |
| `agent` | string? | Agent name, e.g. `/backend` |
| `parent_id` | string? | Show subtasks of a parent |
| `q` | string? | Full-text search |
| `limit` | integer? | Default 50 |
| `offset` | integer? | Pagination offset |

**Example:**
```
task_list { "status": "IN_PROGRESS" }
task_list { "agent": "/backend", "limit": 10 }
```

---

### `task_get`

Get a single task with all details (agents, tags, subtasks, dependencies, history, comments).

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Task ID, e.g. `TASK-007` |

---

### `task_create`

Create a new task. The ID is auto-generated if omitted (`TASK-NNN` or `BUG-NNN`).

| Parameter | Type | Description |
|---|---|---|
| `type` | string | feature, bug, refactor, research |
| `title` | string | Task title |
| `description` | string? | Markdown body |
| `parent_id` | string? | Parent task ID for subtasks |
| `agents` | string[]? | Agent names, e.g. `["/backend"]` |
| `tags` | string[]? | Tag names |

**Example:**
```
task_create {
  "type": "feature",
  "title": "Add export endpoint",
  "agents": ["/backend"],
  "description": "## Goal\nExport tasks to CSV."
}
```

---

### `task_update`

Update fields of an existing task (partial update).

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Task ID |
| `title` | string? | New title |
| `description` | string? | New description |
| `commit_hash` | string? | Git short hash |
| `github_issue` | string? | e.g. `#42` |
| `agents` | string[]? | Replace agent list |
| `tags` | string[]? | Replace tag list |

---

### `task_transition`

Change task status with business rule validation.

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Task ID |
| `to` | string | Target status |
| `comment` | string? | Reason for transition |
| `commit_hash` | string? | Required (or `allow_without_commit`) for → DONE |
| `allow_without_commit` | boolean? | Skip commit hash check |
| `force` | boolean? | Override transition guard (DONE→REVIEW, CANCELLED→TODO) |

**Allowed transitions:**
```
TODO        → IN_PROGRESS, BLOCKED, CANCELLED
IN_PROGRESS → REVIEW, BLOCKED, TODO, CANCELLED
REVIEW      → DONE, IN_PROGRESS, BLOCKED, CANCELLED
BLOCKED     → TODO, IN_PROGRESS, CANCELLED
DONE        → REVIEW  (force=true only)
CANCELLED   → TODO    (force=true only)
```

**Example:**
```
task_transition { "id": "TASK-007", "to": "DONE", "commit_hash": "a1b2c3d" }
```

---

### `task_delete`

Delete a task. DONE tasks require `force=true`.

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Task ID |
| `force` | boolean? | Delete even if status is DONE |

---

### `task_add_dependency`

Mark that task `id` depends on task `depends_on_id`. Cycle detection runs; HTTP 409 if a cycle would form.

| Parameter | Type | |
|---|---|---|
| `id` | string | The dependent task |
| `depends_on_id` | string | The task it depends on |

---

### `task_remove_dependency`

Remove a previously added dependency.

| Parameter | Type | |
|---|---|---|
| `id` | string | |
| `depends_on_id` | string | |

---

### `task_add_comment`

Add a comment to a task. Writes a `COMMENT_ADDED` history entry.

| Parameter | Type | |
|---|---|---|
| `id` | string | Task ID |
| `body` | string | Markdown comment body |
| `author` | string? | Defaults to the MCP actor |

---

### `task_history`

Return the audit log for a task (newest first).

| Parameter | Type | |
|---|---|---|
| `id` | string | Task ID |
| `limit` | integer? | Default 50 |

---

### `board_overview`

Return all tasks grouped by status — useful for a quick situational summary without extra parameters.

---

### `task_search`

Full-text search across id, title, and description.

| Parameter | Type | |
|---|---|---|
| `q` | string | Search query |
| `limit` | integer? | Default 20 |

**Example:**
```
task_search { "q": "collab" }
```

---

## Error handling

All tools return `isError: true` in the MCP response when the backend returns 4xx/5xx, with a human-readable message. The agent can reason about the error without crashing.

If the backend is not running, the error message is:
```
Task tracker is not running. Start it with: java -jar task-tracker.jar
```
