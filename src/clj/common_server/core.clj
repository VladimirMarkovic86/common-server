(ns common-server.core
  (:require [session-lib.core :as ssn]
            [mongo-lib.core :as mon]
            [language-lib.core :as lang]
            [utils-lib.core :as utils :refer [parse-body]]
            [dao-lib.core :as dao]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]
            [ajax-lib.http.request-method :as rm]
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
  [request]
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
  [request]
  (try
    (let [websocket (:websocket request)
          {websocket-message :websocket-message
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

(defn sign-up
  "Sign up new user with given data"
  [request]
  (try
    (let [request-body (parse-body
                         request)]
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
 )

(defn not-found
  "If request is not found"
  []
  {:status (stc/not-found)
   :headers {(eh/content-type) (mt/text-plain)}
   :body (str {:status "error"
               :error-message "404 not found"})})

(defn not-authorized
  "If reqeust is unauthorized"
  []
  {:status (stc/unauthorized)
   :headers {(eh/content-type) (mt/text-plain)}
   :body (str {:status "success"})})

(defn response-to-options
  "If request is for OPTIONS"
  [request]
  {:status (stc/ok)
   :headers {(eh/content-type) (mt/text-plain)}
   :body (str {:status "success"})})

(def logged-in-routing-set
  (atom
    #{{:method rm/POST
       :uri rurls/am-i-logged-in-url
       :action ssn/am-i-logged-in}
      {:method rm/POST
       :uri rurls/get-entities-url
       :authorization "-read"
       :entity true
       :action dao/get-entities}
      {:method rm/POST
       :uri rurls/get-entity-url
       :authorization "-read"
       :entity true
       :action dao/get-entity}
      {:method rm/POST
       :uri rurls/update-entity-url
       :authorization "-update"
       :entity true
       :action dao/update-entity}
      {:method rm/POST
       :uri rurls/insert-entity-url
       :authorization "-create"
       :entity true
       :action dao/insert-entity}
      {:method rm/POST
       :uri rurls/logout-url
       :action ssn/logout}
      {:method rm/POST
       :uri rurls/get-labels-url
       :action lang/get-labels}
      {:method rm/POST
       :uri rurls/set-language-url
       :action lang/set-language}
      {:method rm/POST
       :uri rurls/get-allowed-actions-url
       :action get-allowed-actions-response}
      {:method rm/POST
       :uri rurls/get-chat-users-url
       :authorization fns/chat
       :action get-chat-users}
      {:method rm/ws-GET
       :uri rurls/chat-url
       :authorization fns/chat
       :action chat-ws}
      {:method rm/DELETE
       :uri rurls/delete-entity-url
       :authorization "-delete"
       :entity true
       :action dao/delete-entity}}))

(def logged-out-routing-set
  (atom
    #{{:method rm/OPTIONS
       :uri "*"
       :action response-to-options}
      {:method rm/POST
       :uri rurls/login-url
       :action ssn/login-authentication}
      {:method rm/POST
       :uri rurls/sign-up-url
       :action sign-up}
      {:method rm/POST
       :uri rurls/am-i-logged-in-url
       :action ssn/am-i-logged-in}
      {:method rm/POST
       :uri rurls/get-labels-url
       :action lang/get-labels}}))

(defn conj-new-routes
  "Adds new routes"
  [routing-set
   new-routes]
  (swap!
    routing-set
    (fn [value-a
         param]
      (apply
        conj
        value-a
        param))
    new-routes))

(defn add-new-routes
  "Adds routes particular for this project"
  [additional-logged-in-routing-set
   additional-logged-out-routing-set]
  (conj-new-routes
    logged-in-routing-set
    additional-logged-in-routing-set)
  (conj-new-routes
    logged-out-routing-set
    additional-logged-out-routing-set))

(defn routing-fn
  "Routing function that selects right route"
  [request]
  (let [request-method (:request-method request)
        request-uri (:request-uri request)
        is-logged-in (ssn/am-i-logged-in-fn
                       request)
        routing-set (if is-logged-in
                      @logged-in-routing-set
                      @logged-out-routing-set)
        request-action (clojure.set/select
                         (fn [{method :method
                               uri :uri}]
                           (or (and (= request-method
                                       method)
                                    (= request-uri
                                       uri))
                               (= request-method
                                  method
                                  rm/OPTIONS))
                          )
                         routing-set)
        requested-element (first
                            request-action)]
    (when-not (nil?
                requested-element)
      (let [authorized-actions (get-allowed-actions-memo
                                 request)
            requested-authorization (:authorization requested-element)
            is-entity (:entity requested-element)
            request-body (parse-body
                           request)
            entity-name (:entity-type request-body)
            requested-authorization (if is-entity
                                      (str
                                        entity-name
                                        requested-authorization)
                                      requested-authorization)]
        (if (or (nil?
                  requested-authorization)
                (contains?
                  authorized-actions
                  requested-authorization))
          (let [action-fn (:action requested-element)
                response (if (fn?
                               action-fn)
                           (action-fn
                             request)
                           action-fn)]
            (if is-logged-in
              (ssn/set-session-cookies
                request
                response)
              response))
          (not-authorized))
       ))
   ))

(defn routing
  "Routing function"
  [request]
  (utils/print-request
    request)
  (let [response (routing-fn
                   request)]
    (if (nil?
          response)
      (not-found)
      response))
 )

