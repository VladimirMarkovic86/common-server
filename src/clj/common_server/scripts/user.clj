(ns common-server.scripts.user
  (:require [mongo-lib.core :as mon]
            [utils-lib.core :as utils]
            [common-middle.collection-names :refer [role-cname
                                                    user-cname]]
            [common-middle.role-names :refer [user-admin-rname
                                              language-admin-rname
                                              role-admin-rname
                                              chat-rname]]))

(defn insert-users
  "Inserts roles"
  []
  (let [user-admin-id (:_id
                        (mon/mongodb-find-one
                          role-cname
                          {:role-name user-admin-rname}))
        language-admin-id (:_id
                            (mon/mongodb-find-one
                              role-cname
                              {:role-name language-admin-rname}))
        role-admin-id (:_id
                        (mon/mongodb-find-one
                          role-cname
                          {:role-name role-admin-rname}))
        chat-id (:_id
                  (mon/mongodb-find-one
                    role-cname
                    {:role-name chat-rname}))
        admin-encrypted-password (utils/encrypt-password
                                   (or (System/getenv "ADMIN_USER_PASSWORD")
                                       "123"))
        guest-encrypted-password (utils/encrypt-password
                                   (or (System/getenv "GUEST_USER_PASSWORD")
                                       "123"))
        ]
    (mon/mongodb-insert-one
      user-cname
      {:username "admin"
       :email "123@123"
       :password admin-encrypted-password
       :roles [user-admin-id
               language-admin-id
               role-admin-id
               chat-id]})
    (mon/mongodb-insert-one
      user-cname
      {:username "guest"
       :email "234@234"
       :password guest-encrypted-password
       :roles [chat-id]}))
 )

