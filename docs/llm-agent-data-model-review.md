# LLM Agent Data Model Review - Datalevin Edition

This document reviews best practices for data modeling in LLM coding agents using **Datalevin**, a Datalog database with vector capabilities.

---

## Why Datalevin for LLM Agents?

Datalevin combines multiple capabilities needed for agent memory:

| Feature | Traditional (SQL+Vector DB) | Datalevin |
|---------|---------------------------|-----------|
| **Relational data** | SQL JOINs | Datalog implicit joins |
| **Vector search** | Separate DB (pgvector/etc) | Built-in SIMD-accelerated |
| **Time-series** | Time-based indexes | Instant type, range queries |
| **Hierarchical data** | Recursive CTEs | Native recursive queries |
| **Schema flexibility** | ALTER TABLE migrations | Schema-free for EDN blobs |
| **Deployment** | External service | Embedded, no server |
| **Clojure interop** | JDBC/CLJ wrapper | Native EDN |

---

## Core Entities in Datalevin

Based on research across LLM agent architectures, the fundamental entities map to these Datalog schemas:

```clojure
(ns agent.schema
  (:require [datalevin.core :as d]))

(def llm-agent-schema
  {;; ========== AGENT ==========
   ;; The AI system configuration
   :agent/id {:db/valueType :db.type/uuid
              :db/unique :db.unique/identity}
   :agent/name {:db/valueType :db.type/string}
   :agent/system-prompt {:db/valueType :db.type/string}
   :agent/config {:db/valueType :db.type/string}  ;; EDN map
   :agent/tools {:db/valueType :db.type/string  ;; JSON array of tool defs
                 :db/cardinality :db.cardinality/many}
   :agent/created-at {:db/valueType :db.type/instant}
   
   ;; ========== USER ==========
   ;; Human operator of the agent
   :user/id {:db/valueType :db.type/uuid
             :db/unique :db.unique/identity}
   :user/name {:db/valueType :db.type/string}
   :user/preferences {:db/valueType :db.type/string}  ;; EDN preferences
   :user/active-session {:db/valueType :db.type/ref}  ;; current session
   
   ;; ========== PROJECT ==========
   ;; Scope boundary for coding work
   :project/id {:db/valueType :db.type/uuid
                :db/unique :db.unique/identity}
   :project/name {:db/valueType :db.type/string}
   :project/path {:db/valueType :db.type/string}
   :project/root-files {:db/valueType :db.type/string
                        :db/cardinality :db.cardinality/many}
   :project/config {:db/valueType :db.type/string}  ;; EDN project config
   
   ;; ========== SESSION ==========
   ;; A conversation thread with state
   :session/id {:db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   :session/agent {:db/valueType :db.type/ref}
   :session/user {:db/valueType :db.type/ref}
   :session/project {:db/valueType :db.type/ref}
   :session/title {:db/valueType :db.type/string}  ;; auto-generated summary
   :session/status {:db/valueType :db.type/keyword}  ;; :active :paused :ended
   :session/checkpoint {:db/valueType :db.type/string}  ;; serialized state
   :session/meta {:db/valueType :db.type/string}  ;; EDN metadata
   :session/created-at {:db/valueType :db.type/instant}
   :session/ended-at {:db/valueType :db.type/instant}
   
   ;; ========== MESSAGE ==========
   ;; Individual interaction turn
   :message/id {:db/valueType :db.type/uuid
                :db/unique :db.unique/identity}
   :message/session {:db/valueType :db.type/ref}
   :message/role {:db/valueType :db.type/keyword}  ;; :user :assistant :system :tool
   :message/content {:db/valueType :db.type/string}
   :message/parts {:db/valueType :db.type/string}  ;; EDN content parts
   :message/timestamp {:db/valueType :db.type/instant}
   :message/sequence {:db/valueType :db.type/long}  ;; order in session
   :message/model {:db/valueType :db.type/string}
   :message/token-count {:db/valueType :db.type/long}
   :message/latency-ms {:db/valueType :db.type/long}
   :message/tool-calls {:db/valueType :db.type/string}  ;; EDN
   :message/tool-results {:db/valueType :db.type/string}  ;; EDN
   
   ;; ========== TASK ==========
   ;; Background operations
   :task/id {:db/valueType :db.type/uuid
             :db/unique :db.unique/identity}
   :task/session {:db/valueType :db.type/ref}
   :task/type {:db/valueType :db.type/keyword}  ;; :code-search :file-edit :test-run
   :task/status {:db/valueType :db.type/keyword}  ;; :pending :running :completed :failed
   :task/priority {:db/valueType :db.type/long}
   :task/description {:db/valueType :db.type/string}
   :task/scheduled-for {:db/valueType :db.type/instant}
   :task/started-at {:db/valueType :db.type/instant}
   :task/completed-at {:db/valueType :db.type/instant}
   :task/result {:db/valueType :db.type/string}  ;; EDN result data
   :task/error {:db/valueType :db.type/string}
   :task/retry-count {:db/valueType :db.type/long}
   
   ;; ========== MEMORY (Core Innovation) ==========
   ;; Persistent knowledge with decay
   :memory/id {:db/valueType :db.type/uuid
              :db/unique :db.unique/identity}
   :memory/session {:db/valueType :db.type/ref}
   :memory/type {:db/valueType :db.type/keyword}  ;; See memory types below
   :memory/content {:db/valueType :db.type/string}
   :memory/summary {:db/valueType :db.type/string}  ;; condensed version
   :memory/importance {:db/valueType :db.type/float}  ;; 0.0-1.0
   
   ;; Decay tracking (for our "never forget" system)
   :memory/initial-strength {:db/valueType :db.type/float}
   :memory/current-strength {:db/valueType :db.type/float}  ;; calculated
   :memory/decay-rate {:db/valueType :db.type/float}
   :memory/last-recall {:db/valueType :db.type/instant}
   :memory/last-reviewed {:db/valueType :db.type/instant}
   :memory/review-count {:db/valueType :db.type/long}
   
   ;; Hierarchy (recursive memory)
   :memory/parent {:db/valueType :db.type/ref}
   :memory/children {:db/valueType :db.type/ref
                      :db/cardinality :db.cardinality/many}
   :memory/level {:db/valueType :db.type/long}  ;; 0=raw, 1=episode, 2=theme, 3=archetype
   :memory/source-messages {:db/valueType :db.type/ref
                             :db/cardinality :db.cardinality/many}
   
   ;; For retrieval
   :memory/tags {:db/valueType :db.type/keyword
                 :db/cardinality :db.cardinality/many}
   :memory/contexts {:db/valueType :db.type/keyword  ;; :auth :clojure :testing
                     :db/cardinality :db.cardinality/many}
   
   ;; ========== FILE SNAPSHOT ==========
   ;; Code state tracking
   :file-snapshot/id {:db/valueType :db.type/uuid
                       :db/unique :db.unique/identity}
   :file-snapshot/session {:db/valueType :db.type/ref}
   :file-snapshot/path {:db/valueType :db.type/string}
   :file-snapshot/content {:db/valueType :db.type/string}  ;; full text
   :file-snapshot/hash {:db/valueType :db.type/string}  ;; for detecting changes
   :file-snapshot/timestamp {:db/valueType :db.type/instant}
   ;;
   
   ;; ========== ENTITY (for knowledge graph) ==========
   ;; Extracted concepts
   :entity/id {:db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   :entity/name {:db/valueType :db.type/string}
   :entity/type {:db/valueType :db.type/keyword}  ;; :function :class :library :concept
   :entity/description {:db/valueType :db.type/string}
   :entity/mentions {:db/valueType :db.type/ref  ;; memories mentioning this
                     :db/cardinality :db.cardinality/many}
   :entity/related {:db/valueType :db.type/ref  ;; bidirectional links
                   :db/cardinality :db.cardinality/many}})
```

---

## Memory Type Taxonomy

Based on cognitive science and LLM agent research, we define these memory types:

| Type | `:memory/type` | Decay Rate | Description |
|------|----------------|------------|-------------|
| **Interaction** | `:interaction` | 0.8 | Raw message, transient |
| **Episode** | `:episode` | 0.4 | Session summary |
| **Theme** | `:theme` | 0.2 | Cross-session pattern |
| **Archetype** | `:archetype` | 0.05 | Permanent knowledge |
| **Fact** | `:fact` | 0.1 | Extracted knowledge |
| **Preference** | `:preference` | 0.05 | User-specific |
| **Procedural** | `:procedural` | 0.1 | How-to patterns |

---

## Vector Storage in Datalevin

Datalevin supports vector operations natively:

```clojure
(require '[datalevin.core :as d])

;; Schema for vector storage
(def vector-schema
  {:memory/id {:db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   ;; Vector embeddings (stored as float array)
   :memory/embedding {:db/valueType :db.type/f64
                      :db/cardinality :db.cardinality/many}})

;; Open connection with vector support
(def conn (d/get-conn "/tmp/agent-memory" 
                     {:memory/embedding {:db/index-type :db.index/vector
                                        :db/dimension 1536  ;; OpenAI embeddings
                                        :db/metric-type :db.metric/cosine}}))

;; Store a memory with embedding
(defn store-memory [conn content embedding-vector]
  (d/transact! conn
    [{:memory/id (java.util.UUID/randomUUID)
      :memory/type :interaction
      :memory/content content
      :memory/embedding embedding-vector
      :memory/initial-strength 1.0
      :memory/decay-rate 0.8
      :memory/last-recall (java.time.Instant/now)}]))

;; Semantic search
(defn find-similar-memories [conn query-embedding k]
  ;; Get k nearest neighbors by cosine similarity
  (d/q '[:find (pull ?m [:memory/id :memory/content :memory/importance])
         :in $ ?query-vec ?k
         :where [(vector/search :memory/embedding ?query-vec ?k) [[?m]]]]
       (d/db conn)
       query-embedding
       k))
```

---

## Datalog Query Patterns

### 1. Get Session Messages (Ordered)

```clojure
(defn get-session-messages [db session-id]
  (->> (d/q '[:find (pull ?m [:message/role 
                              :message/content 
                              :message/timestamp])
              :in $ ?session-id
              :where [?m :message/session ?session-id]]
            db
            session-id)
       (sort-by #(-> % first :message/timestamp))))
```

### 2. Find Memories Needing Review

```clojure
(defn memories-needing-review [db threshold-days]
  (let [threshold-instant (.minus (java.time.Instant/now)
                                  (java.time.Duration/ofDays threshold-days))]
    (d/q '[:find (pull ?m [:memory/id 
                           :memory/content
                           :memory/last-reviewed
                           :memory/current-strength])
           :in $ ?threshold
           :where [?m :memory/last-reviewed ?reviewed]
                  [(< ?reviewed ?threshold)]]
         db
         threshold-instant)))
```

### 3. Calculate Current Memory Strength

```clojure
(defn calculate-strength [memory now]
  (let [last-recall (or (:memory/last-reviewed memory)
                        (:memory/created-at memory))
        hours-since (/ (.toMillis (.between last-recall now 
                                            java.time.temporal.ChronoUnit/MILLIS))
                       3600000.0)  ;; hours
        initial (:memory/initial-strength memory)
        rate (:memory/decay-rate memory)]
    (* initial (Math/exp (- (* rate hours-since))))))

(defn update-all-strengths [conn]
  (let [now (java.time.Instant/now)
        db (d/db conn)
        memories (d/q '[:find (pull ?m [*])
                        :where [?m :memory/id _]]
                      db)]
    (d/transact! conn
      (for [[memory] memories
            :let [strength (calculate-strength memory now)]]
        [:db/add (:db/id memory) :memory/current-strength strength]))))
```

### 4. Get Memory Hierarchy (Recursive)

```clojure
(defn get-memory-tree [db memory-id depth]
  (let [memory (d/entity db [:memory/id memory-id])]
    (when (and memory (> depth 0))
      {:memory/id (:memory/id memory)
       :memory/content (:memory/content memory)
       :memory/level (:memory/level memory)
       :memory/children (mapv #(get-memory-tree db (:memory/id %) (dec depth))
                              (:memory/_parent memory))})))
```

### 5. Find Patterns Across Sessions

```clojure
(defn find-recurring-patterns [db user-id time-range]
  "Find themes that appear across multiple sessions"
  (->> (d/q '[:find ?theme (count ?session)
              :in $ ?user-id ?start ?end
              :where [?u :user/id ?user-id]
                     [?session :session/user ?u]
                     [?session :session/created-at ?t]
                     [(>= ?t ?start)]
                     [(<= ?t ?end)]
                     [?m :memory/session ?session]
                     [?m :memory/type :theme]
                     [?m :memory/content ?theme]]
            db
            user-id
            (:start time-range)
            (:end time-range))
       (sort-by second >)
       (take 10)))
```

---

## Datalevin-Specific Advantages

### 1. Implicit Joins

Datalog queries automatically traverse relationships:

```clojure
;; Get all messages from a user's active session
;; No explicit JOINs needed - just follow refs
(d/q '[:find ?content
       :in $ ?user-id
       :where [?user :user/id ?user-id]
              [?user :user/active-session ?session]
              [?msg :message/session ?session]
              [?msg :message/content ?content]]
     db
     user-uuid)
```

### 2. Time-Range Queries

Built-in support for time-series data:

```clojure
;; Get all sessions from last 7 days
(d/q '[:find (pull ?s [:session/id :session/title])
       :in $ ?start
       :where [?s :session/created-at ?t]
              [(>= ?t ?start)]]
     db
     (.minus (Instant/now) (Duration/ofDays 7)))
```

### 3. Flexible Schema Evolution

No migrations for new attributes:

```clojure
;; Add new field without schema change
(d/transact! conn
  [[:db/add entity-id :memory/new-attribute "value"]])

;; Query optional attributes safely
(d/q '[:find ?m ?new-val
       :where [?m :memory/id _]
              [(get-else $ ?m :memory/new-attribute nil) ?new-val]]
     db)
```

---

## Entity Lifecycle Examples

### Creating a New Session

```clojure
(defn create-session [conn agent-id user-id project-id]
  (let [session-id (java.util.UUID/randomUUID)
        now (java.time.Instant/now)]
    (d/transact! conn
      [{:session/id session-id
        :session/agent [:agent/id agent-id]
        :session/user [:user/id user-id]
        :session/project [:project/id project-id]
        :session/status :active
        :session/created-at now
        :session/meta (pr-str {:initiator :user
                               :context []})}]
      ;; Update user's active session
      [[:db/add [:user/id user-id] :user/active-session 
        [:session/id session-id]]])
    session-id))
```

### Storing a Message

```clojure
(defn store-message [conn session-id role content & {:keys [model]}]
  (let [msg-id (java.util.UUID/randomUUID)
        now (java.time.Instant/now)
        ;; Get next sequence number
        last-seq (or (->> (d/q '[:find (max ?seq)
                                 :in $ ?session-id
                                 :where [?m :message/session ?session]
                                        [?m :message/sequence ?seq]
                                        [?session :session/id ?session-id]]
                               (d/db conn)
                               session-id)
                          ffirst)
                     0)]
    (d/transact! conn
      [{:message/id msg-id
        :message/session [:session/id session-id]
        :message/role role
        :message/content content
        :message/sequence (inc last-seq)
        :message/timestamp now
        :message/model model}]))
```

### Consolidating Sessions into Episodes

```clojure
(defn consolidate-session [conn session-id]
  "Summarize a session into an episodic memory"
  (let [messages (get-session-messages (d/db conn) session-id)
        summary (generate-summary messages)  ;; LLM call
        episode-id (java.util.UUID/randomUUID)
        msg-ids (map :message/id messages)]
    (d/transact! conn
      [{:memory/id episode-id
        :memory/session [:session/id session-id]
        :memory/type :episode
        :memory/content summary
        :memory/level 1
        :memory/decay-rate 0.4
        :memory/initial-strength 1.0
        :memory/source-messages (map #(vector :message/id %) msg-ids)
        :memory/created-at (java.time.Instant/now)}])))
```

---

## Backup and Portability

```clojure
;; Export database
(d/copy "/tmp/agent-memory" "/backups/agent-memory-$(date +%Y%m%d)")

;; Export as EDN for inspection
(defn export-memories [conn file-path]
  (let [memories (d/q '[:find (pull ?m [*])
                       :where [?m :memory/id _]]
                     (d/db conn))]
    (spit file-path (pr-str memories))))

;; Import from EDN
(defn import-memories [conn file-path]
  (let [data (read-string (slurp file-path))]
    (d/transact! conn data)))
```

---

## Performance Considerations

### 1. Index Only What You Query

```clojure
;; Don't add :db/index unless needed
(def minimal-schema
  {:agent/id {:db/unique :db.unique/identity}  ;; indexed anyway
   :agent/name {}  ;; no index if not queried directly
   :session/user {:db/valueType :db.type/ref}  ;; refs auto-indexed
   })
```

### 2. Use Range Queries for Time-Series

```clojure
;; Efficient: uses timestamp index
(d/get-range db :session/created-at 
             [:closed start-time end-time] 
             :instant)

;; Less efficient: full scan
(d/q '[:find ?s
       :where [?s :session/created-at ?t]
              [(> ?t ?start) (< ?t ?end)]]
     db)
```

### 3. Batch Transacts

```clojure
;; Better: batch many operations
(d/transact! conn (for [i (range 1000)]
                    {:message/id (java.util.UUID/randomUUID)
                     :message/content (str "Message " i)}))
```

---

## References

1. **Datalevin Documentation** - https://github.com/juji-io/datalevin
   - Datalog queries, vector search, LMDB backend

2. **Hou et al. (2024)** - "My agent understands me better" (CHI EA '24)
   - Human-like memory recall and consolidation
   - https://arxiv.org/html/2404.00573v1

3. **Mastra/LangGraph Memory** - https://mastra.ai/docs/agents/agent-memory/
   - Thread-scoped vs cross-thread memory patterns

4. **Letta (MemGPT)** - https://www.letta.com/blog/agent-memory
   - Tiered memory architecture: working/recall/archival

5. **Chessa & Murre (2007)** - Mathematical model of memory
   - Exponential decay: `r(t) = μ * e^(-a * t)`

6. **Datomic Documentation** - https://docs.datomic.com/
   - Datalog query patterns (Datalevin-compatible)
