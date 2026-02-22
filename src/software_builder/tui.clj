(ns software-builder.tui
  "Simple TUI for the software-builder using JLine.
   Provides a chat-like interface for interacting with sessions."
  (:require
   [datalevin.core :as d]
   [software-builder.schema :as schema]
   [software-builder.model :as model])
  (:import
   (org.jline.terminal TerminalBuilder Terminal)
   (org.jline.reader LineReaderBuilder LineReader)
   (org.jline.utils AttributedStyle AttributedStringBuilder)
   (java.util Date)))

;; ═══════════════════════════════════════════════════════════════
;; STATE
;; ═══════════════════════════════════════════════════════════════

(def ^:dynamic *conn*
  "Current database connection for TUI session."
  nil)

(def ^:dynamic *session-id*
  "Current active session ID."
  nil)

;; ═══════════════════════════════════════════════════════════════
;; TERMINAL SETUP
;; ═══════════════════════════════════════════════════════════════

(defn create-terminal
  "Create a system terminal with JLine."
  []
  (-> (TerminalBuilder/builder)
      (.system true)
      (.build)))

(defn create-reader
  "Create a line reader for the terminal."
  [^Terminal terminal]
  (-> (LineReaderBuilder/builder)
      (.terminal terminal)
      (.build)))

;; ═══════════════════════════════════════════════════════════════
;; STYLED OUTPUT
;; ═══════════════════════════════════════════════════════════════

(defn styled-text
  "Create styled text using AttributedStringBuilder.
   style-map can include: :fg :bg :bold :italic :underline"
  [text style-map]
  (let [builder (AttributedStringBuilder.)
        style (AttributedStyle/DEFAULT)]
    (cond-> style
      (:bold style-map) (.bold)
      (:italic style-map) (.italic)
      (:underline style-map) (.underline)
      (:fg style-map) (.foreground ^int (:fg style-map))
      (:bg style-map) (.background ^int (:bg style-map)))
    (.append builder text style)
    (.toAttributedString builder)))

(def color
  "ANSI color constants for styling."
  {:black   AttributedStyle/BLACK
   :red     AttributedStyle/RED
   :green   AttributedStyle/GREEN
   :yellow  AttributedStyle/YELLOW
   :blue    AttributedStyle/BLUE
   :magenta AttributedStyle/MAGENTA
   :cyan    AttributedStyle/CYAN
   :white   AttributedStyle/WHITE})

(defn print-styled
  "Print styled text to terminal."
  [^Terminal terminal text style-map]
  (let [styled (styled-text text style-map)
        writer (.writer terminal)]
    (.println writer styled)
    (.flush writer)))

(defn print-header
  "Print the TUI header."
  [^Terminal terminal session-title]
  (let [width (.getColumns (.getSize terminal))
        line (apply str (repeat width "─"))]
    (print-styled terminal line {:fg (:cyan color) :bold true})
    (print-styled terminal (str "  Session: " session-title) {:fg (:cyan color) :bold true})
    (print-styled terminal line {:fg (:cyan color) :bold true})))

(defn print-help
  "Print help text."
  [^Terminal terminal]
  (let [writer (.writer terminal)]
    (.println writer "")
    (print-styled terminal "  Commands:" {:fg (:yellow color) :bold true})
    (print-styled terminal "    /quit or /q  - Exit the TUI" {:fg (:white color)})
    (print-styled terminal "    /help or /h  - Show this help" {:fg (:white color)})
    (print-styled terminal "    /history     - Show message history" {:fg (:white color)})
    (.println writer "")))

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE DISPLAY
;; ═══════════════════════════════════════════════════════════════

(defn format-timestamp
  "Format a Date for display."
  [^Date date]
  (let [fmt (java.text.SimpleDateFormat. "HH:mm:ss")]
    (.format fmt date)))

(defn print-message
  "Print a single message to the terminal."
  [^Terminal terminal msg]
  (let [role (:message/role msg)
        content (:message/content msg)
        timestamp (:message/timestamp msg)
        time-str (when timestamp (format-timestamp timestamp))
        [role-color role-name] (case role
                                 :user     [(:green color) "User"]
                                 :assistant [(:blue color) "Assistant"]
                                 :system   [(:yellow color) "System"]
                                 :tool     [(:magenta color) "Tool"]
                                 [(:white color) (name role)])]
    (print-styled terminal
                  (str "[" time-str "] ")
                  {:fg (:white color)})
    (print-styled terminal
                  (str role-name ": ")
                  {:fg role-color :bold true})
    (let [writer (.writer terminal)]
      (.println writer content)
      (.flush writer))))

(defn print-message-history
  "Display message history for current session."
  [^Terminal terminal conn session-id]
  (let [db (d/db conn)
        messages (model/get-session-messages db session-id)]
    (if (seq messages)
      (do
        (print-styled terminal "
─── Message History ───" {:fg (:cyan color) :bold true})
        (doseq [msg messages]
          (print-message terminal msg)))
      (print-styled terminal "  (No messages yet)" {:fg (:white color)}))))

;; ═══════════════════════════════════════════════════════════════
;; INPUT HANDLING
;; ═══════════════════════════════════════════════════════════════

(defn handle-command
  "Handle special commands. Returns :quit to exit, :continue otherwise."
  [^Terminal terminal conn session-id input]
  (cond
    (or (= input "/quit") (= input "/q"))
    (do
      (print-styled terminal "Goodbye!" {:fg (:green color)})
      :quit)

    (or (= input "/help") (= input "/h"))
    (do
      (print-help terminal)
      :continue)

    (= input "/history")
    (do
      (print-message-history terminal conn session-id)
      :continue)

    :else
    (do
      (print-styled terminal (str "Unknown command: " input) {:fg (:red color)})
      (print-styled terminal "Type /help for available commands" {:fg (:white color)})
      :continue)))

;; ═══════════════════════════════════════════════════════════════
;; MAIN TUI LOOP
;; ═══════════════════════════════════════════════════════════════

(defn run-tui
  "Run the TUI with an existing database connection and session.
   Returns when user quits."
  [conn session-id]
  (let [terminal (create-terminal)
        reader (create-reader terminal)
        session (model/get-session (d/db conn) session-id)
        title (:session/title session "Untitled Session")]
    (binding [*conn* conn
              *session-id* session-id]
      (try
        ;; Print header
        (print-header terminal title)
        (print-help terminal)
        (print-message-history terminal conn session-id)

        ;; Main input loop
        (loop []
          (let [input (.readLine reader "
> ")]
            (when (seq input)
              (if (.startsWith input "/")
                ;; Handle command
                (when (= :quit (handle-command terminal conn session-id input))
                  (System/exit 0))
                ;; Store as user message
                (do
                  (model/store-message conn session-id :user input)
                  (print-styled terminal "Message stored." {:fg (:green color)})))
              (recur))))
        (finally
          (.close terminal))))))

(defn run-tui-new-session
  "Create a new session and run TUI."
  [conn project-path]
  (let [session-id (model/create-session conn project-path)]
    (println (str "Created new session: " session-id))
    (run-tui conn session-id)))
