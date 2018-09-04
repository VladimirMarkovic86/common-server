(ns common-server.core
  (:require [session-lib.core :as ssn]
            [mongo-lib.core :as mon]
            [language-lib.core :as lang]
            [utils-lib.core :refer [parse-body]]
            [dao-lib.core :as dao]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]))

(defn get-allowed-actions
  ""
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
                            "user"
                            user-id)]
            (let [roles (:roles user)
                  allowed-functionalities (atom #{})]
              (doseq [role-id roles]
                (when-let [role (mon/mongodb-find-by-id
                                  "role"
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

(defn action-allowed?
  ""
  [request-start-line
   request
   request-body
   allow-action-routing]
  (let [allowed (atom false)
        execute-functionality (atom nil)
        allowed-functionalities (get-allowed-actions
                                  request)]
    (case request-start-line
      "POST /am-i-logged-in" (reset! allowed true)
      "POST /get-entities" (reset!
                             execute-functionality
                             (str
                               (:entity-type request-body)
                               "-read"))
      "POST /get-entity" (reset!
                           execute-functionality
                           (str
                             (:entity-type request-body)
                             "-read"))
      "POST /update-entity" (reset!
                              execute-functionality
                              (str
                                (:entity-type request-body)
                                "-update"))
      "POST /insert-entity" (reset!
                              execute-functionality
                              (str
                                (:entity-type request-body)
                                "-create"))
      "DELETE /delete-entity" (reset!
                                execute-functionality
                                (str
                                  (:entity-type request-body)
                                  "-delete"))
      "POST /logout" (reset! allowed true)
      "POST /get-labels" (reset! allowed true)
      "POST /set-language" (reset! allowed true)
      "POST /get-allowed-actions" (reset! allowed true)
      (reset!
        allowed
        allow-action-routing))
    (when (contains?
            allowed-functionalities
            @execute-functionality)
      (reset! allowed true))
    @allowed))

(defn get-allowed-actions-response
  ""
  [request]
  (if-let [allowed-actions (get-allowed-actions
                             request)]
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str
             {:status "success"
              :data allowed-actions})}
    {:status (stc/ok)
     :headers {(eh/content-type) (mt/text-plain)}
     :body (str {:status "error"})}))

(defn routing
  "Routing function"
  [request-start-line
   request
   & [response-routing
      allow-action-routing
      response-routing-not-logged-in]]
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
   )
  (if (ssn/am-i-logged-in-fn request)
    (if (action-allowed?
          request-start-line
          request
          (parse-body request)
          allow-action-routing)
      (let [[cookie-key
             cookie-value] (ssn/refresh-session
                             request)
            response
             (case request-start-line
               "POST /am-i-logged-in" (ssn/am-i-logged-in request)
               "POST /get-entities" (dao/get-entities (parse-body request))
               "POST /get-entity" (dao/get-entity (parse-body request))
               "POST /update-entity" (dao/update-entity (parse-body request))
               "POST /insert-entity" (dao/insert-entity (parse-body request))
               "DELETE /delete-entity" (dao/delete-entity (parse-body request))
               "POST /logout" (ssn/logout request)
               "POST /get-labels" (lang/get-labels request)
               "POST /set-language" (lang/set-language
                                      request
                                      (parse-body request))
               "POST /get-allowed-actions" (get-allowed-actions-response
                                             request)
               (if response-routing
                 response-routing
                 {:status (stc/not-found)
                  :headers {(eh/content-type) (mt/text-plain)}
                  :body (str {:status "error"
                              :error-message "404 not found"})})
              )]
        (update-in
          response
          [:headers]
          assoc
          cookie-key
          cookie-value))
      {:status (stc/unauthorized)
       :headers {(eh/content-type) (mt/text-plain)}
       :body (str {:status "success"})})
    (case request-start-line
      "POST /login" (ssn/login-authentication
                      (parse-body
                        request)
                      (:user-agent request))
      "POST /sign-up" (try
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
                          :body (str {:status "error"})})
                       )
      "POST /am-i-logged-in" (ssn/am-i-logged-in request)
      "POST /get-labels" (lang/get-labels request)
      (if response-routing-not-logged-in
        response-routing-not-logged-in
        {:status (stc/unauthorized)
         :headers {(eh/content-type) (mt/text-plain)}
         :body (str {:status "success"})})
     ))
 )

