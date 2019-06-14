(ns common-server.role.entity
  (:require [language-lib.core :refer [get-label]]
            [common-middle.role.entity :as cmre]
            [common-server.preferences :as prf]))

(defn reports
  "Returns reports projection"
  [request
   & [chosen-language]]
  (prf/set-preferences
    request)
  {:entity-label (get-label
                   22
                   chosen-language)
   :projection [:role-name
                ;:functionalities
                ]
   :qsort {:role-name 1}
   :rows (int
           (cmre/calculate-rows))
   :table-rows (int
                 @cmre/table-rows-a)
   :card-columns (int
                   @cmre/card-columns-a)
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

