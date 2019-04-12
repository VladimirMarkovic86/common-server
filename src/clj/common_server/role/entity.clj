(ns common-server.role.entity
  (:require [language-lib.core :refer [get-label]]
            [common-middle.role.entity :as cmre]))

(defn reports
  "Returns reports projection"
  [& [chosen-language]]
  {:entity-label (get-label
                   22
                   chosen-language)
   :projection [:role-name
                ;:functionalities
                ]
   :qsort {:role-name 1}
   :rows cmre/rows
   :labels {:role-name (get-label
                         28
                         chosen-language)
            :functionalities (get-label
                               29
                               chosen-language)}
   :columns {:role-name {:width "140"
                         :header-background-color "lightblue"
                         :header-text-color "white"
                         :column-alignment "C"}}
   })

