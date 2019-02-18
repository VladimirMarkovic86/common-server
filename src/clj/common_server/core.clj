(ns common-server.core
  (:require [session-lib.core :as ssn]
            [mongo-lib.core :as mon]
            [language-lib.core :as lang]
            [utils-lib.core :refer [parse-body]]
            [dao-lib.core :as dao]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]
            [clojure.set :as cset]
            [clojure.string :as cstring]
            [common-middle.request-urls :as rurls]
            [common-middle.ws-request-actions :as wsra]
            [common-middle.collection-names :refer [user-cname
                                                    role-cname
                                                    chat-cname]]
            [common-middle.functionalities :as fns]
            [common-middle.role-names :refer [chat-rname]]
            [ajax-lib.http.response-header :as rsh]))

(defn get-allowed-actions
  "Get allowed actions for logged in user"
  [request]
  (let [cookie-string (:cookie request)
        session-uuid (ssn/get-cookie
                       cookie-string
                       :long-session)
        [session-uuid
         session-collection] (if-not session-uuid
                               [(ssn/get-cookie
                                  cookie-string
                                  :session)
                                "session"]
                               [session-uuid
                                "long-session"])]
    (when-let [session-uuid session-uuid]
      (when-let [session-obj (mon/mongodb-find-one
                               session-collection
                               {:uuid session-uuid})]
        (let [user-id (:user-id session-obj)]
          (when-let [user (mon/mongodb-find-by-id
                            user-cname
                            user-id)]
            (let [roles (:roles user)
                  allowed-functionalities (atom #{})]
              (doseq [role-id roles]
                (when-let [role (mon/mongodb-find-by-id
                                  role-cname
                                  role-id)]
                  (swap!
                    allowed-functionalities
                    (fn [atom-value
                         conj-coll]
                      (apply
                        conj
                        atom-value
                        conj-coll))
                    (:functionalities role))
                 ))
              @allowed-functionalities))
         ))
     ))
 )

(def get-allowed-actions-memo
     (memoize
       get-allowed-actions))

(defn action-allowed?
  "Check if action is allowed to user"
  [request
   request-body
   allow-action-routing]
  (let [allowed (atom false)
        execute-functionality (atom nil)
        allowed-functionalities (get-allowed-actions-memo
                                  request)
        allow-action-routing (if (and allow-action-routing
                                      (fn?
                                        allow-action-routing))
                               allow-action-routing
                               (fn [param]
                                 false))
        {request-uri :request-uri
         request-method :request-method} request]
    (cond
      (= request-method
         "ws GET")
        (cond
          (= request-uri
             rurls/chat-url)
            (reset!
              execute-functionality
              fns/chat)
          :else
            (reset!
              allowed
              (allow-action-routing
                request))
         )
      (= request-method
         "POST")
        (cond
          (= request-uri
             rurls/am-i-logged-in-url)
            (reset! allowed true)
          (= request-uri
             rurls/get-entities-url)
            (reset!
              execute-functionality
              (str
                (:entity-type request-body)
                "-read"))
          (= request-uri
             rurls/get-entity-url)
            (reset!
              execute-functionality
              (str
                (:entity-type request-body)
                "-read"))
          (= request-uri
             rurls/update-entity-url)
            (reset!
              execute-functionality
              (str
                (:entity-type request-body)
                "-update"))
          (= request-uri
             rurls/insert-entity-url)
            (reset!
              execute-functionality
              (str
                (:entity-type request-body)
                "-create"))
          (= request-uri
             rurls/logout-url)
            (reset! allowed true)
          (= request-uri
             rurls/get-labels-url)
            (reset! allowed true)
          (= request-uri
             rurls/set-language-url)
            (reset! allowed true)
          (= request-uri
             rurls/get-allowed-actions-url)
            (reset! allowed true)
          (= request-uri
             rurls/get-chat-users-url)
            (reset!
              execute-functionality
              fns/chat)
          :else
            (reset!
              allowed
              (allow-action-routing
                request))
         )
      (= request-method
         "DELETE")
        (cond
          (= request-uri
             rurls/delete-entity-url)
            (reset!
              execute-functionality
              (str
                (:entity-type request-body)
                "-delete"))
          :else
            (reset!
              allowed
              (allow-action-routing
                request))
         )
      :else
        (reset!
          allowed
          (allow-action-routing
            request))
     )
    (when (contains?
            allowed-functionalities
            @execute-functionality)
      (reset! allowed true))
    @allowed))

(def action-allowed?-memo
     (memoize
       action-allowed?))

(defn get-allowed-actions-response
  "Get allowed actions for logged in user response"
  [request]
  (if-let [allowed-actions (get-allowed-actions-memo
                             request)]
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str
             {:status "success"
              :data allowed-actions})}
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str {:status "error"})})
 )

(defn get-chat-users
  "Get chat users"
  []
  (let [all-users (mon/mongodb-find
                    user-cname)
        chat-role-doc (mon/mongodb-find-one
                        role-cname
                        {:role-name chat-rname})
        chat-users (atom [])]
    (doseq [{roles :roles
             username :username} all-users]
      (let [roles (into
                    #{}
                    roles)
            chat-role-string-id (.toString
                                  (:_id chat-role-doc))]
        (when (contains?
                roles
                chat-role-string-id)
          (swap!
            chat-users
            conj
            {:username username}))
       ))
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str {:status "success"
                 :data @chat-users})})
 )

(defn get-chat-history
  "Get chat history for particular users"
  [usernames]
  (let [db-object (mon/mongodb-find-one
                    chat-cname
                    {:usernames {:$all usernames}}
                    {:messages 1
                     :_id 0})]
    db-object))

(defn save-chat-message
  "Save chat message to database"
  [{usernames :usernames
    message :message}]
  (try
    (let [message (assoc
                    message
                    :sent-at
                    (java.util.Date.))
          mongo-result (mon/mongodb-update-one
                         chat-cname
                         {:usernames {:$all usernames}}
                         {:$addToSet {:messages message}})]
      (let [matched-count (.getMatchedCount
                            mongo-result)
            modified-count (.getModifiedCount
                             mongo-result)]
        (when (= modified-count
                 matched-count
                 0)
          (mon/mongodb-insert-one
            chat-cname
            {:usernames usernames
             :messages [message]})
         ))
     )
    (catch Exception ex
      (println (.getMessage ex))
     ))
 )

(def chat-websocket-set-a
     (atom
       (sorted-set-by
         (fn [{username1 :username}
              {username2 :username}]
              (compare
                username1
                username2))
        ))
 )

(defn remove-websocket-from-set
  "Removes websocket for particular username from set"
  [username]
  (let [new-chat-websocket-set-a (cset/select
                                   (fn [{username-set :username}]
                                     (not= username-set
                                           username))
                                   @chat-websocket-set-a)]
    (reset!
      chat-websocket-set-a
      new-chat-websocket-set-a))
 )

(defn get-websocket-output-fn
  "Checks if there is open websocket for particular username
   if there is websocket and it is open return websocket-output-fn
   if there is websocket and it's closed or there is no websocket
    for particular username return nil"
  [username]
  (let [websocket-output-fn-set (cset/select
                                  (fn [{username-set :username}]
                                    (= username-set
                                       username))
                                  @chat-websocket-set-a)
        websocket-output-fn-el (first
                                 websocket-output-fn-set)
        websocket-socket (:websocket-socket
                              websocket-output-fn-el)
        websocket-output-fn (:websocket-output-fn
                              websocket-output-fn-el)]
    (when (not
            (nil?
              websocket-socket))
      (when (.isClosed
              websocket-socket)
        (remove-websocket-from-set
          username))
      (when (and (not
                   (.isClosed
                     websocket-socket))
                 (fn?
                   websocket-output-fn))
        websocket-output-fn))
   ))

(defn chat-ws
  "Connect to websocket"
  [websocket]
  (try
    (let [{websocket-message :websocket-message
           websocket-socket :websocket-socket
           websocket-output-fn :websocket-output-fn} websocket
          request-body (when-not (cstring/blank?
                                   websocket-message)
                         (read-string
                           websocket-message))
          action (:action request-body)]
      (when (= action
               wsra/establish-connection-action)
        (remove-websocket-from-set
          (:username request-body))
        (swap!
          chat-websocket-set-a
          conj
          {:username (:username request-body)
           :websocket-socket websocket-socket
           :websocket-output-fn websocket-output-fn}))
      (when (= action
               wsra/close-connection-action)
        (remove-websocket-from-set
          (:username request-body))
        (websocket-output-fn
          (str
            {:status "close"})
          -120))
      (when (= action
               wsra/send-chat-message-action)
        (let [{usernames :usernames
               message :message} request-body
              sent-to (get
                        usernames
                        1)
              sent-to-websocket-output-fn (get-websocket-output-fn
                                            sent-to)]
          (when (fn?
                  sent-to-websocket-output-fn)
            (sent-to-websocket-output-fn
              (str
                {:action wsra/receive-chat-message-action
                 :message message}))
           )
          (save-chat-message
            {:usernames usernames
             :message message}))
       )
      (when (= action
               wsra/get-chat-history-action)
        (let [chat-history (get-chat-history
                             (:usernames request-body))]
          (websocket-output-fn
            (str
              {:action wsra/receive-chat-history-action
               :message chat-history}))
         ))
      (when (= action
               wsra/send-audio-chunk-action)
        (let [{receiver-username :receiver
               sender-username :sender
               audio-chunk :audio-chunk} request-body
              receiver-websocket-output-fn (get-websocket-output-fn
                                             receiver-username)]
          (when (fn?
                  receiver-websocket-output-fn)
            (receiver-websocket-output-fn
              (str
                {:action wsra/receive-audio-chunk-action
                 :sender sender-username
                 :audio-chunk audio-chunk}))
           ))
       ))
    (catch Exception e
      (println e))
   ))

(defn not-found
  "If response-routing is nil return 404 not found"
  [response-routing
   request]
  (if (and response-routing
           (fn?
             response-routing))
    (response-routing
      request)
    {:status (stc/not-found)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str {:status "error"
                 :error-message "404 not found"})})
 )

(defn routing
  "Routing function"
  [request
   & [response-routing
      allow-action-routing
      response-routing-not-logged-in]]
  (if-let [request-ws (:websocket request)]
    (if (< (get-in
             request
             [:websocket
              :websocket-message-length])
           300)
      (println
        (str
          "\n"
          request))
      (println
        (str
          "\n"
          (update-in
            request
            [:websocket]
            dissoc
            :websocket-message))
       ))
    (if-let [body (:body request)]
      (if (< (read-string
               (:content-length request))
             300)
        (println
          (str
            "\n"
            request))
        (println
          (str
            "\n"
            (dissoc
              request
              :body))
         ))
      (println
        (str
          "\n"
          request))
     ))
  (let [{request-uri :request-uri
         request-method :request-method} request]
    (if (ssn/am-i-logged-in-fn request)
      (if (action-allowed?-memo
            request
            (parse-body request)
            allow-action-routing)
        (let [[cookie-value
               visible-cookie-value] (ssn/refresh-session
                                       request)
              response
               (cond
                 (= request-method
                    "ws GET")
                   (cond
                     (= request-uri
                        rurls/chat-url)
                       (chat-ws
                         (:websocket request))
                     :else
                       (not-found
                         response-routing
                         request))
                 (= request-method
                    "POST")
                   (cond
                     (= request-uri
                        rurls/am-i-logged-in-url)
                       (ssn/am-i-logged-in request)
                     (= request-uri
                        rurls/get-entities-url)
                       (dao/get-entities (parse-body request))
                     (= request-uri
                        rurls/get-entity-url)
                       (dao/get-entity (parse-body request))
                     (= request-uri
                        rurls/update-entity-url)
                       (dao/update-entity (parse-body request))
                     (= request-uri
                        rurls/insert-entity-url)
                       (dao/insert-entity (parse-body request))
                     (= request-uri
                        rurls/logout-url)
                       (ssn/logout request)
                     (= request-uri
                        rurls/get-labels-url)
                       (lang/get-labels request)
                     (= request-uri
                        rurls/set-language-url)
                       (lang/set-language
                         request
                         (parse-body request))
                     (= request-uri
                        rurls/get-allowed-actions-url)
                       (get-allowed-actions-response
                         request)
                     (= request-uri
                        rurls/get-chat-users-url)
                       (get-chat-users)
                     :else
                       (not-found
                         response-routing
                         request))
                 (= request-method
                    "DELETE")
                   (cond
                     (= request-uri
                        rurls/delete-entity-url)
                       (dao/delete-entity (parse-body request))
                     :else
                       (not-found
                         response-routing
                         request))
                 :else
                   (not-found
                     response-routing
                     request))]
          (if (contains?
                (:headers response)
                (rsh/set-cookie))
            response
            (update-in
              response
              [:headers]
              assoc
              (rsh/set-cookie)
              [cookie-value
               visible-cookie-value]))
         )
        {:status (stc/unauthorized)
         :headers {(eh/content-type) (mt/text-plain)}
         :body (str {:status "success"})})
      (cond
        (= request-method
           "OPTIONS")
          {:status (stc/ok)
           :headers {(eh/content-type) (mt/text-plain)}
           :body (str {:status "success"})}
        (= request-method
           "POST")
          (cond
            (= request-uri
               rurls/login-url)
              (ssn/login-authentication
                (parse-body
                  request)
                (:user-agent request)
                (:accept-language request))
            (= request-uri
               rurls/sign-up-url)
              (try
                (let [request-body (parse-body request)]
                  (mon/mongodb-insert-one
                    (:entity-type request-body)
                    (:entity request-body))
                  (let [{_id :_id} (mon/mongodb-find-one
                                     (:entity-type request-body)
                                     (:entity request-body))]
                    (mon/mongodb-insert-one
                      "preferences"
                      {:user-id _id
                       :language "english"
                       :language-name "English"}))
                  {:status (stc/ok)
                   :headers {(eh/content-type) (mt/text-plain)}
                   :body (str {:status "success"})})
                (catch Exception ex
                  (println (.getMessage ex))
                  {:status (stc/internal-server-error)
                   :headers {(eh/content-type) (mt/text-plain)}
                   :body (str
                           {:status "error"
                            :message (.getMessage ex)})}
                 ))
            (= request-uri
               rurls/am-i-logged-in-url)
              (ssn/am-i-logged-in request)
            (= request-uri
               rurls/get-labels-url)
              (lang/get-labels request)
            :else
              (not-found
                response-routing-not-logged-in
                request))
        :else
          (not-found
            response-routing-not-logged-in
            request))
     ))
 )

