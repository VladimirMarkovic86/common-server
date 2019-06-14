(ns common-server.user.entity
  (:require [language-lib.core :refer [get-label]]
            [common-middle.user.entity :as cmue]
            [common-server.preferences :as prf]))

(defn format-email-field
  "Formats email field in \\nolinkurl{email} format"
  [raw-email
   chosen-language]
  (when (and raw-email
             (string?
               raw-email))
    (str
      "nolinkurlopenbraces"
      raw-email
      "closedbraces"))
 )

(defn reports
  "Returns reports projection"
  [request
   & [chosen-language]]
  (prf/set-preferences
    request)
  {:entity-label (get-label
                   21
                   chosen-language)
   :projection [:username
                :email
                ; :password
                ; :roles
                ]
   :qsort {:username 1}
   :rows (int
           (cmue/calculate-rows))
   :table-rows (int
                 @cmue/table-rows-a)
   :card-columns (int
                   @cmue/card-columns-a)
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
                     :data-format-fn format-email-field
                     :column-alignment "C"}}
   })

