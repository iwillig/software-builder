(ns software-builder.model-test
  "Tests for software-builder.model namespace.
   Uses with-test-db fixture for isolated database per test."
  (:require
   [clojure.test :as t :refer [deftest testing is use-fixtures]]
   [datalevin.core :as d]
   [software-builder.model :as model]
   [software-builder.test-helper :as th])
  (:import
   (java.util UUID Date)))

;; ═══════════════════════════════════════════════════════════════
;; FIXTURE SETUP
;; ═══════════════════════════════════════════════════════════════

(use-fixtures :each th/with-test-db)

;; ═══════════════════════════════════════════════════════════════
;; SESSION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest create-session-test
  (testing "create-session returns a valid UUID"
    (let [session-id (model/create-session th/*test-conn* "/tmp/test-project")]
      (is (instance? UUID session-id) "Should return a UUID")
      (is (not= (UUID/fromString "00000000-0000-0000-0000-000000000000") session-id)
          "Should not be a nil UUID")))

  (testing "create-session stores session with correct data"
    (let [project-path "/tmp/my-project"
          session-id (model/create-session th/*test-conn* project-path)
          session (model/get-session (d/db th/*test-conn*) session-id)]
      (th/assert-session-valid session)
      (is (= project-path (:session/project-path session))
          "Should store the project path")
      (is (= :active (:session/status session))
          "Should default status to :active")
      (is (string? (:session/title session))
          "Should generate a title")
      (is (= session-id (:session/id session))
          "Should have the same UUID"))))

(deftest get-active-sessions-test
  (testing "get-active-sessions returns only active sessions"
    ;; Create two active sessions
    (model/create-session th/*test-conn* "/project/one")
    (model/create-session th/*test-conn* "/project/two")

    (let [active (model/get-active-sessions (d/db th/*test-conn*))]
      (is (= 2 (count active)) "Should find two active sessions")
      (is (every? #(= :active (:session/status %)) active)
          "All returned sessions should be active"))))

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest store-message-test
  (testing "store-message stores a basic message"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")
          msg-id (model/store-message th/*test-conn* session-id :user "Hello, AI!")]
      (is (instance? UUID msg-id) "Should return a message UUID")))

  (testing "stored message has correct attributes"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")
          content "Test message content"
          msg-id (model/store-message th/*test-conn* session-id :assistant content)
          db (d/db th/*test-conn*)
          messages (model/get-session-messages db session-id)]
      (is (= 1 (count messages)) "Should have one message")
      (let [msg (first messages)]
        (th/assert-message-valid msg)
        (is (= content (:message/content msg)))
        (is (= :assistant (:message/role msg)))
        (is (= 0 (:message/sequence msg)) "First message should have sequence 0")))))

(deftest get-session-messages-order-test
  (testing "messages are returned in sequence order"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")]
      ;; Store messages in order
      (model/store-message th/*test-conn* session-id :user "Message 1")
      (model/store-message th/*test-conn* session-id :assistant "Response 1")
      (model/store-message th/*test-conn* session-id :user "Message 2")

      (let [messages (model/get-session-messages (d/db th/*test-conn*) session-id)
            sequences (map :message/sequence messages)]
        (is (= 3 (count messages)))
        (is (= [0 1 2] sequences) "Sequences should be 0, 1, 2")
        (is (= [:user :assistant :user] (map :message/role messages))
            "Roles should be in correct order")))))

(deftest store-message-with-optional-fields-test
  (testing "store-message handles optional fields"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")
          tool-call {:name "read-file" :args {:path "/tmp/test.clj"}}
          tool-result {:success true :content "file contents"}
          msg-id (model/store-message th/*test-conn*
                                      session-id
                                      :tool
                                      "Tool execution"
                                      :model "claude-3-5-sonnet"
                                      :tool-call tool-call
                                      :tool-result tool-result)
          messages (model/get-session-messages (d/db th/*test-conn*) session-id)
          msg (first messages)]
      (is (= "claude-3-5-sonnet" (:message/model msg)))
      (is (string? (:message/tool-call msg)))
      (is (string? (:message/tool-result msg))))))

;; ═══════════════════════════════════════════════════════════════
;; MEMORY TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest create-memory-test
  (testing "create-memory returns a valid UUID"
    (let [mem-id (model/create-memory th/*test-conn* "Test memory content")]
      (is (instance? UUID mem-id))))

  (testing "basic memory has required fields"
    (let [content "Important coding pattern"
          mem-id (model/create-memory th/*test-conn* content :type :fact)
          db (d/db th/*test-conn*)
          memory (d/entity db [:memory/id mem-id])]
      (th/assert-memory-valid memory)
      (is (= content (:memory/content memory)))
      (is (= :fact (:memory/type memory)))
      (is (= 1.0 (:memory/current-strength memory)) "Initial strength should be 1.0"))))

(deftest create-memory-with-session-test
  (testing "memory can be linked to a session"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")
          mem-id (model/create-memory th/*test-conn*
                                      "Session memory"
                                      :type :interaction
                                      :session-id session-id)
          db (d/db th/*test-conn*)
          memory (d/entity db [:memory/id mem-id])]
      (is (= session-id (get-in memory [:memory/session :session/id]))))))

(deftest memory-with-hierarchy-test
  (testing "memories can have parent-child relationships"
    (let [parent-id (model/create-memory th/*test-conn* "Parent theme" :type :theme :level 2)
          child-id (model/create-memory th/*test-conn*
                                        "Child episode"
                                        :type :episode
                                        :level 1
                                        :parent-id parent-id)
          db (d/db th/*test-conn*)
          child (d/entity db [:memory/id child-id])]
      (is (= parent-id (get-in child [:memory/parent :memory/id]))))))

(deftest calculate-strength-test
  (testing "strength decays over time"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")
          mem-id (model/create-memory th/*test-conn* "Decaying memory" :type :fact)
          db (d/db th/*test-conn*)
          memory (d/entity db [:memory/id mem-id])
          now (Date.)
          ;; 10 hours ago - enough to show measurable decay with rate 0.1
          old-time (Date. (- (.getTime now) (* 10 3600 1000)))
          ;; Use :memory/last-reviewed as that's what calculate-strength checks
          old-memory (assoc memory :memory/last-reviewed old-time)
          strength (model/calculate-strength old-memory now)]
      ;; With rate 0.1 and 10 hours, decay factor is e^(-0.1 * 10) = e^-1 = 0.368
      (is (< strength 0.5) "Strength should significantly decay after 10 hours")
      (is (> strength 0.0) "Strength should remain positive"))))

;; ═══════════════════════════════════════════════════════════════
;; SESSION STATS TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest session-stats-test
  (testing "session-stats returns correct message counts"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")]
      ;; Add some messages
      (model/store-message th/*test-conn* session-id :user "User 1")
      (model/store-message th/*test-conn* session-id :assistant "Assistant 1")
      (model/store-message th/*test-conn* session-id :user "User 2")

      (let [stats (model/session-stats (d/db th/*test-conn*) session-id)]
        (is (= 3 (:total-messages stats)))
        (is (= 2 (:user-messages stats)))
        (is (= 1 (:assistant-messages stats)))
        (is (= 0 (:tool-calls stats)))))))

(deftest session-stats-with-tool-calls-test
  (testing "session-stats counts tool calls correctly"
    (let [session-id (model/create-session th/*test-conn* "/tmp/project")]
      (model/store-message th/*test-conn* session-id :user "Request")
      (model/store-message th/*test-conn*
                           session-id
                           :assistant
                           "I'll help"
                           :tool-call {:name "test"})

      (let [stats (model/session-stats (d/db th/*test-conn*) session-id)]
        (is (= 1 (:tool-calls stats)))))))
