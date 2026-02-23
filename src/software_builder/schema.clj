(ns software-builder.schema
  "Phase 1 data schema for personal LLM coding agent.
   Defines Session, Message, and Memory entities with Datalevin."
  (:require
   [datalevin.core :as d]))

;; ═══════════════════════════════════════════════════════════════
;; PHASE 1: CORE SCHEMA
;; Minimal viable data model for personal coding agent
;; ═══════════════════════════════════════════════════════════════

(def phase-1-schema
  {;; ─────────── SESSION ───────────
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
                 :db/doc ":interaction | :episode | :theme | :archetype | :fact | :preference"}

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
                             :db/doc "Calculated: mu * e^(-alpha*t)"}

   :memory/decay-rate {:db/valueType :db.type/float
                       :db/doc "alpha: 0.8=raw, 0.4=episode, 0.05=archetype"}

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

   ;; Vector embedding (for semantic search) - uses :db.type/vec for HNSW indexing
   ;; Requires vector-opts when creating connection: {:dimensions 1536 :metric-type :cosine}
   :memory/embedding {:db/valueType :db.type/vec
                      :db/doc "Vector embedding for semantic search"}})

;; ═══════════════════════════════════════════════════════════════
;; DATABASE CONNECTION
;; ═══════════════════════════════════════════════════════════════

(def ^:dynamic *db-path*
  "Default database path. Binds to ~/.local/share/software-builder/db"
  (str (System/getProperty "user.home")
       "/.local/share/software-builder/db"))

(def ^:dynamic *vector-opts*
  "Default vector options for embeddings. 1536 dims for OpenAI embeddings."
  {:dimensions 1536
   :metric-type :cosine})

(defn get-conn
  "Get a Datalevin connection. Creates database if it doesn't exist.
   Options:
   - :vector-opts - Map with :dimensions and :metric-type for vector search"
  ([db-path opts]
   (d/get-conn db-path phase-1-schema
               (merge {:vector-opts (or (:vector-opts opts) *vector-opts*)}
                      (dissoc opts :vector-opts))))
  ([db-path]
   (get-conn db-path {}))
  ([]
   (get-conn *db-path* {})))

(defn close-conn
  "Close a Datalevin connection."
  [conn]
  (d/close conn))
