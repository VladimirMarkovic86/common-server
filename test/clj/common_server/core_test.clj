(ns common-server.core-test
  (:require [clojure.test :refer :all]
            [common-server.core :refer :all]
            [mongo-lib.core :as mon]
            [ajax-lib.http.response-header :as rsh]
            [ajax-lib.http.entity-header :as eh]
            [ajax-lib.http.mime-type :as mt]
            [ajax-lib.http.status-code :as stc]
            [ajax-lib.http.request-method :as rm]
            [common-middle.request-urls :as rurls]
            [common-middle.role-names :refer [chat-rname]]
            [common-middle.ws-request-actions :as wsra]
            [session-lib.core :as ssn]
            [utils-lib.core :as utils]
            [clojure.set :as cset]))

(def db-uri
     (or (System/getenv "MONGODB_URI")
         (System/getenv "PROD_MONGODB")
         "mongodb://admin:passw0rd@127.0.0.1:27017/admin"))

(def db-name
     "test-db")

(def simple-date-obj
     (java.util.Date.))

(defn create-db
  "Create database for testing"
  []
  (mon/mongodb-connect
    db-uri
    db-name)
  (ssn/create-indexes)
  (mon/mongodb-insert-many
    "role"
    [{:role-name "test-role-1"
      :functionalities ["user-create"
                        "user-update"
                        "user-read"
                        "user-delete"]}
     {:role-name "test-role-2"
      :functionalities ["functionality-5"
                        "functionality-6"]}
     {:role-name chat-rname
      :functionalities ["chat"]}
     ])
  (let [test-role-1 (mon/mongodb-find-one
                      "role"
                      {:role-name "test-role-1"})
        test-role-2 (mon/mongodb-find-one
                      "role"
                      {:role-name "test-role-2"})
        test-chat-role (mon/mongodb-find-one
                         "role"
                         {:role-name chat-rname})]
    (mon/mongodb-insert-one
      "user"
      { :username "test-admin"
        :email "test.123@123"
        :password "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
        :roles [(:_id test-role-1)
                (:_id test-role-2)
                (:_id test-chat-role)] })
    (mon/mongodb-insert-one
      "user"
      { :username "test-guest"
        :email "test.321@321"
        :password "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
        :roles [(:_id test-role-2)
                (:_id test-chat-role)] })
   )
  (let [test-user (mon/mongodb-find-one
                    "user"
                    {:username "test-admin"})
        test-guest (mon/mongodb-find-one
                     "user"
                     {:username "test-guest"})]
    (mon/mongodb-insert-one
      "session"
      { :uuid "test-uuid"
        :user-agent "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:65.0) Gecko/20100101 Firefox/65.0"
        :user-id (:_id test-user)
        :username (:username test-user)
        :created-at (java.util.Date.) })
    (mon/mongodb-insert-one
      "session"
      { :uuid "logout-test-uuid"
        :user-agent "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:65.0) Gecko/20100101 Firefox/65.0"
        :user-id (:_id test-user)
        :username (:username test-user)
        :created-at (java.util.Date.) })
    (mon/mongodb-insert-one
      "long-session"
      { :uuid "test-long-session-uuid"
        :user-agent "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:65.0) Gecko/20100101 Firefox/65.0"
        :user-id (:_id test-guest)
        :username (:username test-guest)
        :created-at (java.util.Date.) })
   )
  (let [test-user (mon/mongodb-find-one
                    "user"
                    {:username "test-admin"})
        test-guest (mon/mongodb-find-one
                     "user"
                     {:username "test-guest"})]
    (mon/mongodb-insert-one
      "chat"
      { :usernames ["test-admin"
                    "test-guest"]
        :messages [{:username "test-admin"
                    :text "test"
                    :sent-at simple-date-obj}
                   {:username "test-guest"
                    :text "test2"
                    :sent-at simple-date-obj}]
         })
    (mon/mongodb-insert-one
      "preferences"
      { :user-id (:_id test-user)
        :language "english"
        :language-name "English"})
   )
  (mon/mongodb-insert-one
    "language"
    { :code 1
      :english "English translation"
      :serbian "Српски превод" })
  (mon/mongodb-insert-many
    "language"
    [{ :code 14
       :english "English translation"
       :serbian "Српски превод" }
     { :code 19
       :english "English translation"
       :serbian "Српски превод" }]))

(defn destroy-db
  "Destroy testing database"
  []
  (mon/mongodb-drop-database
    db-name)
  (mon/mongodb-disconnect))

(defn before-and-after-tests
  "Before and after tests"
  [f]
  (create-db)
  (f)
  (destroy-db))

(use-fixtures :each before-and-after-tests)

(deftest test-get-allowed-actions
  (testing "Test get allowed actions"
    
    (let [request {:cookie "session=test-uuid; session-visible=exists"}
          allowed-functionalities (get-allowed-actions
                                    request)]
      (is
        (= allowed-functionalities
           #{"user-create"
             "user-update"
             "user-read"
             "user-delete"
             "functionality-5"
             "functionality-6"
             "chat"}))
     )
    
    (let [request {:cookie "session=not-existing-uuid; session-visible=exists"}
          allowed-functionalities (get-allowed-actions
                                    request)]
      (is
        (nil?
          allowed-functionalities))
     )
    
    (let [request {:cookie "long-session=test-long-session-uuid; long-session-visible=exists"}
          allowed-functionalities (get-allowed-actions
                                    request)]
      (is
        (= allowed-functionalities
           #{"functionality-5"
             "functionality-6"
             "chat"}))
     )
    
    (let [request {:cookie "long-session=not-existing-long-session-uuid; long-session-visible=exists"}
          allowed-functionalities (get-allowed-actions
                                    request)]
      (is
        (nil?
          allowed-functionalities))
     )
    
   ))

(deftest test-get-allowed-actions-response
  (testing "Test get allowed actions response"
    
    (let [request {:cookie "session=test-uuid; session-visible=exists"
                   :request-method rm/POST
                   :request-uri rurls/get-allowed-actions-url}
          response (routing-fn
                     request)]
      (is
        (= response
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"
                   :data #{"user-create"
                           "user-update"
                           "user-read"
                           "user-delete"
                           "functionality-5"
                           "functionality-6"
                           "chat"}}
            }))
     )
    
    (let [request {:cookie "session=not-existing-uuid; session-visible=exists"
                   :request-method rm/POST
                   :request-uri rurls/get-allowed-actions-url}
          response (routing-fn
                     request)]
      (is
        (nil?
          response))
     )
    
    (let [request {:cookie "long-session=test-long-session-uuid; long-session-visible=exists"
                   :request-method rm/POST
                   :request-uri rurls/get-allowed-actions-url}
          response (routing-fn
                     request)]
      (is
        (= response
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"
                   :data #{"functionality-5"
                           "functionality-6"
                           "chat"}}
            }))
     )
    
    (let [request {:cookie "long-session=not-existing-long-session-uuid; long-session-visible=exists"
                   :request-method rm/POST
                   :request-uri rurls/get-allowed-actions-url}
          response (routing-fn
                     request)]
      (is
        (nil?
          response))
     )
    
   ))

(deftest test-get-chat-users
  (testing "Test get chat users"
   
    (let [chat-users-response (routing-fn
                                {:cookie "long-session=test-long-session-uuid; long-session-visible=exists"
                                 :request-method rm/POST
                                 :request-uri rurls/get-chat-users-url})
          chat-users (into
                       #{}
                       (get-in
                         chat-users-response
                         [:body
                          :data]))
          chat-users-response (update-in
                                chat-users-response
                                [:body]
                                assoc
                                :data chat-users)]
      
      
      (is
        (= chat-users-response
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"
                   :data #{{:username "test-admin"}
                           {:username "test-guest"}}}
            }))
      
     )
   
   ))

(deftest test-get-chat-history
  (testing "Test get chat history"
    
    (is
      (= (get-chat-history
           ["test-admin"
            "test-guest"])
         { :messages [{:username "test-admin"
                       :text "test"
                       :sent-at simple-date-obj}
                      {:username "test-guest"
                       :text "test2"
                       :sent-at simple-date-obj}]}))
    
   ))

(deftest test-save-chat-message
  (testing "Test save chat message"
    
    (let [save-message (save-chat-message
                         {:usernames ["test-admin-chat"
                                      "test-guest-chat"]
                          :message {:username "test-admin-chat"
			                                 :text "test3"}})
			       chat-history (mon/mongodb-find-one
                         "chat"
                         {:usernames ["test-admin-chat"
                                      "test-guest-chat"]})
          chat-messages (:messages chat-history)
          chat-messages-set (into
                              #{}
                              chat-messages)]
      
      (is
        (not
          (empty?
            (clojure.set/select
              (fn [{username :username
                    text :text
                    sent-at :sent-at}]
                (and (= username
                        "test-admin-chat")
                     (= text
                        "test3")
                     (< (- (.getTime
                             (java.util.Date.))
                           (.getTime
                             sent-at))
                        (* 1000
                           5))
                 ))
              chat-messages-set))
         ))
     
     )
    
   ))

(deftest test-remove-websocket-from-set
  (testing "Test remove websocket from set"
    
    (let [fill-chat-websocket-set-a (swap!
                                      chat-websocket-set-a
                                      conj
                                      {:username "test-admin-ws"}
                                      {:username "test-guest-ws"})]
      
      (is
        (= @chat-websocket-set-a
           #{{:username "test-admin-ws"}
             {:username "test-guest-ws"}}))
      
      (remove-websocket-from-set
        "test-admin-ws")
      
      (is
        (= @chat-websocket-set-a
           #{{:username "test-guest-ws"}}))
      
      (remove-websocket-from-set
        "test-guest-ws")
      
      (is
        (= @chat-websocket-set-a
           #{}))
            
     )
    
   ))

(deftest test-get-websocket-output-fn
  (testing "Test get websocket output function"
    
    (let [quasi-socket (java.net.Socket.)
          quasi-function (fn [] "quasi function")
          fill-chat-websocket-set-a (swap!
                                      chat-websocket-set-a
                                      conj
                                      {:username "test-admin-ws"
                                       :websocket-socket quasi-socket
                                       :websocket-output-fn quasi-function})]
      
      (is
        (= @chat-websocket-set-a
           #{{:username "test-admin-ws"
              :websocket-socket quasi-socket
              :websocket-output-fn quasi-function}}))
      
      (is
        (= (get-websocket-output-fn
             "test-admin-ws")
           quasi-function))
      
      (.close
        quasi-socket)
      
      (is
        (= (get-websocket-output-fn
             "test-admin-ws")
           nil))
      
      (is
        (= @chat-websocket-set-a
           #{}))
            
     )
    
   ))

(deftest test-chat-ws
  (testing "Test chat ws"
    
    (let [quasi-socket (java.net.Socket.)
          quasi-function (fn [& [param1
                                 param2]]
                           "quasi function")]
      
      (routing-fn
        {:request-method rm/ws-GET
         :request-uri rurls/chat-url
         :cookie "long-session=test-long-session-uuid; long-session-visible=exists"
         :websocket
          {:websocket-message
            (str
              {:action wsra/establish-connection-action
               :username "test-admin-ws"})
           :websocket-socket quasi-socket
           :websocket-output-fn quasi-function}})
      
      (is
        (= @chat-websocket-set-a
           #{{:username "test-admin-ws"
              :websocket-socket quasi-socket
              :websocket-output-fn quasi-function}}))
      
      (routing-fn
        {:request-method rm/ws-GET
         :request-uri rurls/chat-url
         :cookie "long-session=test-long-session-uuid; long-session-visible=exists"
         :websocket
          {:websocket-message
            (str
              {:action wsra/establish-connection-action
               :username "test-guest-ws"})
           :websocket-socket quasi-socket
           :websocket-output-fn quasi-function}})
      
      (is
        (= @chat-websocket-set-a
           #{{:username "test-admin-ws"
              :websocket-socket quasi-socket
              :websocket-output-fn quasi-function}
             {:username "test-guest-ws"
              :websocket-socket quasi-socket
              :websocket-output-fn quasi-function}}))
      
      (let [save-message (routing-fn
                           {:request-method rm/ws-GET
                            :request-uri rurls/chat-url
                            :cookie "long-session=test-long-session-uuid; long-session-visible=exists"
                            :websocket
                             {:websocket-message
                               (str
                                 {:action wsra/send-chat-message-action
                                  :usernames ["test-admin-ws"
                                              "test-guest-ws"]
                                  :message {:username "test-admin-ws"
                                            :text "test4"}})
                              :websocket-socket quasi-socket
                              :websocket-output-fn quasi-function}})
			         chat-history (mon/mongodb-find-one
                           "chat"
                           {:usernames ["test-admin-ws"
                                        "test-guest-ws"]})
            chat-messages (:messages chat-history)
            chat-messages-set (into
                                #{}
                                chat-messages)]
        
        (is
          (not
            (empty?
              (clojure.set/select
                (fn [{username :username
                      text :text
                      sent-at :sent-at}]
                  (and (= username
                          "test-admin-ws")
                       (= text
                          "test4")
                       (< (- (.getTime
                               (java.util.Date.))
                             (.getTime
                               sent-at))
                          (* 1000
                             5))
                   ))
                chat-messages-set))
           ))
       
       )
      
      (routing-fn
        {:request-method rm/ws-GET
         :request-uri rurls/chat-url
         :cookie "long-session=test-long-session-uuid; long-session-visible=exists"
         :websocket
          {:websocket-message
            (str
              {:action wsra/close-connection-action
               :username "test-admin-ws"})
           :websocket-socket quasi-socket
           :websocket-output-fn quasi-function}})
      
      (routing-fn
        {:request-method rm/ws-GET
         :request-uri rurls/chat-url
         :cookie "long-session=test-long-session-uuid; long-session-visible=exists"
         :websocket
          {:websocket-message
            (str
              {:action wsra/close-connection-action
               :username "test-guest-ws"})
           :websocket-socket quasi-socket
           :websocket-output-fn quasi-function}})
      
      (is
        (= @chat-websocket-set-a
           #{}))
      
     )
    
   ))

(deftest test-read-sign-up-role-ids
  (testing "Test read sign up role ids"
    
    (let [role-names-vector nil
          result (read-sign-up-role-ids
                   role-names-vector)]
      
      (is
        (nil?
          result)
       )
      
     )
    
    (let [role-names-vector "test"
          result (read-sign-up-role-ids
                   role-names-vector)]
      
      (is
        (nil?
          result)
       )
      
     )
    
    (let [role-names-vector [chat-rname]
          result (read-sign-up-role-ids
                   role-names-vector)]
      
      (is
        (vector?
          @sign-up-roles)
       )
      
      (is
        (not
          (empty?
            @sign-up-roles))
       )
      
      (let [first-role-id (first
                            @sign-up-roles)]
        (is
          (string?
            first-role-id)
         )
       )
      
     )
    
   ))

(deftest test-sign-up
  (testing "Test sign up"
    
    (let [password-123 "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
          new-user { :username "testsignupuser"
	                    :email "345@345"
	                    :password password-123}
	         new-user-same-username { :username "testsignupuser"
	                                  :email "543@543"
	                                  :password password-123}
	         new-user-same-email { :username "testsignupuserchanged"
                                :email "345@345"
                                :password password-123}]
      (is
        (not
          (mon/mongodb-exists
            "user"
            new-user))
       )
      
      (is
        (= (routing-fn
             {:request-method rm/POST
              :request-uri rurls/sign-up-url
              :body
               {:entity-type "user"
                :entity new-user}})
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"}}))
      
      (is
        (mon/mongodb-exists
          "user"
          new-user))
      
      (let [signup-response (routing-fn
                              {:request-method rm/POST
                               :request-uri rurls/sign-up-url
                               :body
                                {:entity-type "user"
                                 :entity new-user-same-username}})]
        (is
          (= (:status signup-response)
             (stc/internal-server-error))
         )
        
        (is
          (= (get-in
               signup-response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               signup-response
               [:body
                :status])
             "error")
         )
       )
      
      (let [signup-response (routing-fn
                              {:request-method rm/POST
                               :request-uri rurls/sign-up-url
                               :body
                                {:entity-type "user"
                                 :entity new-user-same-email}})]
        
        (is
          (= (:status signup-response)
             (stc/internal-server-error))
         )
        
        (is
          (= (get-in
               signup-response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               signup-response
               [:body
                :status])
             "error")
         )
       )
      
     )
    
   ))

(deftest test-not-found
  (testing "Test not found"
    
    (is
      (= (not-found)
         {:status (stc/not-found)
          :headers {(eh/content-type) (mt/text-clojurescript)}
          :body {:status "error"
                 :error-message "404 not found"}}))
     
   ))

(deftest test-not-authorized
  (testing "Test not authorized"
    
    (is
      (= (routing-fn
           {:cookie "long-session=test-long-session-uuid; long-session-visible=exists"
            :request-method "*"
            :request-uri rurls/get-entities-url})
         {:status (stc/unauthorized)
          :headers {(eh/content-type) (mt/text-clojurescript)}
          :body {:status "success"}}))
     
   ))

(deftest test-response-to-options
  (testing "Test response to options"
    
    (is
      (= (routing-fn
           {:request-method rm/OPTIONS
            :request-uri "*"})
         {:status (stc/ok)
          :headers {(eh/content-type) (mt/text-clojurescript)}
          :body {:status "success"}}))
     
   ))

(deftest test-get-report
  (testing "Test get report"
    
    (let [request nil
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/not-found))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/text-clojurescript))
       )
      
      (is
        (= (get-in
             result
             [:body
              :status])
           "error")
       )
      
     )
    
    (let [request {:request-get-params
                    {:report "table"
                     :entity "user"
                     :page "-1"}}
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/application-pdf))
       )
      
      (is
        (bytes?
          (:body result))
       )
      
     )
    
    (let [request {:request-get-params
                    {:report "table"
                     :entity "user"
                     :page "-1"
                     :language "serbian"}}
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/application-pdf))
       )
      
      (is
        (bytes?
          (:body result))
       )
      
     )
    
    (let [request {:request-get-params
                    {:report "single"
                     :entity "user"
                     :page "-1"}}
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/application-pdf))
       )
      
      (is
        (bytes?
          (:body result))
       )
      
     )
    
    (let [request {:request-get-params
                    {:report "single"
                     :entity "user"
                     :page "-1"
                     :language "serbian"}
                   }
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/application-pdf))
       )
      
      (is
        (bytes?
          (:body result))
       )
      
     )
    
    (let [base64-png "data:image/png;base64,/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAIAAcDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDj/Dvhv4lQ/FW6gtZP7L8SyxS3tzPOyCJo3OWYhQyupdgMKGAbsNpIKKKAP//Z"
          request {:request-get-params
                    {:report "chart"
                     :language "english"}
                   :body {:base64-png base64-png}}
          result (get-report
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (get-in
             result
             [:headers
              (eh/content-type)])
           (mt/application-pdf))
       )
      
      (is
        (bytes?
          (:body result))
       )
      
     )
    
   ))

(deftest test-read-preferences
  (testing "Test read preferences"
    
    (let [request {:request-method rm/POST
                   :request-uri rurls/read-preferences-url}
          result (routing-fn
                   request)]
      
      (is 
        (nil?
          result)
       )
      
     )
    
    (let [request {:cookie "session=test-uuid; session-visible=exists"
                   :request-method rm/POST
                   :request-uri rurls/read-preferences-url}
          result (routing-fn
                   request)]
      
      (is
        (= (:status result)
           (stc/ok))
       )
      
      (is
        (= (:headers result)
           {"Content-Type" "text/clojurescript"})
       )
      
      (is 
        (contains?
          (get-in
            result
            [:body
             :preferences])
          :language)
       )
      
      (is 
        (contains?
          (get-in
            result
            [:body
             :preferences])
          :language-name)
       )
      
     )
    
   ))

(deftest test-save-preferences
  (testing "Test save preferences"
    
    (let [request {:request-method rm/POST
                   :request-uri rurls/save-preferences-url}
          result (routing-fn
                   request)]
      
      (is
        (nil?
          result)
       )
      
     )
    
    (let [request {:cookie "session=test-uuid; session-visible=exists"
                   :body {:preferences {:test-preferences 1}}
                   :request-method rm/POST
                   :request-uri rurls/save-preferences-url}
          result (routing-fn
                   request)]
      
      (is
        (= result
           {:status (stc/ok)
            :headers {"Content-Type" "text/clojurescript"}
            :body {:status "success"}})
       )
      
     )
    
   ))

(deftest test-forgot-password-reset-password-code-reset-password-final
  (testing "Test forgot password, reset password code and reset password final"
    
    (let [request {:request-method rm/POST
                   :request-uri rurls/forgot-password-url} 
          result (routing-fn
                   request)]
      
      (is
        (= result
           (not-found))
       )
      
     )
    
    (let [request {:body {:email "test.123@123"}
                   :accept-language "sr,en;q=0.5"
                   :request-method rm/POST
                   :request-uri rurls/forgot-password-url}
          result (routing-fn
                   request)]
      
      (is
        (= result
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"}})
       )
      
     )
    
    (let [request {:request-method rm/POST
                   :request-uri rurls/reset-password-code-url} 
          result (routing-fn
                   request)]
      
      (is
        (= result
           (not-found))
       )
      
     )
    
    (let [reset-password-db-obj (mon/mongodb-find-one
                                  "reset-password"
                                  {:email "test.123@123"})
          code (:uuid reset-password-db-obj)
          request {:body {:uuid code}
                   :accept-language "sr,en;q=0.5"
                   :request-method rm/POST
                   :request-uri rurls/reset-password-code-url}
          result (routing-fn
                   request)]
      
      (is
        (= result
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"
                   :email "test.123@123"
                   :uuid code}})
       )
      
     )
    
    (let [request {:request-method rm/POST
                   :request-uri rurls/reset-password-final-url} 
          result (routing-fn
                   request)]
      
      (is
        (= result
           (not-found))
       )
      
     )
    
    (let [reset-password-db-obj (mon/mongodb-find-one
                                  "reset-password"
                                  {:email "test.123@123"})
          code (:uuid reset-password-db-obj)
          request {:body {:uuid code
                          :new-password (utils/sha256
                                          "123")}
                   :accept-language "sr,en;q=0.5"
                   :request-method rm/POST
                   :request-uri rurls/reset-password-final-url}
          result (routing-fn
                   request)]
      
      (is
        (= result
           {:status (stc/ok)
            :headers {(eh/content-type) (mt/text-clojurescript)}
            :body {:status "success"}})
       )
      
      (is
        (nil?
          (mon/mongodb-find-one
            "reset-password"
            {:email "test.123@123"}))
       )
      
     )
    
   ))

(deftest test-print-request
  (testing "Test print request"
    
    (is
      (= (print-request
           {})
         {}))
    
    (is
      (= (print-request
           {:body "test"
            :content-length "4"})
         {:body "test"
          :content-length "4"}))
    
    (is
      (= (print-request
           {:body "test"
            :content-length "300"})
         {:content-length "300"}))
    
    (is
      (= (print-request
           {:body "test"})
         {:body "test"}))
    
    (is
      (= (print-request
           {:content-length "1"})
         {:content-length "1"}))
    
    (is
      (= (print-request
           {:body "test123456test123456test123456test123456test123456test123456test123456test123456test123456  test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456"})
         {}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 4
                        :websocket-message "Test"}})
         {:websocket {:websocket-message-length 4
                      :websocket-message "Test"}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 300
                        :websocket-message "Test"}})
         {:websocket {:websocket-message-length 300}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message "Test"}})
         {:websocket {:websocket-message "Test"}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message "test123456test123456test123456test123456test123456test123456test123456test123456test123456  test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456"}})
         {:websocket {}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 4}})
         {:websocket {:websocket-message-length 4}}))

   ))

(deftest test-routing
  (testing "Test routing"
    
    (let [user-password "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
          new-user { :username "testsignupuserrequest"
	                    :email "request345@345"
	                    :password user-password}]
      (let [request {}
            response (routing
                         request)]
        (is
          (= response
             (not-found))
         )
       )
      
      (let [request {:request-method rm/OPTIONS
                     :request-uri "*"}
            response (routing
                       request)]
        (is
          (= response
             response-to-options)
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/login-url}
            response (routing
                       request)]
        (is
          (= response
             {:status (stc/internal-server-error)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "error"}})
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/login-url
                     :body {:email "test-admin"
                            :password user-password}}
            response (routing
                       request)]
        (is
          (= response
             {:status (stc/internal-server-error)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "error"}})
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/login-url
                     :user-agent "test user agent"
                     :body {:email "test-admin"
                            :password user-password}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (:body response)
             {:status "success"
              :email "success"
              :password "success"
              :username "test-admin"
              :language "english"
              :language-name "English"})
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/sign-up-url}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/internal-server-error))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "error")
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/sign-up-url
                     :body {:entity-type "user"
                            :entity new-user}}
            response (routing
                       request)]
        (is
          (= response
             {:status (stc/ok)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "success"}})
         )
        
        (is
          (mon/mongodb-exists
            "user"
            new-user))
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/am-i-logged-in-url}
            response (routing
                       request)]
        (is
          (= response
             {:status (stc/unauthorized)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body "It's not ok"})
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/am-i-logged-in-url}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/unauthorized))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (:body response)
             "It's not ok")
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/am-i-logged-in-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (:body response)
             {:status "It's ok"
              :username "test-admin"
              :language "english"
              :language-name "English"})
         )
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-labels-url}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (get-in
               response
               [:body
                :language])
             "english")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])
              data-element (first
                             data)]
          (is
            (= (int
                 (:code data-element))
               1)
           )
          
          (is
            (= (:english data-element)
               "English translation")
           )
          
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-labels-url
                     :accept-language	"sr,en;q=0.5"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (get-in
               response
               [:body
                :language])
             "serbian")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])
              data-element (first
                             data)]
          (is
            (= (int
                 (:code data-element))
               1)
           )
          
          (is
            (= (:serbian data-element)
               "Српски превод")
           )
          
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-entities-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])
              data-set (into
                         #{}
                         data)
              select-fn (fn [{username :username}]
                          (or (= username
                                 "test-admin")
                              (= username
                                 "test-guest")
                              (= username
                                 "testsignupuserrequest"))
                         )
              selected-set (cset/select
                             select-fn
                             data-set)]
          (is
            (= (count
                 selected-set)
               3)
           )
         )
       )
      
      (let [new-user-from-db (mon/mongodb-find-one
	                              "user"
	                              new-user)
            request {:request-method rm/POST
                     :request-uri rurls/get-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :entity-filter {:_id (:_id new-user-from-db)}
                            :entity-projection [:username]
                            :projection-include true}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])]
          (is
            (= (:username data)
               "testsignupuserrequest")
           )
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :entity-filter {:_id ""}
                            :entity-projection [:username]
                            :projection-include true}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
       )
      
      (let [new-user-from-db (mon/mongodb-find-one
	                              "user"
	                              new-user)
            request {:request-method rm/POST
                     :request-uri rurls/update-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :_id (:_id new-user-from-db)
                            :entity {:email "request345@345changed"}}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (let [{username :username
               email :email
               password :password} (mon/mongodb-find-by-id
                                     "user"
                                     (:_id new-user-from-db))]
          
          (is
            (= username
               "testsignupuserrequest")
           )
          
          (is
            (= email
               "request345@345changed")
           )
          
          (is
            (= password
               user-password)
           )
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/update-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :_id ""
                            :entity {:email "request345@345changed"}}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/internal-server-error))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "Error")
         )
        
        (is
          (= (get-in
               response
               [:body
                :status-code])
             70)
         )
        
       )
      
      (let [test-role-1 (mon/mongodb-find-one
                          "role"
                          {:role-name "test-role-1"})
            test-role-2 (mon/mongodb-find-one
                          "role"
                          {:role-name "test-role-2"})
            test-chat-role (mon/mongodb-find-one
                             "role"
                             {:role-name chat-rname})
            request {:request-method rm/POST
                     :request-uri rurls/insert-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :entity {:username "insert-test-guest"
                                     :email "inserttest345@345"
                                     :password user-password
                                     :roles [(:_id test-role-1)
                                             (:_id test-role-2)
                                             (:_id test-chat-role)]}}
                     }
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "Success")
         )
        
        (is
          (mon/mongodb-exists
            "user"
            {:username "insert-test-guest"
             :email "inserttest345@345"
             :password user-password})
         )
        
       )
      
      (is
        (mon/mongodb-exists
          "session"
          {:uuid "logout-test-uuid"}))
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/logout-url
                     :cookie "session=logout-test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-plain))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (:body response)
             "Bye bye")
         )
        
        (is
          (not
            (mon/mongodb-exists
              "session"
              {:uuid "logout-test-uuid"}))
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/logout-url
                     :cookie "session=logout-test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= response
             {:status (stc/not-found)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "error"
                     :error-message "404 not found"}})
         )
       
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-labels-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (get-in
               response
               [:body
                :language])
             "english")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])
              data-element (first
                             data)]
          (is
            (= (int
                 (:code data-element))
               1)
           )
          
          (is
            (= (:english data-element)
               "English translation")
           )

         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-labels-url
                     :accept-language	"sr,en;q=0.5"
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (get-in
               response
               [:body
                :language])
             "english")
         )
        
        (let [data (get-in
                     response
                     [:body
                      :data])
              data-element (first
                             data)]
          (is
            (= (int
                 (:code data-element))
               1)
           )
          
          (is
            (= (:english data-element)
               "English translation")
           )
          
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/set-language-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/set-language-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:language "serbian"
                            :language-name "Serbian"}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (let [test-user-admin (mon/mongodb-find-one
                                "user"
                                {:username "test-admin"})
              test-user-admin-preferences (mon/mongodb-find-one
                                            "preferences"
                                            {:user-id (:_id test-user-admin)})]
          
          (is
            (= (:language test-user-admin-preferences)
               "serbian")
           )
          
          (is
            (= (:language-name test-user-admin-preferences)
               "Serbian")
           )
           
         )
       
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-allowed-actions-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (get-in
               response
               [:body
                :data])
             #{"user-create"
               "user-update"
               "user-read"
               "user-delete"
               "functionality-5"
               "functionality-6"
               "chat"})
         )
       
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-allowed-actions-url}
            response (routing
                       request)]
        
        (is
          (= response
             {:status (stc/not-found)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "error"
              :error-message "404 not found"}}))
       
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-chat-users-url}
            response (routing
                       request)]
        
        (is
          (= response
             {:status (stc/not-found)
              :headers {(eh/content-type) (mt/text-clojurescript)}
              :body {:status "error"
              :error-message "404 not found"}}))
       
       )
      
      (let [request {:request-method rm/POST
                     :request-uri rurls/get-chat-users-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
        
        (is
          (= (into
               #{}
               (get-in
                 response
                 [:body
                  :data]))
             #{{:username "test-admin"}
               {:username "test-guest"}
               {:username "insert-test-guest"}})
         )
       
       )
      
      (let [quasi-socket (java.net.Socket.)
            quasi-function (fn [& [param1
                                   param2]]
                             "quasi function")
            request {:request-method rm/ws-GET
                     :request-uri rurls/chat-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :websocket
                      {:websocket-message
                        (str
                          {:action wsra/establish-connection-action
                           :username "test-admin-ws"})
                       :websocket-socket quasi-socket
                       :websocket-output-fn quasi-function}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/not-found))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "error")
         )
       
       )
      
      (let [request {:request-method rm/DELETE
                     :request-uri rurls/delete-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :entity-filter {:_id ""}}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/internal-server-error))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "Error")
         )
        
        (is
          (= (get-in
               response
               [:body
                :status-code])
             70)
         )
        
       )
      
      (is
        (mon/mongodb-exists
          "user"
          {:username "insert-test-guest"})
       )
      
      (let [user-from-db (mon/mongodb-find-one
                           "user"
                           {:username "insert-test-guest"})
            request {:request-method rm/DELETE
                     :request-uri rurls/delete-entity-url
                     :cookie "session=test-uuid; session-visible=exists"
                     :user-agent "test user-agent"
                     :body {:entity-type "user"
                            :entity-filter {:_id (:_id user-from-db)}}}
            response (routing
                       request)]
        
        (is
          (= (:status response)
             (stc/ok))
         )
        
        (is
          (= (get-in
               response
               [:headers
                (eh/content-type)])
             (mt/text-clojurescript))
         )
        
        (is
          (not
            (nil?
              (get-in
                response
                [:headers
                 (rsh/set-cookie)])
             ))
         )
        
        (is
          (= (get-in
               response
               [:body
                :status])
             "success")
         )
      
         (is
           (not
             (mon/mongodb-exists
               "user"
               {:username "insert-test-guest"}))
          )
        
       )
      
      
     )
    
   ))

