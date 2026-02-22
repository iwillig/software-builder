(ns software-builder.test-helper
  "Test helper utilities with fixtures for isolated Datalevin databases.
   Provides a per-test database setup that ensures test isolation."
  (:require
   [clojure.test :as t]
   [clojure.java.io :as io]
   [datalevin.core :as d]
   [software-builder.schema :as schema])
  (:import
   (java.lang ProcessHandle)
   (java.util UUID Date)))

;; ═══════════════════════════════════════════════════════════════
;; DYNAMIC VAR FOR TEST DATABASE CONNECTION
;; ═══════════════════════════════════════════════════════════════

(def ^:dynamic *test-conn*
  "Dynamic var holding the current test database connection.
   Bound by with-test-db fixture for each test."
  nil)

;; ═══════════════════════════════════════════════════════════════
;; DATABASE PATH GENERATION
;; ═══════════════════════════════════════════════════════════════

(def ^:private test-id-counter
  "Atom for generating unique test database paths."
  (atom 0))

(defn- generate-test-db-path
  "Generate a unique temporary database path for a test.
   Combines process ID, timestamp counter, and random component."
  []
  (let [pid (.. ProcessHandle current pid)
        counter (swap! test-id-counter inc)
        timestamp (System/currentTimeMillis)]
    (str "/tmp/software-builder-test-" pid "-" timestamp "-" counter)))

;; ═══════════════════════════════════════════════════════════════
;; DATABASE CLEANUP
;; ═══════════════════════════════════════════════════════════════

(defn- delete-directory-recursive
  "Recursively delete a directory and all its contents."
  [dir]
  (when (.exists dir)
    (doseq [file (reverse (file-seq dir))]
      (io/delete-file file true))))

;; ═══════════════════════════════════════════════════════════════
;; FIXTURE DEFINITIONS
;; ═══════════════════════════════════════════════════════════════

(defn with-test-db
  "Fixture that creates a fresh database for each test.
   Binds *test-conn* to the connection, and cleans up after test."
  [test-fn]
  (let [db-path (generate-test-db-path)]
    (try
      ;; Setup: create connection with test schema
      (let [conn (d/get-conn db-path
                             schema/phase-1-schema
                             {:vector-opts schema/*vector-opts*})]
        ;; Bind connection and run test
        (binding [*test-conn* conn]
          (test-fn))
        ;; Teardown: close and cleanup
        (d/close conn))
      (finally
        ;; Ensure cleanup happens even if test throws
        (delete-directory-recursive (io/file db-path))))))

(defn with-test-db-once
  "Fixture that creates a single database for all tests in namespace.
   Use :once fixture type for faster tests when isolation isn't critical.
   Note: Tests must not conflict with each other's data."
  [test-fn]
  (let [db-path (generate-test-db-path)]
    (try
      (let [conn (d/get-conn db-path
                             schema/phase-1-schema
                             {:vector-opts schema/*vector-opts*})]
        (binding [*test-conn* conn]
          (test-fn))
        (d/close conn))
      (finally
        (delete-directory-recursive (io/file db-path))))))

;; ═══════════════════════════════════════════════════════════════
;; ASSERTION HELPERS
;; ═══════════════════════════════════════════════════════════════

(defn is-uuid?
  "Predicate to check if value is a UUID."
  [v]
  (instance? UUID v))

(defn is-date?
  "Predicate to check if value is a Date."
  [v]
  (instance? Date v))

(defn assert-session-valid
  "Assert that a session map has all required fields."
  [session]
  (t/is (map? session) "Session should be a map")
  (t/is (is-uuid? (:session/id session)) "Session should have a UUID id")
  (t/is (string? (:session/project-path session)) "Session should have a project path")
  (t/is (keyword? (:session/status session)) "Session should have a status keyword")
  (t/is (is-date? (:session/created-at session)) "Session should have a created-at timestamp"))

(defn assert-message-valid
  "Assert that a message map has all required fields."
  [msg]
  (t/is (map? msg) "Message should be a map")
  (t/is (is-uuid? (:message/id msg)) "Message should have a UUID id")
  (t/is (keyword? (:message/role msg)) "Message should have a role keyword")
  (t/is (string? (:message/content msg)) "Message should have string content")
  (t/is (number? (:message/sequence msg)) "Message should have a sequence number"))

(defn assert-memory-valid
  "Assert that a memory map has all required fields."
  [memory]
  (t/is (map? memory) "Memory should be a map")
  (t/is (is-uuid? (:memory/id memory)) "Memory should have a UUID id")
  (t/is (keyword? (:memory/type memory)) "Memory should have a type keyword")
  (t/is (string? (:memory/content memory)) "Memory should have content")
  (t/is (number? (:memory/current-strength memory)) "Memory should have a current strength"))
