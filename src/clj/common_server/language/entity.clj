(ns common-server.language.entity
  (:require [language-lib.core :refer [get-label]]
            [common-middle.language.entity :as cmle]
            [common-server.preferences :as prf]))

(defn format-code-field
  "Formats code field of language entity into simple integer"
  [raw-code
   chosen-language]
  (when (and raw-code
             (number?
               raw-code))
    (int
      raw-code))
 )

(defn reports
  "Returns reports projection"
  [request
   & [chosen-language]]
  (prf/set-preferences
    request)
  {:entity-label (get-label
                   23
                   chosen-language)
   :projection [:code
                :english
                :serbian
                ]
   :qsort {:code 1}
   :rows (int
           (cmle/calculate-rows))
   :table-rows (int
                 @cmle/table-rows-a)
   :card-columns (int
                   @cmle/card-columns-a)
   :labels {:code (get-label
                    24
                    chosen-language)
            :english (get-label
                       25
                       chosen-language)
            :serbian (get-label
                       26
                       chosen-language)}
   :columns {:code {:width "20"
                    :header-background-color "lightblue"
                    :header-text-color "white"
                    :data-format-fn format-code-field
                    :column-alignment "C"}
             :english {:width "60"
                       :header-background-color "lightblue"
                       :header-text-color "white"}
             :serbian {:width "60"
                       :header-background-color "lightblue"
                       :header-text-color "white"}}
   })

