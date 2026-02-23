(ns software-builder.tui
  "TUI for the software-builder using charm.clj's Model-Update-View architecture.
   Provides a chat interface with full-width input using charm's text-input component."
  (:require
   [clojure.string :as str]
   [datalevin.core :as d]
   [software-builder.model :as model]
   [software-builder.llm :as llm]
   [charm.core :as charm]
   [bling.banner :refer [banner]]
   [bling.fonts.ansi-shadow :refer [ansi-shadow]])
  (:import
   (java.util Date)))

;; ═══════════════════════════════════════════════════════════════
;; COLORS & STYLES
;; ═══════════════════════════════════════════════════════════════

(def ^:private user-color (charm/ansi 67))
(def ^:private assist-color (charm/ansi 245))
(def ^:private timestamp-color (charm/ansi 240))
(def ^:private system-color (charm/ansi 178))
(def ^:private tool-color (charm/ansi 139))
(def ^:private border-color (charm/ansi 240))

(def ^:private user-header-style
  (charm/style :fg user-color :bold true))

(def ^:private assist-header-style
  (charm/style :fg assist-color :bold false))

(def ^:private timestamp-style
  (charm/style :fg timestamp-color :bold false))

(def ^:private content-style
  (charm/style :fg (charm/ansi 253)))

(def ^:private border-style
  (charm/style :fg border-color))

(def ^:private status-style
  (charm/style :fg (charm/ansi 242) :italic true))

;; ═══════════════════════════════════════════════════════════════
;; INITIALIZATION
;; ═══════════════════════════════════════════════════════════════

(defn- -format-timestamp
  "Format a Date for display (minimal HH:MM)."
  [^Date date]
  (let [fmt (java.text.SimpleDateFormat. "HH:mm")]
    (.format fmt date)))

(defn- -create-banner
  "Create the Software Builder banner with bling."
  []
  (banner
   {:font ansi-shadow
    :text "Software Builder"
    :gradient-direction :to-right
    :gradient-colors [:cool :warm]}))

(defn- -create-input-box
  "Create full-width text input component."
  [width]
  (charm/text-input :prompt ""
                    :placeholder "Type your message..."
                    :width (- width 8)  ; Account for borders and padding
                    :focused true
                    :id :main-input))

(defn- -session-status-text
  "Build status string for token usage."
  [db session-id]
  (let [messages (model/get-session-messages db session-id)
        total-chars (reduce + 0 (map #(count (:message/content %)) messages))
        tokens (quot total-chars 4)
        msg-count (count messages)]
    (format "~%,d tokens · %d message%s"
            tokens msg-count (if (= 1 msg-count) "" "s"))))

(defn init-state
  "Initialize the TUI state for charm/run."
  [conn session-id llm-client]
  (let [db (d/db conn)
        session (model/get-session db session-id)
        ;; Default size, will be updated on first window-size event
        width 80
        height 24]
    {:conn conn
     :session-id session-id
     :llm-client llm-client
     :model-name (when llm-client (-> llm-client :config :model))
     :session session
     :messages (model/get-session-messages db session-id)
     :width width
     :height height
     :banner (-create-banner)
     :input (-create-input-box width)
     :spinner nil
     :thinking? false
     :error nil}))

;; ═══════════════════════════════════════════════════════════════
;; UPDATE FUNCTION
;; ═══════════════════════════════════════════════════════════════

(defn- -handle-command
  "Handle slash commands. Returns [new-state cmd]."
  [state input]
  (cond
    (or (= input "/quit") (= input "/q"))
    [state charm/quit-cmd]

    (= input "/help")
    [(assoc state :show-help true) nil]

    (= input "/history")
    (let [db (d/db (:conn state))
          messages (model/get-session-messages db (:session-id state))]
      [(assoc state :messages messages :show-history true) nil])

    (= input "/clear")
    [(assoc state :messages []) nil]

    :else
    [(assoc state :error (str "Unknown command: " input)) nil]))

(defn- -send-to-llm
  "Send message to LLM and return command for async handling."
  [state input]
  (let [{:keys [conn session-id llm-client]} state]
    ;; Store user message
    (model/store-message conn session-id :user input)
    ;; Create async command for LLM call
    (charm/cmd
     (fn []
       (let [system-prompt "You are a helpful coding assistant. Provide concise, accurate answers."
             messages (concat
                       [(llm/make-message :system system-prompt)]
                       (map #(llm/make-message (:message/role %) (:message/content %))
                            (:messages state))
                       [(llm/make-message :user input)])
             response @(llm/complete llm-client messages {:max-tokens 500 :temperature 0.7})]
         {:type :llm-response
          :response response})))))

(defn- -update-input
  "Update text input with message and handle submission."
  [state msg]
  (let [[input cmd] (charm/text-input-update (:input state) msg)]
    (cond
      ;; Enter key - submit input
      (and (charm/key-match? msg "enter") (seq (charm/text-input-value input)))
      (let [value (str/trim (charm/text-input-value input))
            new-input (charm/text-input-reset input)]
        (if (.startsWith value "/")
          ;; Handle command
          (-handle-command (assoc state :input new-input) value)
          ;; Send to LLM
          (let [llm-cmd (-send-to-llm state value)]
            [(assoc state
                    :input new-input
                    :thinking? true
                    :messages (conj (:messages state)
                                    {:message/role :user
                                     :message/content value
                                     :message/timestamp (Date.)}))
             (charm/batch llm-cmd
                          (charm/cmd (fn [] {:type :spinner-start})))])))

      ;; Regular input update
      :else
      [(assoc state :input input) cmd])))

(defn- -handle-llm-response
  "Handle LLM response message."
  [state {:keys [response]}]
  (let [content (:content response)]
    (if content
      (do
        (model/store-message (:conn state) (:session-id state) :assistant content
                             :model (:model response))
        [(assoc state
                :thinking? false
                :spinner nil
                :messages (conj (:messages state)
                                {:message/role :assistant
                                 :message/content content
                                 :message/timestamp (Date.)}))
         nil])
      [(assoc state
              :thinking? false
              :spinner nil
              :error (str "Error: " (:error response "Unknown error")))
       nil])))

(defn update-fn
  "Update function for charm/run.
   Receives [state msg] and returns [new-state cmd]."
  [state msg]
  (cond
    ;; Window resize - use values from message if available, otherwise keep current
    (charm/window-size? msg)
    (let [width (or (:width msg) (:width state) 80)
          height (or (:height msg) (:height state) 24)]
      [(assoc state
              :width width
              :height height
              :input (-create-input-box width))
       nil])

    ;; Quit commands
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c"))
    [state charm/quit-cmd]

    ;; LLM response
    (= :llm-response (:type msg))
    (-handle-llm-response state msg)

    ;; Spinner tick (for animation)
    (= :spinner-tick (:type msg))
    (if-let [spinner (:spinner state)]
      (let [[new-spinner cmd] (charm/spinner-update spinner msg)]
        [(assoc state :spinner new-spinner) cmd])
      [state nil])

    ;; Start spinner
    (= :spinner-start (:type msg))
    (let [spinner-cfg (charm/spinner :dot :id :llm-thinking)
          [spinner cmd] (charm/spinner-init spinner-cfg)]
      [(assoc state :spinner spinner) cmd])

    ;; Text input handling (default)
    :else
    (-update-input state msg)))

;; ═══════════════════════════════════════════════════════════════
;; VIEW FUNCTION
;; ═══════════════════════════════════════════════════════════════

(defn- -wrap-text
  "Wrap text to fit within width."
  [text max-width]
  (if (<= (count text) max-width)
    [text]
    (loop [words (str/split text #"\s+")
           line ""
           lines []]
      (if (empty? words)
        (if (seq line) (conj lines line) lines)
        (let [word (first words)
              test-line (if (seq line) (str line " " word) word)]
          (if (<= (count test-line) max-width)
            (recur (rest words) test-line lines)
            (recur (rest words) word (conj lines line))))))))

(defn- -render-message
  "Render a single message."
  [msg width]
  (let [role (:message/role msg)
        content (:message/content msg)
        timestamp (:message/timestamp msg)
        time-str (when timestamp (-format-timestamp timestamp))
        content-width (- width 4)]
    (case role
      :user
      (str (charm/render timestamp-style (str "[" time-str "] "))
           (charm/render user-header-style "You:\n")
           (str/join "\n" (map #(str "  " (charm/render content-style %))
                               (-wrap-text content content-width)))
           "\n")

      :assistant
      (str (charm/render timestamp-style (str "[" time-str "] "))
           (charm/render assist-header-style "Assistant:\n")
           (str/join "\n" (map #(str "  " (charm/render content-style %))
                               (-wrap-text content content-width)))
           "\n")

      ;; system/tool
      (let [role-style (case role
                         :system (charm/style :fg system-color :bold true)
                         :tool (charm/style :fg tool-color :bold true)
                         (charm/style :fg (charm/ansi 250)))
            role-name (case role
                        :system "System"
                        :tool "Tool"
                        (str/capitalize (name role)))]
        (str (charm/render timestamp-style (str "[" time-str "] "))
             (charm/render role-style (str role-name ": "))
             content
             "\n")))))

(defn- -render-input-box
  "Render the full-width input box with borders."
  [state]
  (let [width (:width state)
        top (str (charm/render border-style "╭")
                 (charm/render border-style (apply str (repeat (- width 2) "─")))
                 (charm/render border-style "╮"))
        bottom (str (charm/render border-style "╰")
                    (charm/render border-style (apply str (repeat (- width 2) "─")))
                    (charm/render border-style "╯"))
        status (-session-status-text (d/db (:conn state)) (:session-id state))
        input-line (charm/text-input-view (:input state))
        ;; Pad input line to full width
        input-visible (str "│ " input-line)
        input-padding (max 0 (- width (count input-visible) 1))
        full-input (str input-visible
                        (apply str (repeat input-padding " "))
                        (charm/render border-style "│"))]
    (str top "\n"
         full-input "\n"
         bottom "\n"
         (charm/render status-style (str "  " status)) "\n")))

(defn- -render-header
  "Render the header with banner and metadata."
  [state]
  (let [width (:width state)
        banner (:banner state)
        model-name (or (:model-name state) "Not configured")
        title (:session/title (:session state) "Untitled")
        path (:session/project-path (:session state) "")
        ;; Create bordered content
        content (str banner "\n\n"
                     "  Model:   " model-name "\n"
                     "  Session: " title "\n"
                     "  Path:    " path)
        lines (str/split-lines content)
        max-len (apply max (map count lines))
        box-width (min width (+ max-len 4))
        top (str (charm/render border-style "╭")
                 (charm/render border-style (apply str (repeat (- box-width 2) "─")))
                 (charm/render border-style "╮"))
        bottom (str (charm/render border-style "╰")
                    (charm/render border-style (apply str (repeat (- box-width 2) "─")))
                    (charm/render border-style "╯"))]
    (str top "\n"
         (str/join "\n" (map #(str (charm/render border-style "│ ")
                                   %
                                   (apply str (repeat (- box-width (count %) 3) " "))
                                   (charm/render border-style "│"))
                             lines))
         "\n" bottom "\n")))

(defn- -render-thinking-indicator
  "Render thinking indicator with spinner."
  [state]
  (if-let [spinner (:spinner state)]
    (str "  " (charm/spinner-view spinner) " Thinking...\n")
    ""))

(defn view-fn
  "View function for charm/run. Returns the complete UI string."
  [state]
  (let [width (:width state)
        header (-render-header state)
        messages (str/join "\n" (map #(-render-message % width) (:messages state)))
        thinking (-render-thinking-indicator state)
        input-box (-render-input-box state)
        help-text "\nCommands: /quit, /help, /history, /clear"]
    (str header
         messages
         thinking
         "\n"
         input-box
         (charm/render status-style help-text))))

;; ═══════════════════════════════════════════════════════════════
;; PUBLIC API
;; ═══════════════════════════════════════════════════════════════

(defn run-tui
  "Run the TUI using charm/run with full event-driven architecture."
  [conn session-id]
  (let [llm-client (when-let [token (System/getenv "HF_TOKEN")]
                     (llm/hugging-face-client
                      {:api-token token
                       :model "meta-llama/Meta-Llama-3.1-8B-Instruct"
                       :provider "hyperbolic"}))
        initial-state (init-state conn session-id llm-client)]
    (charm/run {:init (fn [] [initial-state nil])
                :update update-fn
                :view view-fn
                :alt-screen false})))

(defn run-tui-new-session
  "Create a new session and run TUI."
  [conn project-path]
  (let [session-id (model/create-session conn project-path)]
    (println (str "Created new session: " session-id))
    (run-tui conn session-id)))
