(ns software-builder.main-test
  "Tests for software-builder.main namespace."
  (:require
   [clojure.test :as t :refer [deftest testing is use-fixtures]]
   [datalevin.core :as d]
   [software-builder.main :as main]
   [software-builder.model :as model]
   [software-builder.tui :as tui]
   [software-builder.test-helper :as th])
  (:import
   (java.util UUID)))

(use-fixtures :each th/with-test-db)

;; ═══════════════════════════════════════════════════════════════
;; PRIMARY COMMAND IMPLEMENTATION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest main-cmd-impl-test
  (testing "main command with valid project path starts TUI"
    ;; Mock the TUI functions to avoid blocking
    (let [result (with-redefs [tui/run-tui (fn [_ _] nil)
                               tui/run-tui-new-session (fn [_ _] nil)]
                   (main/main-cmd-impl th/*test-conn* "/tmp/test"))]
      (is (= 0 result))))

  (testing "main command with empty path defaults to current directory"
    (let [current-dir (System/getProperty "user.dir")
          output (with-out-str
                   (with-redefs [tui/run-tui-new-session (fn [_ path]
                                                           (println (str "Using path: " path)))]
                     (main/main-cmd-impl th/*test-conn* "")))]
      (is (re-find (re-pattern current-dir) output))))

  (testing "main command with nil path defaults to current directory"
    (let [current-dir (System/getProperty "user.dir")
          output (with-out-str
                   (with-redefs [tui/run-tui-new-session (fn [_ path]
                                                           (println (str "Using path: " path)))]
                     (main/main-cmd-impl th/*test-conn* nil)))]
      (is (re-find (re-pattern current-dir) output))))

  (testing "main command resumes existing session for same path"
    ;; Create a session first
    (let [session-id (model/create-session th/*test-conn* "/tmp/resume-test")
          output (with-out-str
                   (with-redefs [tui/run-tui (fn [_ _] nil)]
                     (main/main-cmd-impl th/*test-conn* "/tmp/resume-test")))]
      (is (re-find #"Resuming existing session" output))
      (is (re-find (re-pattern (str session-id)) output))))

  (testing "main command creates new session for new path"
    (let [output (with-out-str
                   (with-redefs [tui/run-tui-new-session (fn [_ _] nil)]
                     (main/main-cmd-impl th/*test-conn* "/tmp/new-tui-test")))]
      ;; Should not mention resuming since it's a new session
      (is (not (re-find #"Resuming existing session" output))))))

;; ═══════════════════════════════════════════════════════════════
;; SESSION COMMAND IMPLEMENTATION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest session-list-cmd-impl-test
  (testing "list shows no sessions when empty"
    (let [output (with-out-str (main/session-list-cmd-impl th/*test-conn*))]
      (is (re-find #"No active sessions found" output))))

  (testing "list shows created sessions"
    ;; Create a session first
    (model/create-session th/*test-conn* "/tmp/listed-project")
    ;; Now list should show it
    (let [output (with-out-str (main/session-list-cmd-impl th/*test-conn*))]
      (is (re-find #"Active Sessions" output))
      (is (re-find #"/tmp/listed-project" output))
      (is (re-find #"ID:" output)))))

(deftest session-create-cmd-impl-test
  (testing "create session with valid path"
    (let [output (with-out-str (main/session-create-cmd-impl th/*test-conn* "/tmp/test-project"))]
      (is (re-find #"Created new session" output))
      (is (re-find #"/tmp/test-project" output))))

  (testing "create session with empty path returns error"
    (let [result (main/session-create-cmd-impl th/*test-conn* "")]
      (is (= 1 result))))

  (testing "create session with nil path returns error"
    (let [result (main/session-create-cmd-impl th/*test-conn* nil)]
      (is (= 1 result)))))

(deftest session-show-cmd-impl-test
  (testing "show session with valid ID"
    ;; Create and show a session
    (let [session-id (model/create-session th/*test-conn* "/tmp/show-test")
          output (with-out-str (main/session-show-cmd-impl th/*test-conn* (str session-id)))]
      (is (re-find #"Session Details" output))
      (is (re-find #"/tmp/show-test" output))
      (is (re-find #":active" output))
      (is (re-find #"Statistics" output))))

  (testing "show session with messages"
    ;; Create session with messages
    (let [session-id (model/create-session th/*test-conn* "/tmp/msg-test")]
      (model/store-message th/*test-conn* session-id :user "Hello")
      (model/store-message th/*test-conn* session-id :assistant "Hi there")
      ;; Show the session
      (let [output (with-out-str (main/session-show-cmd-impl th/*test-conn* (str session-id)))]
        (is (re-find #"Total messages: 2" output))
        (is (re-find #"User messages: 1" output))
        (is (re-find #"Assistant messages: 1" output)))))

  (testing "show session with invalid UUID returns error"
    (let [result (main/session-show-cmd-impl th/*test-conn* "not-a-uuid")]
      (is (= 1 result))))

  (testing "show non-existent session returns error"
    (let [random-uuid (UUID/randomUUID)
          result (main/session-show-cmd-impl th/*test-conn* (str random-uuid))]
      (is (= 1 result))))

  (testing "show with empty ID returns error"
    (let [result (main/session-show-cmd-impl th/*test-conn* "")]
      (is (= 1 result)))))

;; ═══════════════════════════════════════════════════════════════
;; EXAMPLE / DOCUMENTATION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest example-session-lifecycle-test
  (testing "full session lifecycle with messages"
    ;; Given: A new session
    (let [project-path "/tmp/test-project"
          session-id (model/create-session th/*test-conn* project-path)]

      ;; Then: The session should exist and be valid
      (is (th/is-uuid? session-id) "Should return a UUID")

      ;; When: We add messages to the session
      (model/store-message th/*test-conn* session-id :user "Hello!")
      (model/store-message th/*test-conn* session-id :assistant "Hi there!")

      ;; Then: The messages should be retrievable
      (let [messages (model/get-session-messages (d/db th/*test-conn*) session-id)]
        (is (= 2 (count messages)) "Should have 2 messages")
        (is (= ["Hello!" "Hi there!"] (map :message/content messages))
            "Messages should be in order"))

      ;; Then: Session stats should reflect the activity
      (let [stats (model/session-stats (d/db th/*test-conn*) session-id)]
        (is (= 2 (:total-messages stats)))
        (is (= 1 (:user-messages stats)))
        (is (= 1 (:assistant-messages stats)))
        (is (= 0 (:tool-calls stats)))))))

(deftest example-isolation-test
  (testing "each test gets its own isolated database"
    ;; This test verifies that the previous test's data is not present
    (let [active-sessions (model/get-active-sessions (d/db th/*test-conn*))]
      ;; Previous test created 1 session, but we should see 0
      ;; because this test has its own fresh database
      (is (seqable? active-sessions))
      (is (empty? active-sessions)))))

(deftest example-helper-assertions-test
  (testing "using test-helper assertion helpers"
    ;; Create a session
    (let [session-id (model/create-session th/*test-conn* "/tmp/test")]

      ;; Use the helper assertion functions
      (th/assert-session-valid
       (model/get-session (d/db th/*test-conn*) session-id))

      ;; Create a message and validate it
      (model/store-message th/*test-conn* session-id :user "Test message")
      (let [messages (model/get-session-messages (d/db th/*test-conn*) session-id)]
        (is (= 1 (count messages)))
        (th/assert-message-valid (first messages))))))

;; ═══════════════════════════════════════════════════════════════
;; CLI CONFIGURATION TEST
;; ═══════════════════════════════════════════════════════════════

(deftest cli-config-test
  (testing "CLI configuration is valid"
    (is (some? main/CLI-CONFIG))
    (is (= "software-builder" (:command main/CLI-CONFIG)))
    ;; Primary command has :runs
    (is (some? (:runs main/CLI-CONFIG)))
    ;; Has path option with -p short flag
    (let [opts (:opts main/CLI-CONFIG)]
      (is (= 1 (count opts)))
      (is (= "path" (:option (first opts))))
      (is (= "p" (:short (first opts)))))
    ;; No subcommands in CLI-CONFIG (handled manually in -main)
    (is (nil? (:subcommands main/CLI-CONFIG)))))

;; ═══════════════════════════════════════════════════════════════
;; HELPER FUNCTION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest get-db-path-test
  (testing "get-db-path returns path with user home"
    (let [path (main/get-db-path)]
      (is (string? path))
      (is (re-find #"software-builder/db" path)))))
