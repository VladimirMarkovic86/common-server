(ns common-server.core
  (:require [session-lib.core :as ssn]
            [mongo-lib.core :as mon]
            [language-lib.core :as lang]
            [utils-lib.core :refer [parse-body]]
            [dao-lib.core :as dao]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]
            [common-middle.request-urls :as rurls]))

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
  [request
   request-body
   allow-action-routing]
  (let [allowed (atom false)
        execute-functionality (atom nil)
        allowed-functionalities (get-allowed-actions
                                  request)
        {request-uri :request-uri
         request-method :request-method} request]
    (cond
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
          :else
            (reset!
              allowed
              allow-action-routing))
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
              allow-action-routing))
      :else
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
     :body (str {:status "error"})})
 )

(defn not-found
  ""
  [response-routing]
  (if response-routing
    response-routing
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
  (let [{request-uri :request-uri
         request-method :request-method} request]
    (if (ssn/am-i-logged-in-fn request)
      (if (action-allowed?
            request
            (parse-body request)
            allow-action-routing)
        (let [[cookie-key
               cookie-value] (ssn/refresh-session
                               request)
              response
               (cond
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
                     :else
                       (not-found
                         response-routing))
                 (= request-method
                    "DELETE")
                   (cond
                     (= request-uri
                        rurls/delete-entity-url)
                       (dao/delete-entity (parse-body request))
                     :else
                       (not-found
                         response-routing))
                 :else
                   (not-found
                     response-routing))]
          (update-in
            response
            [:headers]
            assoc
            cookie-key
            cookie-value))
        {:status (stc/unauthorized)
         :headers {(eh/content-type) (mt/text-plain)}
         :body (str {:status "success"})})
      (cond
        (= request-method
           "OPTIONS")
          {:status (stc/ok)}
        (= request-method
           "POST")
          (cond
            (= request-uri
               rurls/login-url)
              (ssn/login-authentication
                (parse-body
                  request)
                (:user-agent request))
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
                  :body (str {:status "error"})})
               )
            (= request-uri
               rurls/am-i-logged-in-url)
              (ssn/am-i-logged-in request)
            (= request-uri
               rurls/get-labels-url)
              (lang/get-labels request)
            :else
              (not-found
                response-routing-not-logged-in))
        :else
          (not-found
            response-routing-not-logged-in))
     ))
 )

