(ns software-builder.llm-test
  "Tests for software-builder.llm namespace."
  (:require
   [clojure.test :as t :refer [deftest testing is use-fixtures]]
   [software-builder.llm :as llm]
   [software-builder.model :as model]
   [software-builder.test-helper :as th])
  (:import
   (java.util Date)))

(use-fixtures :each th/with-test-db)

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE RECORD TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest make-message-test
  (testing "create a basic message"
    (let [msg (llm/make-message :user "Hello")]
      (is (= :user (:role msg)))
      (is (= "Hello" (:content msg)))
      (is (instance? Date (:timestamp msg)))
      (is (nil? (:metadata msg)))))

  (testing "create message with metadata"
    (let [msg (llm/make-message :assistant "Hi" {:model "test-model"})]
      (is (= :assistant (:role msg)))
      (is (= "Hi" (:content msg)))
      (is (= {:model "test-model"} (:metadata msg)))))

  (testing "create messages for all roles"
    (let [system-msg (llm/make-message :system "You are helpful")
          user-msg (llm/make-message :user "Question")
          assistant-msg (llm/make-message :assistant "Answer")
          tool-msg (llm/make-message :tool "Tool result")]
      (is (= :system (:role system-msg)))
      (is (= :user (:role user-msg)))
      (is (= :assistant (:role assistant-msg)))
      (is (= :tool (:role tool-msg))))))

;; ═══════════════════════════════════════════════════════════════
;; CONVERSATION BUILDER TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest add-system-prompt-test
  (testing "add system prompt to empty conversation"
    (let [conv (llm/add-system-prompt [] "You are helpful")]
      (is (= 1 (count conv)))
      (is (= :system (:role (first conv))))
      (is (= "You are helpful" (:content (first conv))))))

  (testing "add system prompt to existing conversation"
    (let [conv (-> []
                   (llm/add-user-message "Hello")
                   (llm/add-system-prompt "You are helpful"))]
      (is (= 2 (count conv)))
      (is (= :system (:role (first conv)))))))

(deftest add-user-message-test
  (testing "add user message to conversation"
    (let [conv (-> []
                   (llm/add-system-prompt "You are helpful")
                   (llm/add-user-message "What is 2+2?"))]
      (is (= 2 (count conv)))
      (is (= :user (:role (last conv))))
      (is (= "What is 2+2?" (:content (last conv)))))))

(deftest add-assistant-message-test
  (testing "add assistant message to conversation"
    (let [conv (-> []
                   (llm/add-user-message "What is 2+2?")
                   (llm/add-assistant-message "2+2=4"))]
      (is (= 2 (count conv)))
      (is (= :assistant (:role (last conv))))
      (is (= "2+2=4" (:content (last conv)))))))

;; ═══════════════════════════════════════════════════════════════
;; HUGGING FACE CLIENT TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest hugging-face-client-creation-test
  (testing "create client with minimal config"
    (let [client (llm/hugging-face-client {:api-token "test" :model "test-model"})]
      (is (some? client))))

  (testing "client validates with valid config"
    (let [client (llm/hugging-face-client {:api-token "test" :model "test-model"})]
      (is (= :ok (llm/validate-config client)))))

  (testing "client fails validation without token"
    (let [client (llm/hugging-face-client {:model "test-model"})]
      (is (= {:error "API token is required"} (llm/validate-config client)))))

  (testing "client fails validation without model"
    (let [client (llm/hugging-face-client {:api-token "test"})]
      (is (= {:error "Model is required"} (llm/validate-config client))))))

(deftest supported-models-test
  (testing "client returns list of supported models"
    (let [client (llm/hugging-face-client {:api-token "test" :model "test-model"})
          models (llm/supported-models client)]
      (is (seq models))
      (is (every? string? models))
      (is (some #(re-find #"meta-llama" %) models)))))

;; ═══════════════════════════════════════════════════════════════
;; TOOL DEFINITION TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest make-tool-test
  (testing "create a simple tool"
    (let [calc-tool (llm/make-tool
                     {:name "add"
                      :description "Add two numbers"
                      :parameters {:type "object"
                                   :properties {:a {:type "number"}
                                                :b {:type "number"}}
                                   :required ["a" "b"]}
                      :fn (fn [params _] (+ (:a params) (:b params)))})]
      (is (= "add" (llm/tool-name calc-tool)))
      (is (= "Add two numbers" (llm/tool-description calc-tool)))
      (is (some? (llm/tool-parameters-schema calc-tool)))))

  (testing "tool executes successfully"
    (let [calc-tool (llm/make-tool
                     {:name "add"
                      :description "Add two numbers"
                      :parameters {}
                      :fn (fn [params _] (+ (:a params) (:b params)))})
          result (llm/execute-tool calc-tool {:a 5 :b 3} nil)]
      (is (= 8 (:result result)))
      (is (nil? (:error result)))))

  (testing "tool handles errors gracefully"
    (let [error-tool (llm/make-tool
                      {:name "fail"
                       :description "Always fails"
                       :parameters {}
                       :fn (fn [_ _] (throw (Exception. "Test error")))})
          result (llm/execute-tool error-tool {} nil)]
      (is (nil? (:result result)))
      (is (= "Test error" (:error result))))))

;; ═══════════════════════════════════════════════════════════════
;; TOOL REGISTRY TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest tool-registry-test
  (testing "create registry with tools"
    (let [tool1 (llm/make-tool {:name "add" :description "Add" :parameters {} :fn (fn [_ _] 1)})
          tool2 (llm/make-tool {:name "sub" :description "Sub" :parameters {} :fn (fn [_ _] 2)})
          registry (llm/->ToolRegistry {"add" tool1 "sub" tool2})]
      (is (= #{"add" "sub"} (set (llm/registry-tool-names registry))))))

  (testing "execute tool from registry"
    (let [calc-tool (llm/make-tool {:name "add" :description "Add" :parameters {}
                                    :fn (fn [params _] (+ (:a params) (:b params)))})
          registry (llm/->ToolRegistry {"add" calc-tool})
          result (llm/registry-execute-tool registry "add" {:a 10 :b 20} nil)]
      (is (= 30 (:result result)))))

  (testing "handle unknown tool in registry"
    (let [registry (llm/->ToolRegistry {})
          result (llm/registry-execute-tool registry "unknown" {} nil)]
      (is (nil? (:result result)))
      (is (= "Unknown tool: unknown" (:error result))))))

;; ═══════════════════════════════════════════════════════════════
;; TOOL RESULT TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest tool-result-formatting-test
  (testing "format successful tool result for LLM"
    (let [tool-result (llm/->ToolResult "call-123" 42 nil)
          msg (llm/format-tool-result-for-llm tool-result)]
      (is (= :tool (:role msg)))
      (is (= 42 (:content msg)))
      (is (= {:tool-call-id "call-123"} (:metadata msg)))))

  (testing "format error tool result for LLM"
    (let [tool-result (llm/->ToolResult "call-456" nil "Something went wrong")
          msg (llm/format-tool-result-for-llm tool-result)]
      (is (= :tool (:role msg)))
      (is (= "Something went wrong" (:content msg))))))

;; ═══════════════════════════════════════════════════════════════
;; TOOL CALL RECORD TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest tool-call-record-test
  (testing "create ToolCall record"
    (let [tc (llm/->ToolCall "id-1" "get_weather" {:city "NYC"} "Get weather info")]
      (is (= "id-1" (:id tc)))
      (is (= "get_weather" (:name tc)))
      (is (= {:city "NYC"} (:arguments tc)))
      (is (= "Get weather info" (:description tc))))))

;; ═══════════════════════════════════════════════════════════════
;; CONVERSATION STORE TESTS
;; ═══════════════════════════════════════════════════════════════

(deftest datalevin-conversation-store-test
  (testing "create conversation store"
    (let [store (llm/->DatalevinConversationStore th/*test-conn*)]
      (is (some? store))))

  (testing "append and load conversation"
    (let [store (llm/->DatalevinConversationStore th/*test-conn*)
          ;; Create session and get the actual session ID
          session-id (software-builder.model/create-session th/*test-conn* "/tmp/test")]
      ;; Append messages
      (llm/append-message store session-id (llm/make-message :user "Hello"))
      (llm/append-message store session-id (llm/make-message :assistant "Hi there"))
      ;; Load conversation
      (let [loaded (llm/load-conversation store session-id)]
        (is (= 2 (count loaded)))
        (is (= :user (:role (first loaded))))
        (is (= :assistant (:role (second loaded)))))))

  (testing "save conversation batch"
    (let [store (llm/->DatalevinConversationStore th/*test-conn*)
          ;; Create session and get the actual session ID
          session-id (software-builder.model/create-session th/*test-conn* "/tmp/test2")
          messages [(llm/make-message :user "Q1")
                    (llm/make-message :assistant "A1")
                    (llm/make-message :user "Q2")]]
      ;; Save all messages
      (llm/save-conversation store session-id messages)
      ;; Verify
      (let [loaded (llm/load-conversation store session-id)]
        (is (= 3 (count loaded)))))))
