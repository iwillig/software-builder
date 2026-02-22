(ns software-builder.main
  "Main entry point for software-builder CLI.
   Provides commands for session management and TUI interaction."
  (:gen-class)
  (:require
   [clojure.string :as str]
   [datalevin.core :as d]
   [software-builder.schema :as schema]
   [software-builder.model :as model]
   [software-builder.tui :as tui])
  (:import
   (java.util UUID)))

;; ═══════════════════════════════════════════════════════════════
;; DATABASE CONNECTION MANAGEMENT
;; ═══════════════════════════════════════════════════════════════

(defn- get-db-path
  "Get the default database path."
  []
  (str (System/getProperty "user.home")
       "/.local/share/software-builder/db"))

(defn- with-db
  "Execute a function with a database connection.
   Opens connection, executes fn, closes connection."
  [f]
  (let [conn (schema/get-conn (get-db-path) {})]
    (try
      (f conn)
      (finally
        (schema/close-conn conn)))))

;; ═══════════════════════════════════════════════════════════════
;; COMMAND HANDLERS
;; ═══════════════════════════════════════════════════════════════

(defn cmd-help
  "Display help information."
  []
  (println "Software Builder - Personal LLM Coding Agent")
  (println)
  (println "Usage: bb main <command> [args]")
  (println)
  (println "Commands:")
  (println "  help                    Show this help")
  (println "  tui <project-path>      Start TUI for a project (creates or resumes session)")
  (println "  session list            List active sessions")
  (println "  session create <path>   Create new session for path")
  (println "  session show <uuid>     Show session details and messages")
  (println)
  (println "Examples:")
  (println "  bb main tui ./my-project")
  (println "  bb main session list")
  (println "  bb main session create /tmp/test"))

(defn cmd-tui
  "Start the TUI for a project.
   Creates a new session or uses an existing active session for the project."
  [project-path]
  (if (str/blank? project-path)
    (do
      (println "Error: Project path required")
      (println "Usage: bb main tui <project-path>")
      (System/exit 1))
    (with-db
      (fn [conn]
        ;; Check for existing active session
        (let [db (d/db conn)
              active (model/get-active-sessions db)
              existing (first (filter #(= project-path (:session/project-path %)) active))]
          (if existing
            (do
              (println (str "Resuming existing session: " (:session/id existing)))
              (tui/run-tui conn (:session/id existing)))
            (tui/run-tui-new-session conn project-path)))))))

(defn cmd-session-list
  "List all active sessions."
  []
  (with-db
    (fn [conn]
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
          (println "No active sessions found."))))))

(defn cmd-session-create
  "Create a new session for a project path."
  [project-path]
  (if (str/blank? project-path)
    (do
      (println "Error: Project path required")
      (println "Usage: bb main session create <project-path>")
      (System/exit 1))
    (with-db
      (fn [conn]
        (let [session-id (model/create-session conn project-path)]
          (println (str "Created new session: " session-id))
          (println (str "Project path: " project-path)))))))

(defn cmd-session-show
  "Show session details and message history."
  [session-id-str]
  (if (str/blank? session-id-str)
    (do
      (println "Error: Session ID required")
      (println "Usage: bb main session show <session-uuid>")
      (System/exit 1))
    (try
      (let [session-id (UUID/fromString session-id-str)]
        (with-db
          (fn [conn]
            (let [db (d/db conn)
                  session (model/get-session db session-id)]
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
                  (let [stats (model/session-stats db session-id)
                        messages (model/get-session-messages db session-id)]
                    (println "Statistics:")
                    (println (str "  Total messages: " (:total-messages stats)))
                    (println (str "  User messages: " (:user-messages stats)))
                    (println (str "  Assistant messages: " (:assistant-messages stats)))
                    (println)
                    (when (seq messages)
                      (println "Messages:")
                      (doseq [msg messages]
                        (let [role (name (:message/role msg))
                              content (str/join (take 50 (:message/content msg))) ; truncated
                              truncated (> (count (:message/content msg)) 50)]
                          (println (str "  [" role "] " content (when truncated "...")))))
                      (println))))
                (do
                  (println (str "Session not found: " session-id))
                  (System/exit 1)))))))
      (catch IllegalArgumentException e
        (println (str "Invalid UUID: " session-id-str))
        (System/exit 1)))))

;; ═══════════════════════════════════════════════════════════════
;; MAIN ENTRY POINT
;; ═══════════════════════════════════════════════════════════════

(defn -main
  "Main entry point. Dispatches to command handlers."
  [& args]
  (cond
    ;; No args or help
    (or (empty? args)
        (= "help" (first args))
        (= "-h" (first args))
        (= "--help" (first args)))
    (cmd-help)

    ;; TUI command
    (= "tui" (first args))
    (cmd-tui (second args))

    ;; Session commands
    (= "session" (first args))
    (let [subcmd (second args)]
      (cond
        (= "list" subcmd) (cmd-session-list)
        (= "create" subcmd) (cmd-session-create (nth args 2 nil))
        (= "show" subcmd) (cmd-session-show (nth args 2 nil))
        :else (do
                (println "Unknown session command:" subcmd)
                (println "Valid: list, create, show")
                (System/exit 1))))

    ;; Unknown command
    :else
    (do
      (println "Unknown command:" (first args))
      (println "Run 'bb main help' for usage information")
      (System/exit 1))))
