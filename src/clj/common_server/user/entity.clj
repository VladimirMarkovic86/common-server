(ns common-server.user.entity
  (:require [language-lib.core :refer [get-label]]
            [common-middle.user.entity :as cmue]))

(defn reports
  "Returns reports projection"
  [& [chosen-language]]
  {:entity-label (get-label
                   21
                   chosen-language)
   :projection [:username
                :email
                ; :password
                ; :roles
                ]
   :qsort {:username 1}
   :rows cmue/rows
   :labels {:username (get-label
                        19
                        chosen-language)
            :email (get-label
                     14
                     chosen-language)}
   :columns {:username {:width "50"
                        :header-background-color "lightblue"
                        :header-text-color "white"
                        :column-alignment "C"}
             :email {:width "90"
                     :header-background-color "lightblue"
                     :header-text-color "white"
                     :column-alignment "C"}}
   })

