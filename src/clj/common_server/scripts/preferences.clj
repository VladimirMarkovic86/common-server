(ns common-server.scripts.preferences
  (:require [mongo-lib.core :as mon]
            [common-middle.collection-names :refer [preferences-cname
                                                    user-cname]]))

(defn insert-preferences
  "Inserts preferences"
  []
  (let [admin-user-id (:_id
                        (mon/mongodb-find-one
                          user-cname
                          {:username "admin"}))
        guest-user-id (:_id
                        (mon/mongodb-find-one
                          user-cname
                          {:username "guest"}))]
    (mon/mongodb-insert-one
      preferences-cname
      {:user-id admin-user-id
       :language "english"
       :language-name "English"})
    (mon/mongodb-insert-one
      preferences-cname
      {:user-id guest-user-id
       :language "english"
       :language-name "English"}))
 )

