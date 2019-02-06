(ns common-server.scripts.language
  (:require [mongo-lib.core :as mon]
            [common-middle.collection-names :refer [language-cname]]))

(defn insert-labels
  "Inserts labels"
  []
  (mon/mongodb-insert-many
    language-cname
    [{ :code 1 :english "Save" :serbian "Сачувај" }
     { :code 2 :english "Log out" :serbian "Одјави се" }
     { :code 3 :english "Home" :serbian "Почетна" }
     { :code 4 :english "Create" :serbian "Креирај" }
     { :code 5 :english "Show all" :serbian "Прикажи све" }
     { :code 6 :english "Details" :serbian "Детаљи" }
     { :code 7 :english "Edit" :serbian "Измени" }
     { :code 8 :english "Delete" :serbian "Обриши" }
     { :code 9 :english "Actions" :serbian "Акције" }
     { :code 10 :english "Insert" :serbian "Упиши" }
     { :code 11 :english "Update" :serbian "Ажурирај" }
     { :code 12 :english "Cancel" :serbian "Откажи" }
     { :code 13 :english "Search" :serbian "Претрага" }
     { :code 14 :english "E-mail" :serbian "Е-пошта" }
     { :code 15 :english "Password" :serbian "Лозинка" }
     { :code 16 :english "Remember me" :serbian "Упамти ме" }
     { :code 17 :english "Log in" :serbian "Пријави се" }
     { :code 18 :english "Sign up" :serbian "Направи налог" }
     { :code 19 :english "Username" :serbian "Корисничко име" }
     { :code 20 :english "Confirm password" :serbian "Потврди лозинку" }
     { :code 21 :english "User" :serbian "Корисник" }
     { :code 22 :english "Role" :serbian "Улога" }
     { :code 23 :english "Language" :serbian "Језик" }
     { :code 24 :english "Label code" :serbian "Код лабеле" }
     { :code 25 :english "English" :serbian "Енглески" }
     { :code 26 :english "Serbian" :serbian "Српски" }
     { :code 27 :english "Functionality" :serbian "Функционалност" }
     { :code 28 :english "Role name" :serbian "Назив улоге" }
     { :code 29 :english "Functionalities" :serbian "Функционалности" }
     { :code 30 :english "Roles" :serbian "Улоге" }
     { :code 31 :english "No entities" :serbian "Нема ентитета" }
     { :code 32 :english "Administration" :serbian "Администрација" }
     { :code 33 :english "- Select -" :serbian "- Одабери -" }
     { :code 34 :english "first" :serbian "прва" }
     { :code 35 :english "previous" :serbian "претходна" }
     { :code 36 :english "next" :serbian "следећа" }
     { :code 37 :english "last" :serbian "задња" }
     { :code 38 :english "Bad input" :serbian "Погрешан унос" }
     { :code 39 :english "Custom error" :serbian "Грешка" }
     { :code 40 :english "Pattern mismatch" :serbian "Не поклапање шаблона" }
     { :code 41 :english "Range overflow" :serbian "Прекорачење опсега" }
     { :code 42 :english "Range underflow" :serbian "Не достигнут опсег" }
     { :code 43 :english "Step mismatch" :serbian "Не поклапање корака" }
     { :code 44 :english "Too long" :serbian "Предуго" }
     { :code 45 :english "Too short" :serbian "Прекратко" }
     { :code 46 :english "Type mismatch" :serbian "Не поклапање типа" }
     { :code 47 :english "Value missing" :serbian "Недостатак вредности" }
     { :code 48,
       :english "Please fill out this field.",
       :serbian "Молим попуните ово поље." }
     { :code 49,
       :english "Please enter a number.",
       :serbian "Молим унесите број." }
     { :code 50,
       :english "Please select one of these options.",
       :serbian "Молим одаберите једну од ових опција." }
     { :code 51,
       :english "Please select an item in the list.",
       :serbian "Молим одаберите ставку у листи." }
     { :code 52,
       :english "Please enter an email address.",
       :serbian "Молим унесите адресу е-поште." }
     { :code 53,
       :english "Please use at least $0 characters (you are currently using $1 characters).",
       :serbian "Молим искористите бар $0 карактера (тренутно користите $1 карактера)." }
     { :code 54,
       :english "Please select a valid value. The two nearest valid values are $0 and $1.",
       :serbian "Молим одаберите исправну вредност. Две најближе исправне вредности су $0 и $1." }
     { :code 55,
       :english "Please select a value that is no less than $0.",
       :serbian "Молим одаберите вредност која није мања од $0." }
     { :code 56,
       :english "Please select a value that is no more than $0.",
       :serbian "Молим одаберите вредност која није већа од $0." }
     { :code 57,
       :english "Please match the requested format.",
       :serbian "Молим држите се захтеваног формата." }
     { :code 58,
       :english "Given email doesn't exist.",
       :serbian "Прослеђена адреса е-поште не постоји." }
     { :code 59,
       :english "Incorrect password for given email.",
       :serbian "Нетачна лозинка за прослеђену адресу е-поште." }
     { :code 60,
       :english "Confirm password does not match with password.",
       :serbian "Потврдна лозинка се не поклапа са лозинком." }
     { :code 61,
       :english "Username and/or email already exists.",
       :serbian "Корисничко име и/или адреса е-поште већ постоји." }
     { :code 62,
       :english "Sample App",
       :serbian "Sample App" }
     { :code 63,
       :english "Sample App is prototype project which can be used to quickly get head start in building your Web Apps.",
       :serbian "Sample App је прототип пројекат који може да се искористи за брзи почетак у изградњи ваше веб апликације." }
     { :code 64,
       :english "Password must contain at least one uppercase letter, one lowercase letter, one number and one special character @$!%*?&..",
       :serbian "Лозинка мора да садржи барем једно велико слово, једно мало слово, један број и један специјални карактер @$!%*?&.." }
     { :code 65,
       :english "Please select a file.",
       :serbian "Молим одаберите фајл." }
     { :code 66 :english "Type your message here" :serbian "Упишите вашу поруку овде" }
     { :code 67 :english "Send" :serbian "Пошаљи" }
     { :code 68 :english "Chat" :serbian "Ћаскање" }
		   { :code 69 :english "Refresh" :serbian "Освежи" }
     ]))

