(ns common-server.core
  (:require [session-lib.core :as ssn]
            [mongo-lib.core :as mon]
            [language-lib.core :as lang]
            [utils-lib.core :as utils]
            [dao-lib.core :as dao]
            [pdflatex-lib.core :as tex]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]
            [ajax-lib.http.request-method :as rm]
            [clojure.set :as cset]
            [clojure.string :as cstring]
            [common-server.user.entity :as usere]
            [common-server.role.entity :as rolee]
            [common-server.language.entity :as languagee]
            [common-middle.request-urls :as rurls]
            [common-middle.ws-request-actions :as wsra]
            [common-middle.collection-names :refer [user-cname
                                                    role-cname
                                                    chat-cname
                                                    reset-password-cname
                                                    session-cname
                                                    long-session-cname
                                                    preferences-cname]]
            [common-middle.functionalities :as fns]
            [common-middle.functionalities-by-url :as cmfbu]
            [common-middle.role-names :refer [chat-rname]]
            [ajax-lib.http.response-header :as rsh]
            [mail-lib.core :as mail])
  (:import [java.io FileInputStream
                    File]))

(def email-address
     (atom "markovic.vladimir86.no.reply@gmail.com"))

(def email-password
     (atom "secret"))

(def reset-password-mail-template-path
     (atom "resources/mails/reset_password_template.html"))

(def entities-map
     (atom {:user {:reports usere/reports}
            :role {:reports rolee/reports}
            :language {:reports languagee/reports}}))

(def set-specific-preferences-a-fn
     (atom nil))

(defn print-request
  "Prints request map"
  [request]
  (let [ws-content-length (get-in
                            request
                            [:websocket
                             :websocket-message-length])
        ws-content-length (if ws-content-length
                            ws-content-length
                            (when-let [websocket-message (get-in
                                                           request
                                                           [:websocket
                                                            :websocket-message])]
                              (count
                                websocket-message))
                           )
        content-length (:content-length request)
        content-length (if (and content-length
                                (string?
                                  content-length))
                         (read-string
                           content-length)
                         (when-let [body (:body
                                           request)]
                           (count
                             body))
                        )
        result (atom nil)]
    (when (or (and ws-content-length
                   (< ws-content-length
                      300))
              (and content-length
                   (< content-length
                      300))
              (and (nil?
                     ws-content-length)
                   (nil?
                     content-length))
           )
      (reset!
        result
        request))
    (when (and ws-content-length
               (<= 300
                   ws-content-length))
      (reset!
        result
        (update-in
          request
          [:websocket]
          dissoc
          :websocket-message))
     )
    (when (and content-length
               (<= 300
                   content-length))
      (reset!
        result
        (dissoc
          request
          :body))
     )
   (println
     (str
       "\n"
       @result))
   @result))

(defn not-found
  "If request is not found"
  []
  {:status (stc/not-found)
   :headers {(eh/content-type) (mt/text-clojurescript)}
   :body {:status "error"
          :error-message "404 not found"}})

(defn get-allowed-actions
  "Get allowed actions for logged in user"
  [request]
  (let [cookie-string (:cookie request)
        long-session-uuid (ssn/get-cookie
                            cookie-string
                            :long-session)
        long-session-obj (when long-session-uuid
                           (mon/mongodb-find-one
                             long-session-cname
                             {:uuid long-session-uuid}))
        session-uuid (ssn/get-cookie
                       cookie-string
                       :session)
        session-obj (when session-uuid
                      (mon/mongodb-find-one
                        session-cname
                        {:uuid session-uuid}))
        result (atom nil)]
    (when long-session-obj
      (let [user-id (:user-id long-session-obj)]
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
            (reset!
              result
              @allowed-functionalities))
         ))
     )
    (when session-obj
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
            (reset!
              result
              @allowed-functionalities))
         ))
     )
    @result))

(def get-allowed-actions-memo
     (memoize
       get-allowed-actions))

(defn check-authorization
  "Check if request is authorized to be executed"
  [request]
  (let [authorized-actions (get-allowed-actions-memo
                             request)
        request-uri (:request-uri request)
        functionalities (cmfbu/get-functionalities-by-url
                          request-uri)
        functionalities-set (if (string?
                                  functionalities)
                              #{functionalities}
                              functionalities)]
    (if (empty?
          functionalities-set)
      true
      (let [intersection-set (cset/intersection
                               functionalities-set
                               authorized-actions)]
        (if (empty?
              intersection-set)
          false
          (let [request-body (:body request)
                request-body-keys-set (into
                                        #{}
                                        request-body)
                is-entity-action (< 2
                                    (count
                                      (cset/intersection
                                        request-body-keys-set
                                        #{:entity-type
                                          :entity-filter
                                          :_id}))
                                  )]
            (if is-entity-action
              (not
                (empty?
                  (cset/select
                    (fn [el]
                      (cstring/index-of
                        el
                        (:entity-type request-body))
                     )
                    intersection-set))
               )
              true))
         ))
     ))
 )

(defmulti routing-fn
  (fn [request]
    (if (= (:request-method request)
           rm/OPTIONS)
      [rm/OPTIONS
       "*"
       :logged-in-or-out
       :authorized]
      (let [is-logged-in (ssn/am-i-logged-in-fn
                           request)]
        (if is-logged-in
          (let [is-authorized (check-authorization
                                request)]
            (if is-authorized
              [(:request-method request)
               (:request-uri request)
               :logged-in
               :authorized]
              ["*"
               "*"
               :logged-in
               :not-authorized]))
          [(:request-method request)
           (:request-uri request)
           :logged-out
           :authorized]))
     ))
 )

(defmethod routing-fn
  [rm/POST
   rurls/am-i-logged-in-url
   :logged-in
   :authorized]
  [request]
  (ssn/am-i-logged-in
    request))

(defmethod routing-fn
  [rm/POST
   rurls/am-i-logged-in-url
   :logged-out
   :authorized]
  [request]
  (ssn/am-i-logged-in
    request))

(defmethod routing-fn
  [rm/POST
   rurls/get-entities-url
   :logged-in
   :authorized]
  [request]
  (dao/get-entities
    request))

(defmethod routing-fn
  [rm/POST
   rurls/get-entity-url
   :logged-in
   :authorized]
  [request]
  (dao/get-entity
    request))

(defmethod routing-fn
  [rm/POST
   rurls/update-entity-url
   :logged-in
   :authorized]
  [request]
  (dao/update-entity
    request))

(defmethod routing-fn
  [rm/POST
   rurls/insert-entity-url
   :logged-in
   :authorized]
  [request]
  (dao/insert-entity
    request))

(defmethod routing-fn
  [rm/POST
   rurls/logout-url
   :logged-in
   :authorized]
  [request]
  (ssn/logout
    request))

(defmethod routing-fn
  [rm/POST
   rurls/get-labels-url
   :logged-in
   :authorized]
  [request]
  (lang/get-labels
    request))

(defmethod routing-fn
  [rm/POST
   rurls/get-labels-url
   :logged-out
   :authorized]
  [request]
  (lang/get-labels
    request))

(defmethod routing-fn
  [rm/POST
   rurls/set-language-url
   :logged-in
   :authorized]
  [request]
  (lang/set-language
    request))

(defmethod routing-fn
  [rm/POST
   rurls/get-allowed-actions-url
   :logged-in
   :authorized]
  [request]
  "Get allowed actions for logged in user response"
  (if-let [allowed-actions (get-allowed-actions-memo
                             request)]
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-clojurescript)}
     :body {:status "success"
            :data allowed-actions}}
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-clojurescript)}
     :body {:status "error"}}))

(defmethod routing-fn
  [rm/POST
   rurls/get-chat-users-url
   :logged-in
   :authorized]
  [request]
  "Get chat users"
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
     :headers {(eh/content-type) (mt/text-clojurescript)}
     :body {:status "success"
            :data @chat-users}}))

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
        websocket-socket (:websocket-socket websocket-output-fn-el)
        websocket-output-fn (:websocket-output-fn websocket-output-fn-el)]
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

(defmethod routing-fn
  [rm/ws-GET
   rurls/chat-url
   :logged-in
   :authorized]
  [request]
  "Connect to websocket"
  (try
    (let [websocket (:websocket request)
          {websocket-message :websocket-message
           websocket-socket :websocket-socket
           websocket-output-fn :websocket-output-fn} websocket
          request-body (when (and (string?
                                    websocket-message)
                                  (not
                                    (cstring/blank?
                                      websocket-message))
                              )
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
          {:status "close"}
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
              {:action wsra/receive-chat-message-action
               :message message})
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
            {:action wsra/receive-chat-history-action
             :message chat-history})
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
              {:action wsra/receive-audio-chunk-action
               :sender sender-username
               :audio-chunk audio-chunk}))
         ))
      (when (= action
               wsra/make-a-call-action)
        (let [{receiver-username :receiver
               sender-username :sender} request-body
              receiver-websocket-output-fn (get-websocket-output-fn
                                             receiver-username)]
          (when (nil?
                  receiver-websocket-output-fn)
            (websocket-output-fn
              {:action wsra/unavailable-action
               :sender receiver-username}))
          (when (fn?
                  receiver-websocket-output-fn)
            (receiver-websocket-output-fn
              {:action wsra/receive-call-action
               :sender sender-username}))
         ))
      (when (= action
               wsra/call-accepted-action)
        (let [{receiver-username :receiver
               sender-username :sender} request-body
              receiver-websocket-output-fn (get-websocket-output-fn
                                             receiver-username)]
          (when (fn?
                  receiver-websocket-output-fn)
            (receiver-websocket-output-fn
              {:action wsra/call-accepted-action
               :sender sender-username}))
         ))
      (when (= action
               wsra/hang-up-call-action)
        (let [{receiver-username :receiver
               sender-username :sender} request-body
              receiver-websocket-output-fn (get-websocket-output-fn
                                             receiver-username)]
          (when (fn?
                  receiver-websocket-output-fn)
            (receiver-websocket-output-fn
              {:action wsra/hang-up-call-action
               :sender sender-username}))
         ))
      (when (= action
               wsra/unavailable-action)
        (let [{receiver-username :receiver
               sender-username :sender} request-body
              receiver-websocket-output-fn (get-websocket-output-fn
                                             receiver-username)]
          (when (fn?
                  receiver-websocket-output-fn)
            (receiver-websocket-output-fn
              {:action wsra/unavailable-action
               :sender sender-username}))
         ))
     )
    (catch Exception e
      (println
        (.getMessage
          e))
     )))

(defmethod routing-fn
  [rm/DELETE
   rurls/delete-entity-url
   :logged-in
   :authorized]
  [request]
  (dao/delete-entity
    request))

(defn get-report
  "Generates pdf report and returns it's bytes in response body
   
   /reports?report=table&entity=user&page=-1&language=english
   /reports?report=table&entity=user&page=1&language=english
   
   /reports?report=single&entity=user&id=0123456789&language=english
   /reports?report=single&entity=user&id=0123456789&language=english"
  [request]
  (let [{{report-type :report
          entity-type :entity
          page-number :page
          entity-id :id
          selected-language :language} :request-get-params
         {base64-png :base64-png} :body} request
        response-a (atom
                     {:status (stc/ok)
                      :headers {(eh/content-type) (mt/application-pdf)}})]
    (when (= report-type
             "table")
      (let [template-name (if (and selected-language
                                   (string?
                                     selected-language)
                                   (= selected-language
                                      "serbian"))
                            "table_template_sr.tex"
                            "table_template.tex")
            template (tex/read-template
                       template-name)
            page-number (try
                          (read-string
                            page-number)
                          (catch Exception e
                            (println
                              (.getMessage
                                e))
                            -1))
            entity-config-fn (get-in
                               @entities-map
                               [(keyword
                                  entity-type)
                                :reports])
            reports-config (when (and entity-config-fn
                                      (fn?
                                        entity-config-fn))
                             (entity-config-fn
                               request
                               selected-language))
            page-number-header (if (< -1
                                      page-number)
                                 (str
                                   (lang/get-label
                                     74
                                     selected-language)
                                   (inc
                                     page-number))
                                 (lang/get-label
                                   73
                                   selected-language))
            template (tex/replace-variable
                       template
                       "TABLE_PAGE_NUMBER_GOES_HERE"
                       page-number-header)
            table-of-label (lang/get-label
                             72
                             selected-language)
            entity-label (or (:entity-label reports-config)
                             entity-type)
            template (tex/replace-variable
                       template
                       "TABLE_HEADING_ENTITY_NAME_GOES_HERE"
                       (str
                         table-of-label
                         entity-label))
            request-body {:pagination (< -1
                                         page-number)
                          :current-page page-number
                          :rows (or (:rows reports-config)
                                    10)
                          :entity-type entity-type
                          :entity-filter {}
                          :projection (:projection reports-config)
                          :projection-include true
                          :qsort (:qsort reports-config)
                          :collation nil}
            table-data (dao/get-entities
                         {:body request-body})
            replacing-content (if (= (:card-columns reports-config)
                                     0)
                                (tex/generate-latex-table
                                  (get-in
                                    table-data
                                    [:body
                                     :data])
                                  reports-config
                                  selected-language)
                                (tex/generate-latex-card-table
                                  (get-in
                                    table-data
                                    [:body
                                     :data])
                                  reports-config
                                  selected-language))
            prepared-template (tex/replace-variable
                                template
                                "TABLE_GOES_HERE"
                                replacing-content)
            pdf-report-bytes (tex/execute-pdflatex
                               prepared-template)]
        (swap!
          response-a
          assoc
          :body pdf-report-bytes))
     )
    (when (= report-type
             "single")
      (let [template-name (if (and selected-language
                                   (string?
                                     selected-language)
                                   (= selected-language
                                      "serbian"))
                            "table_template_sr.tex"
                            "table_template.tex")
            template (tex/read-template
                       template-name)
            entity-config-fn (get-in
                               @entities-map
                               [(keyword
                                  entity-type)
                                :reports])
            reports-config (when (and entity-config-fn
                                      (fn?
                                        entity-config-fn))
                             (entity-config-fn
                               request
                               selected-language))
            template (tex/replace-variable
                       template
                       "TABLE_PAGE_NUMBER_GOES_HERE"
                       "")
            table-of-label (lang/get-label
                             75
                             selected-language)
            entity-label (or (:entity-label reports-config)
                             entity-type)
            template (tex/replace-variable
                       template
                       "TABLE_HEADING_ENTITY_NAME_GOES_HERE"
                       (str
                         table-of-label
                         entity-label))
            request-body {:entity-type entity-type
                          :entity-filter {:_id entity-id}
                          :entity-projection (:projection reports-config)
                          :projection-include true}
            table-data (dao/get-entity
                         {:body request-body})
            replacing-content (tex/generate-latex-single-entity
                                (get-in
                                  table-data
                                  [:body
                                   :data])
                                reports-config
                                selected-language)
            prepared-template (tex/replace-variable
                                template
                                "TABLE_GOES_HERE"
                                replacing-content)
            pdf-report-bytes (tex/execute-pdflatex
                               prepared-template)]
        (swap!
          response-a
          assoc
          :body pdf-report-bytes))
     )
    (when (= report-type
             "chart")
      (let [template-name (if (and selected-language
                                   (string?
                                     selected-language)
                                   (= selected-language
                                      "serbian"))
                            "table_template_sr.tex"
                            "table_template.tex")
            template (tex/read-template
                       template-name)
            template (tex/replace-variable
                       template
                       "TABLE_PAGE_NUMBER_GOES_HERE"
                       "")
            table-of-label (lang/get-label
                             1027
                             selected-language)
            template (tex/replace-variable
                       template
                       "TABLE_HEADING_ENTITY_NAME_GOES_HERE"
                       table-of-label)
            [replacing-content
             images-name-vector] (tex/generate-latex-image
                                   base64-png
                                   selected-language)
            prepared-template (tex/replace-variable
                                template
                                "TABLE_GOES_HERE"
                                replacing-content)
            pdf-report-bytes (tex/execute-pdflatex
                               prepared-template)]
        (swap!
          response-a
          assoc
          :body pdf-report-bytes)
        (tex/remove-temporary-images
          images-name-vector))
     )
    (when (nil?
            (:body @response-a))
      (reset!
        response-a
        (not-found))
     )
    @response-a))

(defmethod routing-fn
  [rm/GET
   rurls/reports-url
   :logged-in
   :authorized]
  [request]
  (get-report
    request))

(defmethod routing-fn
  [rm/POST
   rurls/reports-url
   :logged-in
   :authorized]
  [request]
  (get-report
    request))

(defmethod routing-fn
  [rm/POST
   rurls/read-preferences-url
   :logged-in
   :authorized]
  [request]
  "Read preferences from database and send them back to front end"
  (let [preferences (ssn/get-preferences
                      request)]
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-clojurescript)}
     :body {:status "success"
            :preferences preferences}}))

(defmethod routing-fn
  [rm/POST
   rurls/save-preferences-url
   :logged-in
   :authorized]
  [request]
  "Saves preferences into database"
  (try
    (let [preferences (get-in
                        request
                        [:body
                         :preferences])
          session-obj (ssn/get-session-obj
                        request)
          db-preferences (ssn/get-preferences
                           request)]
      (if db-preferences
        (mon/mongodb-update-one
          preferences-cname
          {:user-id (:user-id db-preferences)}
          preferences)
        (mon/mongodb-insert-one
          preferences-cname
          (assoc
            preferences
            :user-id (:user-id session-obj))
         ))
      {:status (stc/ok)
       :headers {(eh/content-type) (mt/text-clojurescript)}
       :body {:status "success"}})
    (catch Exception e
      (println
        (.getMessage
          e))
      {:status (stc/internal-server-error)
       :headers {(eh/content-type) (mt/text-clojurescript)}
       :body {:status "error"}}))
 )

(def response-to-options
     {:status (stc/ok)
      :headers {(eh/content-type) (mt/text-clojurescript)}
      :body {:status "success"}})

(defmethod routing-fn
  [rm/OPTIONS
   "*"
   :logged-in-or-out
   :authorized]
  [request]
  "If request is for OPTIONS"
  response-to-options)

(defmethod routing-fn
  [rm/POST
   rurls/login-url
   :logged-out
   :authorized]
  [request]
  (ssn/login-authentication
    request))

(def sign-up-roles
  (atom []))

(defn read-sign-up-role-ids
  "Reads role ids for role names passed in vector parameter"
  [role-names-vector]
  (when (and role-names-vector
             (vector?
               role-names-vector))
    (doseq [role-name role-names-vector]
      (let [{role-id :_id} (mon/mongodb-find-one
                            role-cname
                            {:role-name role-name})]
        (swap!
          sign-up-roles
          conj
          role-id))
     ))
 )

(defmethod routing-fn
  [rm/POST
   rurls/sign-up-url
   :logged-out
   :authorized]
  [request]
  "Sign up new user with given data"
  (try
    (let [request-body (:body
                         request)
          {entity-type :entity-type
           entity :entity} request-body
          entity (assoc
                   entity
                   :roles
                   @sign-up-roles)]
      (mon/mongodb-insert-one
        entity-type
        entity)
      (let [{_id :_id} (mon/mongodb-find-one
                         entity-type
                         entity)]
        (mon/mongodb-insert-one
          "preferences"
          {:user-id _id
           :language "english"
           :language-name "English"}))
      {:status (stc/ok)
       :headers {(eh/content-type) (mt/text-clojurescript)}
       :body {:status "success"}})
    (catch Exception ex
      (println (.getMessage ex))
      {:status (stc/internal-server-error)
       :headers {(eh/content-type) (mt/text-clojurescript)}
       :body {:status "error"
              :message (.getMessage ex)}})
   ))

(defn generate-reset-password-code
  "Generates reset password code"
  [email]
  (when (and email
             (string?
               email))
    (let [reset-password-obj (mon/mongodb-find-one
                               reset-password-cname
                               {:email email})]
      (if reset-password-obj
        (let [uuid (.toString
                     (java.util.UUID/randomUUID))]
          (mon/mongodb-update-one
            reset-password-cname
            {:email email}
            {:email email
             :uuid uuid
             :created-at (java.util.Date.)})
          uuid)
        (let [uuid (.toString
                     (java.util.UUID/randomUUID))]
          (mon/mongodb-insert-one
            reset-password-cname
            {:email email
             :uuid uuid
             :created-at (java.util.Date.)})
          uuid))
     ))
 )

(defmethod routing-fn
  [rm/POST
   rurls/forgot-password-url
   :logged-out
   :authorized]
  [request]
  "Checks if user with sent email exists, and if it does sends reset password code to it"
  (let [{{email :email} :body} request
        email (or email
                  "")
        selected-language (ssn/get-accept-language
                            request)
        user-db-obj (mon/mongodb-find-one
                      user-cname
                      {:email email})]
    (if user-db-obj
      (let [from @email-address
            password @email-password
            to email
            subject (lang/get-label
                      81
                      selected-language)
            from-contact-name (lang/get-label
                                62
                                selected-language)
            content (try
                      (let [template-path @reset-password-mail-template-path
                            template-is (FileInputStream.
                                          (File.
                                            template-path))
                            available-bytes (.available
                                              template-is)
                            template-byte-array (byte-array
                                                  available-bytes)
                            read-is (.read
                                      template-is
                                      template-byte-array)]
                        (.close
                          template-is)
                        (String.
                          template-byte-array
                          "UTF-8"))
                      (catch Exception e
                        (println
                          (.getMessage
                            e))
                        ""))
            content (cstring/replace
                      content
                      "EMAIL_TITLE"
                      (lang/get-label
                        81
                        selected-language))
            content (cstring/replace
                      content
                      "PARAGRAPH_CONTENT"
                      (lang/get-label
                        82
                        selected-language))
            reset-password-code (generate-reset-password-code
                                  email)
            content (cstring/replace
                      content
                      "RESET_PASSWORD_CODE"
                      reset-password-code)]
        (mail/send-email
          from
          password
          to
          subject
          content
          true
          from-contact-name)
        {:status (stc/ok)
         :headers {(eh/content-type) (mt/text-clojurescript)}
         :body {:status "success"}})
      (not-found))
   ))

(defmethod routing-fn
  [rm/POST
   rurls/reset-password-code-url
   :logged-out
   :authorized]
  [request]
  "Check if reset password code exists"
  (let [{{code :uuid} :body} request
        code (or code
                 "")
        reset-password-db-obj (mon/mongodb-find-one
                                reset-password-cname
                                {:uuid code})]
    (if reset-password-db-obj
      (let [{email :email
             db-uuid :uuid} reset-password-db-obj]
        {:status (stc/ok)
         :headers {(eh/content-type) (mt/text-clojurescript)}
         :body {:status "success"
                :email email
                :uuid db-uuid}})
      (not-found))
   ))

(defmethod routing-fn
  [rm/POST
   rurls/reset-password-final-url
   :logged-out
   :authorized]
  [request]
  "Checks if reset password code exists and if it does changes password"
  (let [{{code :uuid
          new-password :new-password} :body} request
        code (or code
                 "")
        new-password (or new-password
                         "")
        reset-password-db-obj (mon/mongodb-find-one
                                reset-password-cname
                                {:uuid code})]
    (if reset-password-db-obj
      (let [{email :email} reset-password-db-obj]
        (try
          (mon/mongodb-update-one
            user-cname
            {:email email}
            {:password new-password})
          (mon/mongodb-delete-one
            reset-password-cname
            {:email email})
          {:status (stc/ok)
           :headers {(eh/content-type) (mt/text-clojurescript)}
           :body {:status "success"}}
          (catch Exception e
            (println
              (.getMessage
                e))
            {:status (stc/internal-server-error)
             :headers {(eh/content-type) (mt/text-clojurescript)}
             :body {:status "error"}}))
       )
      (not-found))
   ))

(defmethod routing-fn
  ["*"
   "*"
   :logged-in
   :not-authorized]
  [request]
  "If request is unauthorized"
  {:status (stc/unauthorized)
   :headers {(eh/content-type) (mt/text-clojurescript)}
   :body {:status "success"}})

(defmethod routing-fn
  :default
  [request]
  "Default response is nil"
  nil)

(defmulti update-session
  (fn [request]
    (let [is-logged-in (ssn/am-i-logged-in-fn
                         request)
          request-uri (:request-uri request)]
      (if (and is-logged-in
               (not= request-uri
                     rurls/logout-url))
        :update
        :no-update))
   ))

(defmethod update-session
  :update
  [request]
  (let [response (routing-fn
                   request)]
    (ssn/set-session-cookies
      request
      response))
 )

(defmethod update-session
  :no-update
  [request]
  (routing-fn
    request))

(defn routing
  "Routing function"
  [request]
  (print-request
    request)
  (let [response (update-session
                   request)]
    (if (nil?
          response)
      (not-found)
      response))
 )

