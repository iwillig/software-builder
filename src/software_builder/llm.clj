(ns software-builder.llm
  "LLM integration abstractions using protocols and records.
   Provides a unified interface for different LLM providers
   with Hugging Face Inference as the primary implementation.
   Uses Aleph for async HTTP."
  (:require
   [clojure.string :as str]
   [jsonista.core :as json]
   [aleph.http :as http]
   [manifold.deferred :as mf]
   [datalevin.core :as d]
   [software-builder.model :as model])
  (:import
   (java.util Date)))

;; ═══════════════════════════════════════════════════════════════
;; MESSAGE RECORDS
;; ═══════════════════════════════════════════════════════════════

(defrecord Message
           [role content timestamp metadata])

(defrecord ToolCall
           [id name arguments description])

(defrecord ToolResult
           [tool-call-id result error])

;; ═══════════════════════════════════════════════════════════════
;; LLM PROTOCOLS
;; ═══════════════════════════════════════════════════════════════

(defprotocol LLMClient
  "Protocol for LLM client implementations."
  (complete [this messages options]
    "Send a chat completion request.
     Returns a deferred containing map with:
     - :content - Response text
     - :tool-calls - Vector of ToolCall records
     - :usage - Token usage stats
     - :finish-reason - Why generation stopped")
  (complete-stream [this messages options callback]
    "Send a streaming chat completion request.
     Calls callback for each chunk, returns deferred of final response.")
  (supported-models [this]
    "Return a list of supported model identifiers.")
  (validate-config [this]
    "Validate client configuration. Returns :ok or {:error reason}"))

(defprotocol ToolDefinition
  "Protocol for defining tools that LLMs can call."
  (tool-name [this]
    "Return the unique name of this tool.")
  (tool-description [this]
    "Return a description of what this tool does.")
  (tool-parameters-schema [this]
    "Return JSON schema for tool parameters.")
  (execute-tool [this parameters context]
    "Execute the tool with given parameters and context.
     Returns ToolResult record."))

;; ═══════════════════════════════════════════════════════════════
;; HUGGING FACE INFERENCE IMPLEMENTATION
;; ═══════════════════════════════════════════════════════════════

(defrecord HuggingFaceConfig
           [api-token provider model base-url timeout-ms])

(defrecord HuggingFaceClient
           [config]
  LLMClient
  (complete [_ messages options]
    (let [url (str (:base-url config) "/" (:provider config) "/v1/chat/completions")
          headers {"Authorization" (str "Bearer " (:api-token config))
                   "Content-Type" "application/json"}
          body {:model (:model config)
                :messages (map #(select-keys % [:role :content]) messages)
                :max_tokens (get options :max-tokens 1024)
                :temperature (get options :temperature 0.7)
                :stream false}]
      (mf/chain
       (http/post url
                  {:headers headers
                   :body (json/write-value-as-string body)
                   :timeout (:timeout-ms config 30000)})
       (fn [response]
         (if (= 200 (:status response))
           (let [data (json/read-value (slurp (:body response))
                                       json/keyword-keys-object-mapper)
                 choice (first (:choices data))
                 message (:message choice)]
             {:content (:content message)
              :role (:role message)
              :tool-calls (when (:tool_calls message)
                            (map #(->ToolCall
                                   (:id %)
                                   (get-in % [:function :name])
                                   (json/read-value (get-in % [:function :arguments])
                                                    json/keyword-keys-object-mapper)
                                   (get-in % [:function :description]))
                                 (:tool_calls message)))
              :usage (:usage data)
              :finish-reason (:finish_reason choice)
              :model (:model data)})
           {:error (str "HTTP " (:status response))
            :status (:status response)})))))

  (complete-stream [this messages options callback]
    ;; For now, delegate to non-streaming version
    ;; Full streaming with SSE would require additional handling
    (mf/chain
     (complete this messages options)
     (fn [response]
       (when (:content response)
         (callback {:type :content :data (:content response)}))
       (callback {:type :done})
       response)))

  (supported-models [_]
    ;; Models available on HF Inference (Hyperbolic provider)
    ;; Note: Use exact model IDs as required by the provider
    ["meta-llama/Meta-Llama-3.1-8B-Instruct"
     "meta-llama/Meta-Llama-3.1-70B-Instruct"
     "meta-llama/Llama-3.3-70B-Instruct"
     "meta-llama/Llama-3.2-3B-Instruct"
     "mistralai/Pixtral-12B-2409"
     "deepseek-ai/DeepSeek-V3"
     "deepseek-ai/DeepSeek-R1"
     "Qwen/Qwen2.5-Coder-32B-Instruct"
     "Qwen/Qwen2.5-72B-Instruct"
     "Qwen/QwQ-32B"])

  (validate-config [_]
    (cond
      (str/blank? (:api-token config))
      {:error "API token is required"}

      (str/blank? (:model config))
      {:error "Model is required"}

      :else
      :ok)))

;; ═══════════════════════════════════════════════════════════════
;; TOOL REGISTRY
;; ═══════════════════════════════════════════════════════════════

(defrecord ToolRegistry
           [tools])

(defn registry-tool-names
  "Get all tool names from registry."
  [registry]
  (keys (:tools registry)))

(defn registry-execute-tool
  "Execute a tool from the registry."
  [registry tool-name parameters context]
  (if-let [tool (get (:tools registry) tool-name)]
    (execute-tool tool parameters context)
    (->ToolResult nil nil (str "Unknown tool: " tool-name))))

;; ═══════════════════════════════════════════════════════════════
;; BUILDER FUNCTIONS
;; ═══════════════════════════════════════════════════════════════

(defn hugging-face-client
  "Create a HuggingFaceClient with configuration.

   Required options:
   - :api-token - HF API token
   - :model - Model identifier

   Optional options:
   - :provider - Provider name (default: hyperbolic)
   - :base-url - API base URL (default: https://router.huggingface.co)
   - :timeout-ms - Request timeout (default: 30000)"
  [options]
  (let [config (->HuggingFaceConfig
                (:api-token options)
                (get options :provider "hyperbolic")
                (:model options)
                (get options :base-url "https://router.huggingface.co")
                (get options :timeout-ms 30000))]
    (->HuggingFaceClient config)))

(defn make-message
  "Create a Message record.

   Roles: :system, :user, :assistant, :tool"
  ([role content]
   (->Message role content (Date.) nil))
  ([role content metadata]
   (->Message role content (Date.) metadata)))

(defn make-tool
  "Create a simple tool implementation from a function.

   Options:
   - :name - Tool name
   - :description - Tool description
   - :parameters - JSON schema for parameters
   - :fn - Function that takes [params context] and returns result"
  [options]
  (let [tool-name (:name options)
        tool-desc (:description options)
        params-schema (:parameters options)
        tool-fn (:fn options)]
    (reify ToolDefinition
      (tool-name [_] tool-name)
      (tool-description [_] tool-desc)
      (tool-parameters-schema [_] params-schema)
      (execute-tool [_ parameters context]
        (try
          (->ToolResult nil (tool-fn parameters context) nil)
          (catch Exception e
            (->ToolResult nil nil (.getMessage e))))))))

;; ═══════════════════════════════════════════════════════════════
;; CONVERSATION HELPERS
;; ═══════════════════════════════════════════════════════════════

(defn add-system-prompt
  "Add a system prompt to the beginning of messages."
  [messages prompt]
  (cons (make-message :system prompt) messages))

(defn add-user-message
  "Add a user message to the conversation."
  [messages content]
  (conj (vec messages) (make-message :user content)))

(defn add-assistant-message
  "Add an assistant message to the conversation."
  [messages content]
  (conj (vec messages) (make-message :assistant content)))

(defn format-tool-result-for-llm
  "Format a ToolResult for sending back to LLM."
  [tool-result]
  (make-message :tool
                (or (:result tool-result)
                    (:error tool-result)
                    "No result")
                {:tool-call-id (:tool-call-id tool-result)}))

;; ═══════════════════════════════════════════════════════════════
;; CONVERSATION MANAGEMENT
;; ═══════════════════════════════════════════════════════════════

(defprotocol ConversationStore
  "Protocol for storing and retrieving conversations."
  (load-conversation [this session-id]
    "Load messages for a session. Returns list of Message records.")
  (save-conversation [this session-id messages]
    "Save messages for a session.")
  (append-message [this session-id message]
    "Append a single message to the conversation."))

;; Datalevin-backed conversation store
(defrecord DatalevinConversationStore
           [db-conn]

  ConversationStore
  (load-conversation [_ session-id]
    (let [db (d/db db-conn)
          messages (model/get-session-messages db session-id)]
      (map #(->Message
             (:message/role %)
             (:message/content %)
             (:message/timestamp %)
             (dissoc % :message/role :message/content :message/timestamp))
           messages)))

  (save-conversation [_ session-id messages]
    (doseq [msg messages]
      (model/store-message db-conn
                           session-id
                           (:role msg)
                           (:content msg)
                           :model (get-in msg [:metadata :model]))))

  (append-message [_ session-id message]
    (model/store-message db-conn
                         session-id
                         (:role message)
                         (:content message)
                         :model (get-in message [:metadata :model]))))
