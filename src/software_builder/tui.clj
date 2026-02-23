(ns software-builder.tui
  "TUI for the software-builder with LLM integration.
   Provides a chat interface where user messages are sent to an LLM
   and responses are displayed and stored."
  (:require
   [clojure.string :as str]
   [datalevin.core :as d]
   [software-builder.model :as model]
   [software-builder.llm :as llm]
   [charm.core :as charm])
  (:import
   (org.jline.terminal TerminalBuilder Terminal)
   (org.jline.reader LineReaderBuilder)
   (org.jline.utils AttributedStyle AttributedStringBuilder)
   (java.util Date)))

;; ═══════════════════════════════════════════════════════════════
;; STATE
;; ═══════════════════════════════════════════════════════════════

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
  "Create styled text using AttributedStringBuilder."
  [text style-map]
  (let [builder (AttributedStringBuilder.)
        style   (cond-> AttributedStyle/DEFAULT
                  (:bold style-map)      .bold
                  (:italic style-map)    .italic
                  (:underline style-map) .underline
                  (:fg style-map)        (.foreground ^int (:fg style-map))
                  (:bg style-map)        (.background ^int (:bg style-map)))]
    (.append builder text style)
    (.toAttributedString builder)))

(def color
  "ANSI color constants."
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
    (print-styled terminal "    /clear       - Clear the screen" {:fg (:white color)})
    (.println writer "")))

;; ═══════════════════════════════════════════════════════════════
;; SPINNER
;; ═══════════════════════════════════════════════════════════════

(defn create-async-spinner
  "Create an async spinner that prints to terminal until stopped.
   Returns a map with :stop fn to stop the spinner."
  ([terminal]
   (create-async-spinner terminal "Processing..."))
  ([terminal message]
   (let [spinner-config (charm/spinner :dot :id :llm-spinner)
         [spinner-state _cmd] (charm/spinner-init spinner-config)
         running (atom true)
         frames-atom (atom spinner-state)
         width (.getColumns (.getSize terminal))
         writer (.writer terminal)]
     (future
       (while @running
         (let [current @frames-atom
               frame (charm/spinner-view current)
               text (str "  " frame " " message)]
           (.print writer "\r")
           (.print writer (apply str (repeat width " ")))
           (.print writer "\r")
           (.print writer text)
           (.flush writer)
           (Thread/sleep 80)
           (when @running
             (swap! frames-atom #(first (charm/spinner-update % {:type :spinner-tick})))))))
     {:stop (fn []
              (reset! running false)
              (.print writer "\r")
              (.print writer (apply str (repeat width " ")))
              (.print writer "\r")
              (.flush writer))})))

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE DISPLAY
;; ═══════════════════════════════════════════════════════════════

;; Subtle color palette using charm.style (ANSI 256 colors)
(def ^:private user-color      (charm/ansi 67))   ; soft blue
(def ^:private assist-color    (charm/ansi 245))  ; soft gray
(def ^:private timestamp-color (charm/ansi 240))  ; muted gray
(def ^:private system-color    (charm/ansi 178))  ; soft yellow
(def ^:private tool-color      (charm/ansi 139))  ; soft magenta

;; Style definitions using charm.style
(def ^:private user-header-style
  (charm/style :fg user-color :bold true))

(def ^:private assist-header-style
  (charm/style :fg assist-color :bold false))

(def ^:private timestamp-style
  (charm/style :fg timestamp-color :bold false))

(def ^:private content-style
  (charm/style :fg (charm/ansi 253)))  ; near-white

(defn format-timestamp
  "Format a Date for display (minimal HH:MM)."
  [^Date date]
  (let [fmt (java.text.SimpleDateFormat. "HH:mm")]
    (.format fmt date)))

(defn- render-header
  "Render a message header with charm.style."
  [timestamp prefix style]
  (str (charm/render timestamp-style (str "[" timestamp "] "))
       (charm/render style prefix)))

(defn- wrap-text
  "Wrap text to fit within a specified width."
  [text max-width]
  (if (<= (count text) max-width)
    [text]
    (loop [words (str/split text #"\s+")
           line ""
           lines []]
      (if (empty? words)
        (if (seq line)
          (conj lines line)
          lines)
        (let [word (first words)
              test-line (if (seq line)
                          (str line " " word)
                          word)]
          (if (<= (count test-line) max-width)
            (recur (rest words) test-line lines)
            (recur (rest words) word (conj lines line))))))))

(defn- indent-lines
  "Indent lines with a consistent prefix."
  [lines indent]
  (map #(str indent %) lines))

(defn print-message
  "Print a single message to the terminal with subtle styling."
  [^Terminal terminal msg]
  (let [role      (:message/role msg)
        content   (:message/content msg)
        timestamp (:message/timestamp msg)
        time-str  (when timestamp (format-timestamp timestamp))
        width     (.getColumns (.getSize terminal))
        content-width (- width 4)  ; Account for indent
        writer    (.writer terminal)]
    (case role
      :user
      (do
        (.println writer (render-header time-str "You" user-header-style))
        (doseq [line (indent-lines (wrap-text content content-width) "  ")]
          (.println writer (charm/render content-style line)))
        (.println writer ""))

      :assistant
      (do
        (.println writer (render-header time-str "Assistant" assist-header-style))
        (doseq [line (indent-lines (wrap-text content content-width) "  ")]
          (.println writer (charm/render content-style line)))
        (.println writer ""))

      ;; system / tool: minimal styling
      (let [role-style (case role
                         :system (charm/style :fg system-color :bold true)
                         :tool   (charm/style :fg tool-color :bold true)
                         (charm/style :fg (charm/ansi 250)))
            role-name  (case role
                         :system "System"
                         :tool   "Tool"
                         (str/capitalize (name role)))]
        (.println writer (str (charm/render timestamp-style (str "[" time-str "] "))
                              (charm/render role-style (str role-name ": "))
                              content))
        (.println writer "")))
    (.flush writer)))

(defn print-llm-response
  "Print an LLM response to the terminal with subtle styling."
  [^Terminal terminal content]
  (print-message terminal
                 {:message/role :assistant
                  :message/content content
                  :message/timestamp (Date.)}))

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
;; LLM INTEGRATION
;; ═══════════════════════════════════════════════════════════════

(defn create-llm-client
  "Create an LLM client from environment configuration.
   Uses HF_TOKEN env var for authentication."
  []
  (let [token (System/getenv "HF_TOKEN")]
    (when token
      (llm/hugging-face-client
       {:api-token token
        :model "meta-llama/Meta-Llama-3.1-8B-Instruct"
        :provider "hyperbolic"}))))

(defn build-conversation-messages
  "Build message list from session history for LLM context.
   Returns vector of Message records."
  [conn session-id system-prompt]
  (let [db (d/db conn)
        history (model/get-session-messages db session-id)
        messages (concat
                  (when system-prompt
                    [(llm/make-message :system system-prompt)])
                  (map #(llm/make-message (:message/role %) (:message/content %))
                       history))]
    (vec messages)))

(defn send-to-llm
  "Send conversation to LLM and return response.
   Returns deferred containing the response map."
  [llm-client conn session-id user-input]
  (let [system-prompt "You are a helpful coding assistant. Provide concise, accurate answers."
        ;; Build history BEFORE storing the new message to avoid duplicates
        messages     (build-conversation-messages conn session-id system-prompt)
        all-messages (conj messages (llm/make-message :user user-input))]
    ;; Store after history is captured
    (model/store-message conn session-id :user user-input)
    (llm/complete llm-client all-messages {:max-tokens 500 :temperature 0.7})))

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

    (= input "/clear")
    (do
      (let [writer (.writer terminal)]
        (.print writer "\u001b[2J\u001b[H")
        (.flush writer)
        (print-header terminal (:session/title (model/get-session (d/db conn) session-id) "Session")))
      :continue)

    :else
    (do
      (print-styled terminal (str "Unknown command: " input) {:fg (:red color)})
      (print-styled terminal "Type /help for available commands" {:fg (:white color)})
      :continue)))

(defn handle-user-input
  "Handle user input, send to LLM, display and store response.
   Returns :continue or :quit."
  [^Terminal terminal llm-client conn session-id input]
  (if (.startsWith input "/")
    ;; Handle command - propagate :quit to the loop
    (handle-command terminal conn session-id input)
    ;; Send to LLM
    (do
      ;; Echo user's message as a chat bubble
      (print-message terminal {:message/role      :user
                               :message/content   input
                               :message/timestamp (Date.)})
      ;; Start async spinner while waiting for LLM
      (let [spinner (create-async-spinner terminal "Thinking...")
            response @(send-to-llm llm-client conn session-id input)
            content (:content response)]
        ;; Stop spinner when response arrives
        ((:stop spinner))
        ;; Display and store response
        (if content
          (do
            (print-llm-response terminal content)
            (model/store-message conn session-id :assistant content
                                 :model (:model response)))
          (print-styled terminal
                        (str "Error: " (:error response "Unknown error"))
                        {:fg (:red color)})))
      :continue)))

;; ═══════════════════════════════════════════════════════════════
;; INPUT BOX
;; ═══════════════════════════════════════════════════════════════

(defn- -session-status-line
  "Build a status string summarising the current session's token usage.
   Estimates tokens as character-count / 4 (standard rough approximation)."
  [db session-id width]
  (let [messages   (model/get-session-messages db session-id)
        total-chars (reduce + 0 (map #(count (:message/content %)) messages))
        tokens     (quot total-chars 4)
        msg-count  (count messages)
        text       (format "  ~%,d tokens  ·  %d message%s"
                           tokens msg-count (if (= 1 msg-count) "" "s"))
        ;; Truncate if wider than terminal
        text       (if (> (count text) width) (subs text 0 width) text)]
    text))

(defn -read-input
  "Read a line of input with a full-width bordered box and a token-count
   status line below it. Pre-draws the complete layout so the bottom border
   and status are never cut off by terminal scroll on Enter."
  [^Terminal terminal reader conn session-id]
  (let [width  (.getColumns (.getSize terminal))
        top    (str "╭" (apply str (repeat (- width 2) "─")) "╮")
        bottom (str "╰" (apply str (repeat (- width 2) "─")) "╯")
        status (str "\u001b[2m"
                    (-session-status-line (d/db conn) session-id width)
                    "\u001b[0m")
        writer (.writer terminal)]
    ;; Pre-draw: blank separator · top border · blank input area · bottom border · status
    (.println writer "")
    (.println writer top)
    (.println writer "")
    (.println writer bottom)
    (.println writer status)
    (.flush writer)
    ;; Move cursor up 3 lines into the blank input area between the borders.
    (.print writer "\u001b[3A")
    (.flush writer)
    ;; readLine draws "│ > " on the input line. On Enter, cursor moves to
    ;; the bottom border line — not the last terminal line — so no extra scroll.
    (let [input (.readLine reader "│ > ")]
      ;; Cursor is now on line N+3 (bottom border placeholder).
      ;; Move up 3 to the blank separator (line N) and clear to end of screen,
      ;; erasing the entire input box so it never appears in the chat history.
      (.print writer "\u001b[3A\r\u001b[0J")
      (.flush writer)
      input)))

;; ═══════════════════════════════════════════════════════════════
;; MAIN TUI LOOP
;; ═══════════════════════════════════════════════════════════════

(defn run-tui
  "Run the TUI with an existing database connection and session.
   Initializes LLM client and processes user input."
  [conn session-id]
  (let [terminal (create-terminal)
        reader   (create-reader terminal)
        session  (model/get-session (d/db conn) session-id)
        title    (:session/title session "Untitled Session")
        llm-client (create-llm-client)]
    (try
      (print-header terminal title)
      (print-help terminal)
      (print-message-history terminal conn session-id)

      (when-not llm-client
        (print-styled terminal
                      "Warning: HF_TOKEN not set. LLM features unavailable."
                      {:fg (:red color)}))

      (loop []
        (let [input (-read-input terminal reader conn session-id)]
          (when-not (str/blank? input)
            (when (not= :quit (handle-user-input terminal llm-client conn session-id input))
              (recur)))))
      (finally
        (.close terminal)))))

(defn run-tui-new-session
  "Create a new session and run TUI with LLM integration."
  [conn project-path]
  (let [session-id (model/create-session conn project-path)]
    (println (str "Created new session: " session-id))
    (run-tui conn session-id)))
