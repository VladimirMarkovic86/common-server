(ns common-server.preferences
  (:require [session-lib.core :as ssn]
            [common-middle.user.entity :as cmue]
            [common-middle.role.entity :as cmre]
            [common-middle.language.entity :as cmle]))

(def set-specific-preferences-a-fn
     (atom nil))

(defn set-preferences
  "Sets preferences on server side"
  [request]
  (let [preferences (ssn/get-preferences
                      request)
        {{{table-rows-l :table-rows
           card-columns-l :card-columns} :language-entity
          {table-rows-r :table-rows
           card-columns-r :card-columns} :role-entity
          {table-rows-u :table-rows
           card-columns-u :card-columns} :user-entity} :display
         specific-map :specific} preferences]
    (reset!
      cmle/table-rows-a
      (or table-rows-l
          10))
    (reset!
      cmle/card-columns-a
      (or card-columns-l
          0))
    (reset!
      cmue/table-rows-a
      (or table-rows-u
          10))
    (reset!
      cmue/card-columns-a
      (or card-columns-u
          0))
    (reset!
      cmre/table-rows-a
      (or table-rows-r
          10))
    (reset!
      cmre/card-columns-a
      (or card-columns-r
          0))
    (when (fn?
            @set-specific-preferences-a-fn)
      (@set-specific-preferences-a-fn
        specific-map))
   ))

