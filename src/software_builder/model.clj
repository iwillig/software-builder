(ns software-builder.model
  "Data model operations for Session, Message, and Memory entities.
   Provides CRUD operations and query utilities for the personal LLM coding agent."
  (:require
   [datalevin.core :as d])
  (:import
   (java.util UUID Date)))

;; ═══════════════════════════════════════════════════════════════
;; SESSION OPERATIONS
;; ═══════════════════════════════════════════════════════════════

(defn create-session
  "Create a new session for a project path. Returns the session UUID."
  [conn project-path]
  (let [session-id (UUID/randomUUID)
        now (Date.)]
    (d/transact! conn
                 [{:session/id session-id
                   :session/project-path project-path
                   :session/title (str "Session at " project-path)
                   :session/status :active
                   :session/checkpoint "{}"
                   :session/meta (pr-str {:git-branch nil
                                          :active-files []
                                          :tool-registry []})
                   :session/created-at now}])
    session-id))

(defn get-session
  "Retrieve a session by UUID."
  [db session-id]
  (d/entity db [:session/id session-id]))

(defn get-active-sessions
  "Get all sessions with :active status."
  [db]
  (->> (d/q '[:find (pull ?s [*])
              :where [?s :session/status :active]]
            db)
       (map first)))

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE OPERATIONS
;; ═══════════════════════════════════════════════════════════════

(defn- -next-sequence
  "Get the next sequence number for a session's messages."
  [db session-id]
  (or (->> (d/q '[:find (max ?seq)
                  :in $ ?session-id
                  :where [?m :message/session ?s]
                  [?m :message/sequence ?seq]
                  [?s :session/id ?session-id]]
                db session-id)
           ffirst)
      -1))

(defn store-message
  "Store a message in a session. Returns the message UUID."
  [conn session-id role content & {:keys [model tool-call tool-result content-type]}]
  (let [msg-id (UUID/randomUUID)
        now (Date.)
        db (d/db conn)
        last-seq (-next-sequence db session-id)]
    (d/transact! conn
                 (filterv some?
                          [{:message/id msg-id
                            :message/session [:session/id session-id]
                            :message/role role
                            :message/content content
                            :message/content-type (or content-type :text)
                            :message/sequence (inc last-seq)
                            :message/timestamp now}
                           (when model
                             [:db/add [:message/id msg-id] :message/model model])
                           (when tool-call
                             [:db/add [:message/id msg-id] :message/tool-call (pr-str tool-call)])
                           (when tool-result
                             [:db/add [:message/id msg-id] :message/tool-result (pr-str tool-result)])]))
    msg-id))

(defn get-session-messages
  "Get all messages for a session, ordered by sequence."
  [db session-id]
  (->> (d/q '[:find (pull ?m [*])
              :in $ ?session-id
              :where [?s :session/id ?session-id]
              [?m :message/session ?s]]
            db session-id)
       (map first)
       (sort-by :message/sequence)))

;; ═══════════════════════════════════════════════════════════════
;; MEMORY OPERATIONS
;; ═══════════════════════════════════════════════════════════════

(def ^:private decay-rates
  "Default decay rates by memory type (alpha in the decay formula)."
  {:interaction 0.8
   :episode 0.4
   :theme 0.2
   :archetype 0.05
   :fact 0.1
   :preference 0.05})

(defn create-memory
  "Create a memory from content. Returns the memory UUID."
  [conn content & {:keys [type level session-id parent-id source-ids]
                   :or {type :interaction level 0}}]
  (let [mem-id (UUID/randomUUID)
        now (Date.)]
    (d/transact! conn
                 (filterv some?
                          [{:memory/id mem-id
                            :memory/type type
                            :memory/content content
                            :memory/initial-strength 1.0
                            :memory/current-strength 1.0
                            :memory/decay-rate (get decay-rates type 0.5)
                            :memory/last-recall now
                            :memory/level level}
                           (when session-id
                             [:db/add [:memory/id mem-id] :memory/session [:session/id session-id]])
                           (when parent-id
                             [:db/add [:memory/id mem-id] :memory/parent [:memory/id parent-id]])
                           (when source-ids
                             (for [sid source-ids]
                               [:db/add [:memory/id mem-id] :memory/source-messages [:message/id sid]]))]))
    mem-id))

(defn calculate-strength
  "Calculate current memory strength using exponential decay formula:
   strength(t) = initial * e^(-decay-rate * hours-since-recall)"
  [memory now]
  (let [last-recall (or (:memory/last-reviewed memory)
                        (:memory/created-at memory)
                        (Date.))
        hours-since (/ (- (.getTime ^Date now) (.getTime ^Date last-recall))
                       3600000.0)
        initial (:memory/initial-strength memory 1.0)
        rate (:memory/decay-rate memory 0.5)]
    (* initial (Math/exp (- (* rate hours-since))))))

(defn update-memory-strengths
  "Recalculate all memory strengths. Call periodically."
  [conn]
  (let [now (Date.)
        db (d/db conn)
        memories (d/q '[:find ?m ?mem-id
                        :where [?m :memory/id ?mem-id]]
                      db)]
    (d/transact! conn
                 (for [[mem-entity mem-id] memories
                       :let [memory (d/entity db mem-entity)
                             strength (calculate-strength memory now)]]
                   [:db/add [:memory/id mem-id] :memory/current-strength strength]))))

(defn get-memories-by-tag
  "Get all memories with a specific tag."
  [db tag]
  (->> (d/q '[:find (pull ?m [*])
              :in $ ?tag
              :where [?m :memory/tags ?tag]]
            db tag)
       (map first)))

(defn get-memories-needing-review
  "Get memories with strength below threshold, sorted by lowest first."
  [db threshold]
  (->> (d/q '[:find (pull ?m [*])
              :where [?m :memory/current-strength ?s]
              [(< ?s ?threshold)]]
            db threshold)
       (map first)
       (sort-by :memory/current-strength)))

;; ═══════════════════════════════════════════════════════════════
;; QUERY UTILITIES
;; ═══════════════════════════════════════════════════════════════

(defn session-stats
  "Get statistics for a session."
  [db session-id]
  (let [messages (get-session-messages db session-id)
        session (get-session db session-id)]
    {:session/id session-id
     :session/title (:session/title session)
     :total-messages (count messages)
     :user-messages (count (filter #(= :user (:message/role %)) messages))
     :assistant-messages (count (filter #(= :assistant (:message/role %)) messages))
     :tool-calls (count (filter :message/tool-call messages))}))
