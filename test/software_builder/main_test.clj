(ns software-builder.main-test
  "Example tests showing how to use the test-helper fixtures.

   This namespace demonstrates the proper way to set up isolated
   database tests using software-builder.test-helper."
  (:require
   [clojure.test :as t :refer [deftest testing is use-fixtures]]
   [datalevin.core :as d]
   [software-builder.model :as model]
   [software-builder.test-helper :as th]))

;; ═══════════════════════════════════════════════════════════════
;; FIXTURE SETUP
;; ═══════════════════════════════════════════════════════════════

;; Use :each for isolated database per test (slower, safer)
(use-fixtures :each th/with-test-db)

;; Alternative: Use :once for shared database (faster, requires careful test design)
;; (use-fixtures :once th/with-test-db-once)

;; ═══════════════════════════════════════════════════════════════
;; EXAMPLE TESTS
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
        (is (= 1 (:assistant-messages stats)))))))

(deftest example-isolation-test
  (testing "each test gets its own isolated database"
    ;; This test verifies that the previous test's data is not present
    (let [active-sessions (model/get-active-sessions (d/db th/*test-conn*))]
      ;; Previous test created 1 session, but we should see 0
      ;; because this test has its own fresh database
      (is (empty? active-sessions)
          "Database should be empty at start of each test"))))

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
