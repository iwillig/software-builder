# Data Model Design: Personal LLM Coding Agent

This document defines the data model for a **single-user, local LLM coding agent** with persistent conversation history and long-term memory.

## Design Principles

1. **Start Minimal**: Focus on sessions, messages, and memories
2. **Built for Growth**: Schema supports future entities (tasks, file snapshots, users)
3. **Single User**: No authentication, multi-tenancy, or user isolation needed
4. **Everything Persists**: Conversations, memories, context - nothing ephemeral
5. **Clojure Native**: EDN everywhere, Datalog queries, no impedance mismatch

---

## Entity Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        CORE ENTITIES                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐           │
│  │  Session │────▶│ Messages │     │ Memories │           │
│  │ (thread) │     │ (turns)  │     │(knowledge)│           │
│  └──────────┘     └──────────┘     └──────────┘           │
│        │                               │                    │
│        │ has many                      │ references         │
│        ▼                               ▼                    │
│  ┌──────────┐                    ┌──────────┐              │
│  │Project   │                    │ Entities │              │
│  │(context) │                    │(concepts)│              │
│  └──────────┘                    └──────────┘              │
│        ▲                                                    │
│        │ embedded in                                       │
│  ┌──────────┐                                              │
│  │ File     │                                              │
│  │ Snapshots│                                              │
│  └──────────┘                                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Entity Responsibilities

| Entity | Purpose | Cardinality |
|--------|---------|-------------|
| **Session** | Conversation thread, checkpoint state | One per conversation |
| **Message** | Individual user/assistant turn | Many per session |
| **Memory** | Persisted knowledge with decay | Cross-session, many |
| **Entity** | Extracted concepts (functions, libs) | Referenced by memories |
| **Project** | Root directory + config | Scoped to sessions |
| **File Snapshot** | Code state at point in time | Many per session |

---

## Phase 1: MVP Schema (Start Here)

### Schema Definition

```clojure
(ns agent.data-model
  (:require [datalevin.core :as d]))

;; ═══════════════════════════════════════════════════════════════
;; PHASE 1: CORE SCHEMA
;; Minimal viable data model for personal coding agent
;; ═══════════════════════════════════════════════════════════════

(def phase-1-schema
  {
   ;; ─────────── SESSION ───────────
   ;; A conversation thread. Think: one "coding session" in your editor
   :session/id {:db/valueType :db.type/uuid
                :db/unique :db.unique/identity
                :db/doc "Unique session identifier"}
   
   :session/project-path {:db/valueType :db.type/string
                          :db/doc "Working directory for this session"}
   
   :session/title {:db/valueType :db.type/string
                   :db/doc "Auto-generated summary of session purpose"}
   
   :session/status {:db/valueType :db.type/keyword
                    :db/doc ":active | :paused | :archived"}
   
   :session/checkpoint {:db/valueType :db.type/string
                        :db/doc "Serialized LLM state for resumption"}
   
   :session/meta {:db/valueType :db.type/string
                 :db/doc "EDN map: git branch, active files, tool state"}
   
   :session/created-at {:db/valueType :db.type/instant
                        :db/doc "When session started"}
   
   :session/ended-at {:db/valueType :db.type/instant
                      :db/doc "When session ended (if archived)"}
   
   :session/tags {:db/valueType :db.type/keyword
                  :db/cardinality :db.cardinality/many
                  :db/doc "User-defined or inferred tags: :auth :refactor :bug-fix"}
   
   ;; ─────────── MESSAGE ───────────
   ;; Each interaction turn. Immutable, append-only.
   :message/id {:db/valueType :db.type/uuid
                :db/unique :db.unique/identity}
   
   :message/session {:db/valueType :db.type/ref
                     :db/doc "Links to parent session"}
   
   :message/role {:db/valueType :db.type/keyword
                 :db/doc ":user | :assistant | :system | :tool"}
   
   :message/content {:db/valueType :db.type/string
                    :db/doc "Raw message text"}
   
   :message/content-type {:db/valueType :db.type/keyword
                         :db/doc ":text | :code | :tool-call | :tool-result"}
   
   :message/sequence {:db/valueType :db.type/long
                      :db/doc "Order within session (0, 1, 2...)"}
   
   :message/timestamp {:db/valueType :db.type/instant}
   
   :message/model {:db/valueType :db.type/string
                  :db/doc "LLM used: claude-3-5-sonnet, gpt-4, etc"}
   
   :message/token-count {:db/valueType :db.type/long
                        :db/doc "Total tokens for cost tracking"}
   
   ;; For tool/function calling
   :message/tool-call {:db/valueType :db.type/string
                       :db/doc "EDN: tool name and arguments"}
   
   :message/tool-result {:db/valueType :db.type/string
                         :db/doc "EDN: tool execution result"}
   
   ;; ─────────── MEMORY ───────────
   ;; Persisted knowledge with decay tracking
   :memory/id {:db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   
   :memory/type {:db/valueType :db.type/keyword
                 :db/doc ":interaction|:episode|:theme|:archetype|:fact|:preference"}
   
   :memory/session {:db/valueType :db.type/ref
                   :db/doc "Source session (nullable for cross-session)"}
   
   :memory/content {:db/valueType :db.type/string
                    :db/doc "Raw memory text"}
   
   :memory/summary {:db/valueType :db.type/string
                    :db/doc "Condensed version for quick retrieval"}
   
   ;; Decay tracking (see memory-decay-and-consolidation.md)
   :memory/initial-strength {:db/valueType :db.type/float
                             :db/doc "Starting memory strength (0.0-1.0)"}
   
   :memory/current-strength {:db/valueType :db.type/float
                             :db/doc "Calculated: μ * e^(-αt)"}
   
   :memory/decay-rate {:db/valueType :db.type/float
                      :db/doc "α: 0.8=raw, 0.4=episode, 0.05=archetype"}
   
   :memory/last-recall {:db/valueType :db.type/instant
                       :db/doc "When last retrieved"}
   
   :memory/last-reviewed {:db/valueType :db.type/instant
                          :db/doc "When human+AI reviewed"}
   
   :memory/review-count {:db/valueType :db.type/long}
   
   ;; Hierarchy (for recursive summarization)
   :memory/parent {:db/valueType :db.type/ref
                   :db/doc "Higher-level summary this feeds into"}
   
   :memory/level {:db/valueType :db.type/long
                  :db/doc "0=raw, 1=episode, 2=theme, 3=archetype"}
   
   :memory/source-messages {:db/valueType :db.type/ref
                            :db/cardinality :db.cardinality/many
                            :db/doc "Messages this memory derived from"}
   
   ;; Context for retrieval
   :memory/tags {:db/valueType :db.type/keyword
                 :db/cardinality :db.cardinality/many}
   
   ;; Vector embedding (for semantic search)
   :memory/embedding {:db/valueType :db.type/f64
                      :db/cardinality :db.cardinality/many
                      :db/index-type :db.index/vector
                      :db/dimension 1536
                      :db/metric-type :db.metric/cosine
                      :db/doc "Vector embedding of content"}})
```

---

## Phase 1: Usage Examples

### Initializing the Database

```clojure
(ns agent.db
  (:require [datalevin.core :as d])
  (:import (java.util UUID)
           (java.time Instant Duration)))

(def db-path (str (System/getProperty "user.home") ".local/share/coding-agent/db"))

(defn get-conn []
  (d/get-conn db-path phase-1-schema))

(defn close-conn [conn]
  (d/close conn))
```

### Creating a Session

```clojure
(defn create-session [conn project-path]
  (let [session-id (java.util.UUID/randomUUID)
        now (java.time.Instant/now)]
    (d/transact! conn
      [{:session/id session-id
        :session/project-path project-path
        :session/title (str "Session at " project-path)  ;; auto-generated later
        :session/status :active
        :session/checkpoint "{}"  ;; initial empty state
        :session/meta (pr-str {:git-branch (get-current-branch project-path)
                              :active-files []
                              :tool-registry []})
        :session/created-at now}])
    session-id))
```

### Storing a Message

```clojure
(defn store-message 
  "Store a message and auto-update session checkpoint"
  [conn session-id role content & {:keys [model tool-call tool-result]}]
  (let [msg-id (java.util.UUID/randomUUID)
        now (java.time.Instant/now)
        db (d/db conn)
        ;; Get next sequence number
        last-seq (or (->> (d/q '[:find (max ?seq)
                                 :in $ ?session-id
                                 :where [?m :message/session ?s]
                                        [?m :message/sequence ?seq]
                                        [?s :session/id ?session-id]]
                               db session-id)
                          ffirst)
                     -1)]
    (d/transact! conn
      (concat
        ;; Store the message
        [{:message/id msg-id
          :message/session [:session/id session-id]
          :message/role role
          :message/content content
          :message/sequence (inc last-seq)
          :message/timestamp now}]
        ;; Optional fields
        (when model
          [[:db/add [:message/id msg-id] :message/model model]])
        (when tool-call
          [[:db/add [:message/id msg-id] :message/tool-call (pr-str tool-call)]])
        (when tool-result
          [[:db/add [:message/id msg-id] :message/tool-result (pr-str tool-result)]])))
    msg-id))
```

### Retrieving Session Messages

```clojure
(defn get-session-messages 
  "Get all messages for a session, ordered by sequence"
  [db session-id]
  (->> (d/q '[:find ?msg (pull ?m [:message/role 
                                    :message/content 
                                    :message/sequence
                                    :message/timestamp
                                    :message/tool-call])
              :in $ ?session-id
              :where [?s :session/id ?session-id]
                     [?m :message/session ?s]
                     [?m :message/sequence ?seq]
                     [(identity ?m) ?msg]]
            db session-id)
       (sort-by #(-> % second :message/sequence))
       (map second)))
```

### Creating a Memory

```clojure
(defn create-memory
  "Create a memory from messages"
  [conn session-id content & {:keys [type level parent-id source-ids]
                             :or {type :interaction
                                  level 0}}]
  (let [mem-id (java.util.UUID/randomUUID)
        now (java.time.Instant/now)
        decay-rates {:interaction 0.8
                     :episode 0.4
                     :theme 0.2
                     :archetype 0.05
                     :fact 0.1
                     :preference 0.05}]
    (d/transact! conn
      (filter some?
        [{:memory/id mem-id
          :memory/session [:session/id session-id]
          :memory/type type
          :memory/content content
          :memory/initial-strength 1.0
          :memory/current-strength 1.0
          :memory/decay-rate (get decay-rates type 0.5)
          :memory/last-recall now
          :memory/level level}
         
         (when parent-id
           [:db/add [:memory/id mem-id] :memory/parent [:memory/id parent-id]])
         
         (when source-ids
           (for [sid source-ids]
             [:db/add [:memory/id mem-id] :memory/source-messages [:message/id sid]]))]))
    mem-id))
```

---

## Phase 2: Extended Schema (Add Later)

### Entities to Add

```clojure
(def phase-2-additions
  {
   ;; ─────────── ENTITY (Knowledge Graph) ───────────
   ;; Extracted concepts: functions, namespaces, libraries, patterns
   :entity/id {:db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   
   :entity/name {:db/valueType :db.type/string}
   
   :entity/type {:db/valueType :db.type/keyword
                 :db/doc ":function :namespace :library :pattern :concept"}
   
   :entity/definition {:db/valueType :db.type/string
                       :db/doc "Code or text definition"}
   
   :entity/mentions {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/many
                     :db/doc "Memories mentioning this entity"}
   
   ;; ─────────── TASK ───────────
   ;; Async background operations
   :task/id {:db/valueType :db.type/uuid
             :db/unique :db.unique/identity}
   
   :task/session {:db/valueType :db.type/ref}
   
   :task/type {:db/valueType :db.type/keyword
               :db/doc ":code-search :file-edit :test-run :lint :format"}
   
   :task/status {:db/valueType :db.type/keyword
                 :db/doc ":pending :running :completed :failed"}
   
   :task/description {:db/valueType :db.type/string}
   
   :task/result {:db/valueType :db.type/string}  ;; EDN
   
   :task/scheduled-for {:db/valueType :db.type/instant}
   
   :task/completed-at {:db/valueType :db.type/instant}
   
   ;; ─────────── FILE SNAPSHOT ───────────
   ;; Code state at specific points
   :file-snapshot/id {:db/valueType :db.type/uuid
                       :db/unique :db.unique/identity}
   
   :file-snapshot/session {:db/valueType :db.type/ref}
   
   :file-snapshot/path {:db/valueType :db.type/string}
   
   :file-snapshot/content {:db/valueType :db.type/string}
   
   :file-snapshot/hash {:db/valueType :db.type/string}
   
   :file-snapshot/timestamp {:db/valueType :db.type/instant}})
```

### Migration Path

```clojure
;; Datalevin supports schema evolution without migrations
;; Just add new attributes:

(defn extend-schema [conn new-attributes]
  (d/transact! conn (map (fn [[attr schema]]
                            {:db/ident attr
                             :db/valueType (:db/valueType schema)
                             :db/cardinality (:db/cardinality schema :db.cardinality/one)
                             :db/unique (:db/unique schema)
                             :db/index-type (:db/index-type schema)
                             :db/doc (:db/doc schema)})
                          new-attributes)))

;; Usage: (extend-schema conn phase-2-additions)
```

---

## Query Patterns

### 1. Active Sessions

```clojure
(defn get-active-sessions [db]
  (d/q '[:find (pull ?s [:session/id 
                         :session/title 
                         :session/project-path 
                         :session/created-at])
         :where [?s :session/status :active]]
       db))
```

### 2. Recent Messages (for context window)

```clojure
(defn get-recent-messages [db session-id n]
  (->> (get-session-messages db session-id)
       (take-last n)))
```

### 3. Memories by Tag

```clojure
(defn get-memories-by-tag [db tag]
  (d/q '[:find (pull ?m [:memory/id :memory/content :memory/current-strength])
         :in $ ?tag
         :where [?m :memory/tags ?tag]]
       db tag))
```

### 4. Calculate Memory Priority

```clojure
(defn calculate-priorities [db]
  "Get memories sorted by need for review (lowest strength first)"
  (->> (d/q '[:find ?m :where [?m :memory/id _]] db)
       (map #(d/entity db (first %)))
       (sort-by :memory/current-strength)))
```

### 5. Session Summary Stats

```clojure
(defn session-stats [db session-id]
  (let [messages (get-session-messages db session-id)]
    {:total-messages (count messages)
     :user-messages (count (filter #(= :user (:message/role %)) messages))
     :assistant-messages (count (filter #(= :assistant (:message/role %)) messages))
     :tool-calls (count (filter :message/tool-call messages))
     :duration-hours (when-let [last-msg (last messages)]
                       (/ (- (.toEpochMilli (:message/timestamp last-msg))
                             (.toEpochMilli (:session/created-at 
                                             (d/entity db [:session/id session-id]))))
                          3600000.0))}))
```

---

## Data Levolution Roadmap

### Version 1.0 (MVP - Start Here)
- [x] Session entity
- [x] Message entity  
- [x] Memory entity with decay tracking
- [x] Vector embeddings (via Datalevin)

### Version 1.1 (Memory Consolidation)
- [ ] Hierarchical memory (parent/child)
- [ ] Automatic episode summarization
- [ ] Review queue UI
- [ ] Memory decay calculation jobs

### Version 1.2 (Context Expansion)
- [ ] Entity extraction
- [ ] Knowledge graph
- [ ] File snapshots
- [ ] Relevant file detection

### Version 1.3 (Operations)
- [ ] Task queue
- [ ] Background jobs
- [ ] Async tool execution
- [ ] Result caching

---

## Schema Quick Reference

| Entity | Key Attributes | Relationships |
|--------|---------------|---------------|
| **Session** | `id`, `project-path`, `status`, `created-at` | Has many Messages |
| **Message** | `id`, `role`, `content`, `sequence`, `timestamp` | Belongs to Session |
| **Memory** | `id`, `type`, `content`, `current-strength`, `decay-rate` | Belongs to Session, has parent |

### Minimal Required Fields

For any transaction, you only need:

```clojure
;; Session
{:session/id (UUID)
 :session/status :active
 :session/created-at (Instant)}

;; Message
{:message/id (UUID)
 :message/session [:session/id session-uuid]
 :message/role :user
 :message/content "Hello"
 :message/sequence 0}

;; Memory
{:memory/id (UUID)
 :memory/type :interaction
 :memory/content "User said..."
 :memory/initial-strength 1.0
 :memory/decay-rate 0.8}
```

---

## File Locations

Recommended structure:

```
~/.local/share/coding-agent/
├── datalevin.db/          # Main database
├── backups/
│   ├── datalevin-20240222.db
│   └── datalevin-20240223.db
└── config.edn             # Agent configuration
```

```clojure
(def db-path
  (str (System/getProperty "user.home")
       "/.local/share/coding-agent/datalevin.db"))
```

---

## Next Steps

1. **Implement Phase 1**: Start with Session + Message
2. **Add Messages**: Build conversation storage
3. **Implement Decay**: Calculate memory strength
4. **Add Vector Search**: Enable semantic retrieval
5. **Build Review UI**: Human-in-the-loop consolidation

See also:
- `memory-decay-and-consolidation.md` - How decay works
- `llm-agent-memory-system.md` - Memory architecture overview
- Datalevin docs: https://github.com/juji-io/datalevin
