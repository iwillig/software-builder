# AGENTS.md - Software Builder Project Context

## Project Overview

**software-builder** is a Clojure CLI application for automated software building and deployment workflows. It features async I/O, embedded database support, and comprehensive CLI tooling.

## Key Libraries & Usage Patterns

### Core Language & Utilities
- **org.clojure/clojure {:mvn/version "1.12.3"}** - Latest stable Clojure version
- **failjure/failjure {:mvn/version "2.3.0"}** - Railway-oriented programming for error handling
  - Use `f/ok` for success, `f/fail` for errors
  - Chain with `f/attempt-all`, `f/chain`, `f/map-failure`
- **clj-commons/pretty {:mvn/version "3.6.7"}** - Pretty printing with colorization
  - Requiring: `[clj-commons.pretty.repl :refer [install-pretty-print]]`
- **funcool/lentes {:mvn/version "1.3.3"}** - Functional lenses
  - Use for nested data transformations: `(l/in [:config :database :url])`

### CLI & Arguments
- **cli-matic/cli-matic {:mvn/version "0.5.4"}** - CLI argument parsing
  - Define commands as data with `:command`, `:args`, `:opts`, `:runs`, `:on-error`
  - Supports subcommands, flags, validation

### Async & Network
- **manifold/manifold {:mvn/version "0.4.3"}** - Stream and deferred abstraction
  - `manifold.deferred/deferred` for async computations
  - `manifold.stream/stream` for event-driven flows
- **aleph/aleph {:mvn/version "0.9.5"}** - Async HTTP/WebSocket server
  - `aleph.http/start-server` for HTTP endpoints
  - `aleph.http/websocket-server` for WS connections
  - Returns manifold deferreds for composable async

### Database
- **datalevin/datalevin {:mvn/version "0.10.5"}** - Datalog database (Datomic-compatible)
  - Embedded, no external server needed
  - Requiring: `[datalevin.api :as d]`
  - Schema: `{:db/valueType ..., :db/cardinality ...}`
  - Query with Datalog syntax

### Terminal UI - Bling (simple styling)
- **io.github.paintparty/bling {:mvn/version "0.9.2"}** - Terminal colors and styling
  - `(bling [:green "Success!"])` for colored output
  - Supports nested styling vectors

### Terminal UI - Charm.clj (full TUI framework)
- **io.github.timokramer/charm.clj** - TUI library inspired by Bubble Tea
  - **Architecture**: Elm-style Model-Update-View pattern
  - **Run modes**: JVM, native-image, or Babashka
  - **Components**: spinner, text-input, list, paginator, timer, progress, help, table, viewport
  - **Features**: Keyboard/mouse events, styling (ANSI/256/true color), borders, padding, alignment, efficient rendering with line diffing
  - **Dependencies**: JLine3 for terminal I/O, core.async for async handling

#### Charm.clj Quick Start
```clojure
(require '[charm.core :as charm])

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q") [state charm/quit-cmd]
    (charm/key-match? msg "k") [(update state :count inc) nil]
    (charm/key-match? msg "j") [(update state :count dec) nil]
    :else [state nil]))

(defn view [state]
  (str "Count: " (:count state) "\n\n"
       "j/k to change, q to quit"))

(charm/run {:init {:count 0}
            :update update-fn
            :view view})
```

#### Charm.clj Core API
```clojure
;; Run the application
(charm/run {:init initial-state
            :update (fn [state msg] [new-state cmd])
            :view (fn [state] "rendered string")
            :alt-screen false      ; Use alternate screen buffer
            :mouse :cell           ; Mouse mode: nil, :normal, :cell, :all
            :focus-reporting false ; Report focus events
            :fps 60})              ; Target frames per second

;; Commands and async operations
charm/quit-cmd                    ; Quit the program
(charm/cmd (fn [] ...))           ; Create async command
(charm/batch cmd1 cmd2 cmd3)      ; Run commands in parallel
(charm/sequence-cmds cmd1 cmd2)   ; Run commands in sequence
```

#### Charm.clj Message Handling
```clojure
;; Message type predicates
(charm/key-press? msg)      ; Keyboard input
(charm/mouse? msg)          ; Mouse event
(charm/window-size? msg)    ; Terminal resized
(charm/quit? msg)           ; Quit signal

;; Key matching
(charm/key-match? msg "q")        ; Letter q
(charm/key-match? msg "ctrl+c")   ; Ctrl+C
(charm/key-match? msg "enter")    ; Enter key
(charm/key-match? msg :up)        ; Arrow keys: :up :down :left :right :home :end :pgup :pgdown

;; Modifier checks
(charm/ctrl? msg)
(charm/alt? msg)
(charm/shift? msg)
```

#### Charm.clj Styling
```clojure
;; Create styles
(def my-style
  (charm/style :fg charm/red
               :bg charm/black
               :bold true
               :italic false
               :underline true
               :padding [1 2]        ; [vertical horizontal] or [top right bottom left]
               :margin [1 1]
               :border charm/rounded-border  ; or normal-border, thick-border, double-border
               :width 80
               :height 24
               :align :center))     ; :left, :center, :right

;; Apply styles
(charm/render my-style "Hello!")
(charm/styled "Hello!" :fg charm/green :bold true)

;; Colors
(charm/rgb 255 100 50)       ; True color
(charm/hex "#ff6432")        ; Hex color
(charm/ansi :red)            ; ANSI 16 colors
(charm/ansi256 196)           ; 256 color palette
(def predefined-colors [charm/black charm/red charm/green charm/yellow
                        charm/blue charm/magenta charm/cyan charm/white])

;; Layout
(charm/join-horizontal :top block1 block2)     ; Join blocks horizontally
(charm/join-vertical :center block1 block2)   ; Join blocks vertically
```

#### Charm.clj Components
```clojure
;; Spinner - loading indicator
(def spinner-state (charm/spinner-init :dots))  ; :dots, :line, :mini-dot, etc.
(charm/spinner-view spinner-state)
(charm/spinning? spinner-state)

;; Text Input
(def input-state (charm/text-input-init :placeholder "Type here..."
                                        :echo charm/echo-normal))  ; echo-password, echo-none
(charm/text-input-update input-state msg)
(charm/text-input-view input-state)
(charm/text-input-value input-state)
(charm/text-input-set-value input-state "new value")
(charm/text-input-focus input-state)
(charm/text-input-blur input-state)

;; List Selection
(def list-state (charm/list-init [{:title "Item 1" :description "Desc 1"}
                                   {:title "Item 2" :description "Desc 2"}]
                                  :show-descriptions true
                                  :cursor-style (charm/style :fg charm/cyan)))
(charm/list-update list-state msg)
(charm/list-view list-state)
(charm/list-selected-item list-state)
(charm/list-selected-index list-state)
(charm/list-set-items list-state new-items)

;; Paginator
(def pager-state (charm/paginator-init :total-pages 10 :current-page 1))
(charm/paginator-view pager-state)
(charm/paginator-next-page pager-state)
(charm/paginator-prev-page pager-state)

;; Progress Bar
(def progress-state (charm/progress-init :percent 0.5))
(charm/progress-view progress-state)
(charm/progress-set progress-state 0.75)
(charm/progress-increment progress-state 0.1)
(charm/progress-complete? progress-state)

;; Help Display
(def help-state (charm/help-init (charm/help-from-pairs
                                   ["j/k" "Navigate"
                                    "enter" "Select"
                                    "q" "Quit"])
                                 :width 60))
(charm/help-view-short help-state)
(charm/help-view-full help-state)
(charm/help-toggle-show-all help-state)

;; Table
(def table-state (charm/table-init [["Name" "Age" "City"]
                                     ["Alice" "30" "NYC"]
                                     ["Bob" "25" "LA"]]
                                    :cursor 0))
(charm/table-view table-state)
(charm/table-selected-row table-state)

;; Viewport - scrollable content
(def viewport-state (charm/viewport-init "Large content..." :width 80 :height 24))
(charm/viewport-update viewport-state msg)
(charm/viewport-view viewport-state)
(charm/viewport-scroll-to viewport-state 50)     ; Scroll to line
(charm/viewport-scroll-percent viewport-state 0.5) ; Scroll to percentage
(charm/viewport-at-top? viewport-state)
(charm/viewport-at-bottom? viewport-state)
```

#### Charm.clj Complete Example
```clojure
(ns myapp.core
  (:require [charm.core :as charm]))

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def count-style
  (charm/style :fg charm/cyan
               :padding [0 1]
               :border charm/rounded-border))

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c"))
    [state charm/quit-cmd]

    ;; Increment
    (or (charm/key-match? msg "k")
        (charm/key-match? msg :up))
    [(update state :count inc) nil]

    ;; Decrement
    (or (charm/key-match? msg "j")
        (charm/key-match? msg :down))
    [(update state :count dec) nil]

    :else [state nil]))

(defn view [state]
  (str (charm/render title-style "Counter App") "\n\n"
       (charm/render count-style (str (:count state))) "\n\n"
       "j/k or arrows to change\n"
       "q to quit"))

(defn -main [& args]
  (charm/run {:init {:count 0}
              :update update-fn
              :view view
              :alt-screen true}))
```

### Terminal UI - JLine (low-level terminal I/O)
- **org.jline/jline {:mvn/version "3.30.6"}** - Java console library for terminal I/O
  - **Used by**: charm.clj (transitive dependency), can also be used directly
  - **Purpose**: Readline-like functionality, line editing, history, completion
  - **Features**: Cross-platform, Emacs/Vi editing modes, ANSI support, Unicode handling

#### JLine Core Modules

| Module | Maven Coordinate | Description |
|--------|------------------|-------------|
| jline-terminal | `org.jline/jline-terminal` | Core terminal abstraction |
| jline-reader | `org.jline/jline-reader` | Line editing and reading |
| jline-console | `org.jline/jline-console` | Higher-level console abstractions |
| jline-style | `org.jline/jline-style` | Styling and coloring |
| jline-prompt | `org.jline/jline-prompt` | Modern prompt API |
| jline-builtins | `org.jline/jline-builtins` | Built-in commands |

#### JLine Basic Setup
```clojure
(import '(org.jline.terminal TerminalBuilder)
        '(org.jline.reader LineReaderBuilder))

;; Create terminal
(def terminal
  (-> (TerminalBuilder/builder)
      (.system true)
      (.build)))

;; Create line reader
(def line-reader
  (-> (LineReaderBuilder/builder)
      (.terminal terminal)
      (.build)))
```

#### JLine Reading Input
```clojure
;; Simple prompt and read
(def input (.readLine line-reader "prompt> "))

;; Masked input (passwords)
(def password (.readLine line-reader "password> " (char 0)))

;; Read with no echo
(def sensitive (.readLine line-reader "secret> " nil nil "*"))
```

#### JLine History Management
```clojure
;; History is automatically persisted
;; Access history
(def history (.getHistory line-reader))

;; Add to history (done automatically on readLine)
(.add history "previous command")

;; Search history
(.searchBackward history "partial match")

;; Configuration options
;; ~/.jline.rc or Java system properties:
;; - jline.history.file=~/.myapp-history
;; - jline.history.size=10000
```

#### JLine Tab Completion
```clojure
(import '(org.jline.reader.impl.completer StringsCompleter))

;; Simple string completion
(def completer (StringsCompleter. ["help" "status" "build" "deploy"]))

;; With description
(def completer
  (-> (StringsCompleter. [(Candidate. "help" "help" nil "Show help" nil nil true)
                           (Candidate. "build" "build" nil "Build project" nil nil true)])
      (.build)))

;; Set on reader
(.setCompleter line-reader completer)

;; Or during builder
(def line-reader
  (-> (LineReaderBuilder/builder)
      (.terminal terminal)
      (.completer completer)
      (.build)))
```

#### JLine Syntax Highlighting
```clojure
(import '(org.jline.reader Highlighter)
        '(org.jline.utils AttributedString AttributedStyle))

;; Custom highlighter
(def highlighter
  (reify Highlighter
    (highlight [_ _ input _]
      (let [builder (AttributedStringBuilder.)]
        ;; Highlight keywords
        (doseq [word (str/split input #"\s+")]
          (cond
            (= word "SELECT") (.append builder "SELECT" 
                                       (.. AttributedStyle/DEFAULT 
                                           (foreground AttributedStyle/YELLOW)))
            (= word "FROM") (.append builder "FROM" 
                                     (.. AttributedStyle/DEFAULT 
                                         (foreground AttributedStyle/CYAN)))
            :else (.append builder word)))
        (.toAttributedString builder)))))

(.setHighlighter line-reader highlighter)
```

#### JLine Terminal Capabilities
```clojure
;; Check terminal capabilities
(.isAnsiSupported terminal)     ; ANSI escape sequences
(.isInteractive terminal)        ; Can read interactive input
(.getType terminal)              ; Type: "dumb", "xterm", "windows", etc.

;; Get terminal size
(def size (.getSize terminal))
(def width (.getColumns size))
(def height (.getRows size))

;; Terminal output
(def writer (.writer terminal))
(.println writer "Output with terminal")
(.flush writer)
```

#### JLine Styled Output
```clojure
(import '(org.jline.utils AttributedString AttributedStyle))

;; Create styled string
(def styled
  (-> (AttributedStringBuilder.)
      (.append "Error: " 
               (.. AttributedStyle/DEFAULT 
                   (foreground AttributedStyle/RED)
                   (bold)))
      (.append "Something went wrong!"
               (.. AttributedStyle/DEFAULT 
                   (foreground AttributedStyle/BLACK)))
      (.toAttributedString)))

(.println (.writer terminal) styled)
```

#### JLine Prompt API (Modern)
```clojure
(import '(org.jline.prompt PromptBuilder))

;; Build prompt with styled elements
(def prompt
  (-> (PromptBuilder.)
      (.styled (.foreground (.bold (AttributedStyleBuilder.)) AttributedStyle/BLUE))
      (.text "myapp")
      (.text "> ")
      (.build)))

;; Use with reader
(def input (.readLine line-reader prompt))
```

#### JLine Key Bindings
```clojure
;; Customize key mapping
(import '(org.jline.reader KeyMap Binding))

;; Get key map
(def emacs-map (.getKeyMaps line-reader))

;; Bind custom action
(.bind emacs-map
       (reify Binding
         (execute [_ reader _]
           ;; Custom action
           (.addSuffix reader "inserted text")))
       "\C-x\C-e")  ; C-x C-e

;; Emacs vs Vi mode
(.get var LineReader/EDITING_MODE)  ; :emacs or :vi
```

#### JLine Complete Example
```clojure
(ns myapp.terminal
  (:require [clojure.string :as str])
  (:import (org.jline.terminal TerminalBuilder)
           (org.jline.reader LineReaderBuilder)
           (org.jline.reader.impl.completer StringsCompleter)
           (org.jline.utils AttributedStyle AttributedStringBuilder)))

(defn create-reader []
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.build))
        completer (StringsCompleter. ["help" "build" "test" "deploy" "exit"])
        reader (-> (LineReaderBuilder/builder)
                   (.terminal terminal)
                   (.completer completer)
                   (.build))]
    {:terminal terminal
     :reader reader}))

(defn print-colored [terminal text color]
  (let [writer (.writer terminal)
        styled (-> (AttributedStringBuilder.)
                   (.append text (.. AttributedStyle/DEFAULT 
                                     (foreground color)))
                   (.toAttributedString))]
    (.println writer styled)
    (.flush writer)))

(defn repl-loop [{:keys [reader terminal]}]
  (loop []
    (try
      (let [input (.readLine reader "myapp> ")]
        (case (str/trim input)
          "" (recur)
          "exit" (println "Goodbye!")
          "help" (do
                   (print-colored terminal 
                                  "Available commands: help, build, test, deploy, exit"
                                  AttributedStyle/GREEN)
                   (recur))
          (do
            (println "You entered:" input)
            (recur))))
      (catch org.jline.reader.UserInterruptException _
        (println "Interrupted")
        (recur))
      (catch org.jline.reader.EndOfFileException _
        (println "EOF")))))

(defn -main [& args]
  (let [ctx (create-reader)]
    (print-colored (:terminal ctx) 
                   "Welcome to MyApp Terminal"
                   AttributedStyle/BLUE)
    (repl-loop ctx)
    (.close (:terminal ctx))))
```

### Development Tools
- **io.github.tonsky/clj-reload {:mvn/version "0.9.8"}** - Hot code reloading
  - Call `(clj-reload.core/reload)` in REPL
- **clj-nrepl-eval** - REPL evaluation tool for AI assistants
  - `clj-nrepl-eval -p <port> "(your-clojure-expr)"` - Evaluate expressions
  - `clj-nrepl-eval -p 7889 "(clojure.repl/doc map)"` - Get function docs
  - `clj-nrepl-eval -p 7889 "(map inc [1 2 3])"` - Test functions
- **etaoin/etaoin {:mvn/version "1.1.43"}** - WebDriver for browser automation
- **clj-kondo/clj-kondo {:mvn/version "2022.09.08"}** - Static analysis linter
- **org.clojure/test.check {:mvn/version "1.1.1"}** - Property-based testing
- **com.gfredericks/test.chuck {:mvn/version "0.2.13"}** - Test.check utilities
- **lambdaisland/kaocha {:mvn/version "1.91.1392"}** - Test runner
  - Run with: `bb test` or `clj -M:test`

### Language Server
- **com.github.clojure-lsp/clojure-lsp {:mvn/version "2025.08.25-14.21.46"}** - LSP implementation

## REPL Workflow - AGENT REQUIREMENTS

**CRITICAL:** Agents MUST use the **clj-nrepl-eval bash command** for ALL REPL interactions. This is the only approved method for evaluating Clojure code.

### Agent REPL Policy (REQUIRED)

1. **ALWAYS use bash to run clj-nrepl-eval** - Example: `bash "clj-nrepl-eval -p 7889 '(+ 1 1)'"`
2. **Verify nREPL is running** - Test connection before attempting evaluations
3. **NEVER start or stop nREPL** - This is the user's responsibility exclusively

### Connection Verification

Before writing any code, agents MUST:

1. **Test the connection:**
   ```bash
   clj-nrepl-eval -p 7889 "(+ 1 1)"
   # Expected: Returns 2
   ```

2. **If connection fails:** Ask the user "Please start your nREPL server (e.g., `bb nrepl` or `clj -M:dev:nrepl`)"

3. **Never proceed** with code generation until nREPL connection is confirmed

### User Responsibilities (DO NOT do these as an agent)
- Starting nREPL: `bb nrepl` or `clj -M:dev:nrepl`
- Stopping nREPL: Ctrl+C or closing the terminal
- Managing nREPL process lifecycle

### Agent Responsibilities (ALWAYS do these)
- Test code in REPL using bash with clj-nrepl-eval before saving
- Evaluate expressions to understand existing code
- Validate functions work correctly before writing to files
- Explore documentation: `clj-nrepl-eval -p 7889 "(clojure.repl/doc function-name)"`
- Load namespaces: `clj-nrepl-eval -p 7889 "(require '[namespace] :reload)"`

### Prohibited Actions
Agents must NEVER:
- Execute `bb nrepl`, `lein repl`, `clj -M:nrepl`, or similar commands
- Start, stop, or restart the nREPL process
- Assume nREPL is running without verifying first

## Project Structure

```
src/
  software_builder/
    main.clj          ; Entry point
resources/            ; Static resources
```

## Common Patterns

### Error Handling with Failjure
```clojure
(require '[failjure.core :as f])
(import '(java.io FileNotFoundException))

(f/attempt-all [config (read-config "build.yaml")
              validated (validate-config config)
              result (run-build validated)]
  (println "Build succeeded:" result)
  (f/when-failed [e]
    (println "Error:" (f/message e))))
```

### Database with Datalevin

Datalevin is a durable Datalog database built on LMDB that combines multiple paradigms:
- **Datalog queries** - Datomic-compatible with automatic implicit joins
- **Key-Value store** - Native EDN storage
- **Full-text search** - T-Wand algorithm-based engine
- **Vector database** - SIMD-accelerated indexing
- **Document DB** - JSON/EDN/Markdown with path indexing

#### Basic Setup
```clojure
(require '[datalevin.core :as d])

;; Schema is a map of maps (not vector like Datomic)
;; Define special attributes; others are treated as EDN blobs
(def schema
  {:user/name  {:db/valueType :db.type/string
                :db/unique    :db.unique/identity}
   :user/roles {:db/cardinality :db.cardinality/many}
   :user/friends {:db/valueType   :db.type/ref
                  :db/cardinality :db.cardinality/many}})

;; Create/open database connection
(def conn (d/get-conn "/tmp/mydb" schema))

;; Or connect to remote Datalevin server
;; (def conn (d/get-conn "dtlv://user:pass@host:8898/mydb" schema))
```

#### Schema Attributes

| Attribute | Purpose | Example Values |
|-----------|---------|---------------|
| `:db/valueType` | Data type | `:db.type/string`, `:db.type/long`, `:db.type/ref`, `:db.type/instant` |
| `:db/cardinality` | One or many values | `:db.cardinality/one` (default), `:db.cardinality/many` |
| `:db/unique` | Unique constraint | `:db.unique/identity` (upsert on match), `:db.unique/value` |
| `:db/index` | Index for queries | `true` |

#### Querying with Datalog
```clojure
;; Transact data with temporary entity IDs (negative integers)
(d/transact! conn
  [{:name "Alice" :db/id -1 :age 30}
   {:name "Bob"   :db/id -2 :age 25}])

;; Basic query
(d/q '[:find ?name
       :where [?e :age ?age]
              [?e :name ?name]
              [(> ?age 25)]]
     (d/db conn))
;; => #{["Alice"]}

;; Query with input parameters
(d/q '[:find ?name ?age
       :in $ ?min-age
       :where [?e :age ?age]
              [?e :name ?name]
              [(> ?age ?min-age)]]
     (d/db conn) 25)

;; Pull API for entity retrieval
(d/q '[:find (pull ?e [*])
       :in $ ?name
       :where [?e :name ?name]]
     (d/db conn) "Alice")
```

#### Key-Value Store Usage
```clojure
;; Open KV database
(def db (d/open-kv "/tmp/mykvdb"))

;; Define tables (called "dbi" in LMDB terminology)
(d/open-dbi db "my-table")

;; Transact operations
(d/transact-kv db
  [[:put "my-table" :key1 "value1"]
   [:put "my-table" :key2 {:edn "data"}]
   [:put "my-table" #inst "2024-01-01" "event" :instant]])

;; Retrieve single value
(d/get-value db "my-table" :key1)
;; => "value1"

;; Range queries
(d/get-range db "my-table" [:closed start-key end-key] :instant)

(d/close-kv db)
```

#### Transaction Modes

**Synchronous** (blocks until persisted):
```clojure
(d/transact! conn tx-data)
```

**Asynchronous** (returns Future, higher throughput):
```clojure
(def future (d/transact-async conn tx-data callback-fn))
;; Wait for completion
(deref future)
```

**Explicit transaction** (for read-modify-write patterns):
```clojure
(d/with-transaction [txn conn]
  (let [current (d/get txn :some-key)]
    (d/put txn :some-key (inc current))))
```

#### Special Features

**Updateable Entities** (Datalevin-specific):
```clojure
(def entity (d/entity db [:user/name "Alice"]))

;; Stage changes for later transaction
(def updated (assoc entity :user/age 42))
;; => {:user/name "Alice", :<STAGED> {:user/age [{:op :assoc} 42]} }

;; Transact the staged entity
(d/transact! conn [updated])

;; Add/retract relationships
(-> entity
    (update :user/age inc)
    (d/add :user/friends {:user/name "Charlie"}))

;; Retract specific values
(d/retract entity :user/friends [{:user/name "Bob"}])
```

**Transaction Functions**:
```clojure
;; Compare-and-swap
[:db/cas entity-id :attribute old-value new-value]

;; Custom transaction functions (for native image compatibility)
;; Use inter-fn or definterfn from datalevin.interpret
```

#### Connection Management
```clojure
;; Always close connections when done
(d/close conn)

;; Database path operations
(d/cleanup "/tmp/mydb")     ; Clear all data
(d/destroy "/tmp/mydb")     ; Delete database files
```

#### Performance Notes
- **Reads**: Concurrent, no locking - each reader sees consistent snapshot
- **Writes**: Serialized (one writer at a time) - avoid long-lived transactions
- **LMDB backend**: High read performance via memory-mapped files
- **Avoid long-lived read transactions**: They prevent page reuse and db growth

### Async with Manifold + Aleph
```clojure
(require '[manifold.deferred :as d])
(require '[aleph.http :as http])

(d/chain
  (http/get "https://api.example.com/builds")
  :body
  slurp
  json/parse-string)
```

### CLI with Cli-matic
```clojure
(require '[cli-matic.core :as cli])

(def CLI-CONFIG
  {:app {:command "builder" :description "Software build tool"}
   :commands [{:command "build"
              :description "Run a build"
              :opts [{:option :target :default "./"}]
              :runs run-build}]})

(defn -main [& args] (cli/run-cmd {:spec CLI-CONFIG} args))
```

## Testing

```bash
# Run all tests
bb test

# Or with kaocha directly
clj -M:test --focus unit

# Reload and test in REPL
(require '[clj-reload.core :as r])
(r/reload)
(require '[kaocha.repl :as k])
(k/run)
```

## Clojure Protocols Reference

Based on [Clojure Protocols Documentation](https://clojure.org/reference/protocols).

### Overview
Protocols provide **high-performance, dynamic polymorphism** as an alternative to Java interfaces. They are fully reified and support runtime extension.

### Key Characteristics
- Named set of named methods with signatures - no implementations required
- Dispatch on first argument type (must have at least one argument)
- A single type can implement multiple protocols
- External extension possible without modifying original types
- Dynamic - does not require AOT compilation

### Defining a Protocol
```clojure
(defprotocol AProtocol
  "A doc string for AProtocol abstraction"
  (bar [a b] "bar docs")
  (baz [a] [a b] [a b c] "baz docs"))  ; multi-arity supported
```

### Implementation Methods
```clojure
;; 1. Inline with deftype/defrecord/reify
(defrecord Foo [a b c]
  P
  (foo [x] a)
  (bar-me [x] b))

;; 2. External extension with extend-type
(extend-type String
  P
  (foo [s] (str "string:" s)))

;; 3. External extension with extend-protocol
(extend-protocol P
  Number
  (foo [n] (* n 2)))

;; 4. Extend via metadata (Clojure 1.10+)
(defprotocol Component
  :extend-via-metadata true
  (start [component]))

(def component (with-meta {:name "db"} {`start (constantly "started")}))
(start component) ; => "started"
```

### Reflection Functions
```clojure
(extends? P MyType)     ; => true if type extends protocol
(satisfies? P obj)        ; => true if object satisfies protocol
(extenders P)             ; => all types extending protocol
```

### Guidelines for Extension
- Only extend protocols you own OR types you own
- If you own neither, extend only in application code (not public libs)
- Extending Clojure's built-in protocols on built-in types is fragile
- Libraries should avoid extending what they don't own

### Protocol vs Multimethod
| Feature | Protocol | Multimethod |
|---------|----------|-------------|
| Dispatch | Type-based (single) | Arbitrary function |
| Performance | Faster (interface-based) | More flexible |
| Extension | Via extend-type | Via defmethod |
| Host interop | Generates Java interfaces | No |

## Aliases Reference

| Alias | Usage | Purpose |
|-------|-------|---------|
| `:dev` | `clj -M:dev` | Development dependencies |
| `:test` | `bb test` | Run kaocha test suite |
| `:nrepl` | `bb nrepl` | Start nREPL server on port 7889 |
| `:main` | `clj -M:main` | Run main namespace |
| `:lint` | `clj -M:lint` | Run clj-kondo linter |
| `:format` | `clj -M:format` | Code formatting |
| `:outdated` | `clj -M:outdated` | Check for dependency updates |
