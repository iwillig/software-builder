(ns software-builder.main
  "Main entry point for software-builder CLI using cli-matic.
   The primary command starts the TUI for a project.
   Session management commands are available as subcommands."
  (:gen-class)
  (:require
   [cli-matic.core :as cli]
   [clojure.string :as str]
   [datalevin.core :as d]
   [software-builder.schema :as schema]
   [software-builder.model :as model]
   [software-builder.tui :as tui]
   [bling.banner :refer [banner]]
   [bling.fonts.ansi-shadow :refer [ansi-shadow]])
  (:import
   (java.util UUID)))

;; ═══════════════════════════════════════════════════════════════
;; BANNER
;; ═══════════════════════════════════════════════════════════════

(defn print-banner
  "Print the Software Builder ASCII banner."
  []
  (println
   (banner
    {:font ansi-shadow
     :text "Software Builder"
     :gradient-direction :to-right
     :gradient-colors [:cool :warm]
     :margin-bottom 1})))

;; ═══════════════════════════════════════════════════════════════
;; DATABASE CONNECTION MANAGEMENT
;; ═══════════════════════════════════════════════════════════════

(defn get-db-path
  "Get the default database path."
  []
  (str (System/getProperty "user.home")
       "/.local/share/software-builder/db"))

(defn with-db
  "Execute a function with a database connection."
  [f]
  (let [conn (schema/get-conn (get-db-path) {})]
    (try
      (f conn)
      (finally
        (schema/close-conn conn)))))

;; ═══════════════════════════════════════════════════════════════
;; COMMAND IMPLEMENTATIONS (for testing with explicit connection)
;; ═══════════════════════════════════════════════════════════════

(defn main-cmd-impl
  "Primary command implementation - starts TUI for a project.
   Defaults to current directory if no path is provided."
  [conn project-path]
  (let [path     (if (str/blank? project-path)
                   (System/getProperty "user.dir")
                   project-path)
        db       (d/db conn)
        active   (model/get-active-sessions db)
        existing (first (filter #(= path (:session/project-path %)) active))]
    (if existing
      (do
        (println (str "Resuming existing session: " (:session/id existing)))
        (tui/run-tui conn (:session/id existing))
        0)
      (do
        (tui/run-tui-new-session conn path)
        0))))

(defn session-list-cmd-impl
  "Implementation of session list with explicit connection."
  [conn]
  (let [db (d/db conn)
        sessions (model/get-active-sessions db)]
    (if (seq sessions)
      (do
        (println "Active Sessions:")
        (println)
        (doseq [session sessions]
          (println (str "  ID:    " (:session/id session)))
          (println (str "  Path:  " (:session/project-path session)))
          (println (str "  Title: " (:session/title session)))
          (println (str "  Created: " (:session/created-at session)))
          (println)))
      (println "No active sessions found."))
    0))

(defn session-create-cmd-impl
  "Implementation of session create with explicit connection."
  [conn path]
  (if (str/blank? path)
    (do
      (println "Error: Project path required")
      1)
    (let [session-id (model/create-session conn path)]
      (println (str "Created new session: " session-id))
      (println (str "Project path: " path))
      0)))

(defn session-show-cmd-impl
  "Implementation of session show with explicit connection."
  [conn session-id-str]
  (if (str/blank? session-id-str)
    (do
      (println "Error: Session ID required")
      1)
    (try
      (let [sid (UUID/fromString session-id-str)
            db (d/db conn)
            session (model/get-session db sid)]
        (if session
          (do
            (println "Session Details:")
            (println)
            (println (str "  ID:       " (:session/id session)))
            (println (str "  Path:     " (:session/project-path session)))
            (println (str "  Title:    " (:session/title session)))
            (println (str "  Status:   " (:session/status session)))
            (println (str "  Created:  " (:session/created-at session)))
            (println)
            (let [stats (model/session-stats db sid)
                  messages (model/get-session-messages db sid)]
              (println "Statistics:")
              (println (str "  Total messages: " (:total-messages stats)))
              (println (str "  User messages: " (:user-messages stats)))
              (println (str "  Assistant messages: " (:assistant-messages stats)))
              (println)
              (when (seq messages)
                (println "Messages:")
                (doseq [msg messages]
                  (let [role      (name (:message/role msg))
                        raw       (:message/content msg)
                        truncated (> (count raw) 50)
                        content   (subs raw 0 (min 50 (count raw)))]
                    (println (str "  [" role "] " content (when truncated "...")))))
                (println)))
            0)
          (do
            (println (str "Session not found: " sid))
            1)))
      (catch IllegalArgumentException _
        (println (str "Invalid UUID: " session-id-str))
        1))))

;; ═══════════════════════════════════════════════════════════════
;; CLI COMMAND HANDLERS (wrap implementations with with-db)
;; ═══════════════════════════════════════════════════════════════

(defn main-cmd
  "Primary CLI command - starts TUI for the given project path."
  [{:keys [path]}]
  (with-db #(main-cmd-impl % path)))

(defn session-list-cmd
  "CLI handler for session list command."
  [_]
  (with-db session-list-cmd-impl))

(defn session-create-cmd
  "CLI handler for session create command."
  [{:keys [path]}]
  (with-db #(session-create-cmd-impl % path)))

(defn session-show-cmd
  "CLI handler for session show command."
  [{:keys [session-id]}]
  (with-db #(session-show-cmd-impl % session-id)))

;; ═══════════════════════════════════════════════════════════════
;; CLI CONFIGURATION
;; ═══════════════════════════════════════════════════════════════

(def CLI-CONFIG
  {:command "software-builder"
   :description "Personal LLM Coding Agent - TUI interface for coding with LLMs"
   :version "0.1.0"
   ;; Primary command takes a project path via -p flag
   :opts [{:option "path"
           :short "p"
           :as "Project path to open in TUI"
           :type :string}]
   :runs main-cmd})

;; ═══════════════════════════════════════════════════════════════
;; MAIN ENTRY POINT
;; ═══════════════════════════════════════════════════════════════

(defn- -flag-value
  "Get the value following a flag in an args sequence. Returns nil if not found."
  [args flag]
  (->> (partition 2 1 args)
       (filter #(= flag (first %)))
       first
       second))

(defn -main
  "Main entry point. Runs cli-matic with configuration.
   Manually routes 'session' subcommand before cli-matic processing
   because cli-matic has issues with :runs + :subcommands combination."
  [& args]
  (let [first-arg (first args)]
    (cond
      ;; Session subcommand - manually dispatch
      (= "session" first-arg)
      (let [subcmd (second args)
            remaining (drop 2 args)]
        (case subcmd
          "list" (session-list-cmd {})
          "create" (session-create-cmd {:path (-flag-value remaining "-p")})
          "show"   (session-show-cmd {:session-id (-flag-value remaining "-s")})
          (do (println "Unknown session command:" subcmd)
              (println "Valid commands: list, create, show")
              1)))

      ;; Default - use cli-matic for the main TUI command
      :else (cli/run-cmd args CLI-CONFIG))))
