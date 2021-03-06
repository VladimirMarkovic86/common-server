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
		   { :code 70 :english "Error" :serbian "Грешка" }
		   { :code 71
		     :english "Username or e-mail alredy exists."
		     :serbian "Корисничко име или е-пошта већ постоји." }
		   { :code 72 :english "Table of entity " :serbian "Табела ентитета " }
		   { :code 73 :english "All pages" :serbian "Све странице" }
		   { :code 74 :english "Page " :serbian "Страница " }
		   { :code 75	:english "Details of entity " :serbian	"Детаљи ентитета " }
		   { :code 76	:english "Forgot password?" :serbian	"Заборавили сте лозинку?" }
		   { :code 77	:english "Reset password code" :serbian	"Код за промену лозинке" }
		   { :code 78	:english "Confirm" :serbian	"Потврди" }
		   { :code 79
		     :english "Reset password code expired or is incorrect"
		     :serbian	"Код за промену лозинке је истекао или није исправан" }
		   { :code 80	:english "New password" :serbian	"Нова лозинка" }
		   { :code 81	:english "Sample App reset password" :serbian	"Sample App промена лозинке" }
		   { :code 82
		     :english "A password reset was requested for Sample App account with this email address. <br> To continue password reset copy, paste and confirm code from below."
       :serbian	"Налог апликације Sample App са овом е-адресом захтева промену лозинке. <br> Да би наставили промену лозинке копирајте, налепите и потврдите следећи код." }
		   { :code 83	:english "Preferences" :serbian	"Подешавања" }
		   { :code 84	:english "Display" :serbian	"Приказ" }
		   { :code 85	:english "Language entity" :serbian	"Ентитет језик" }
		   { :code 86	:english "User entity" :serbian	"Ентитет корисник" }
		   { :code 87	:english "Role entity" :serbian	"Ентитет улога" }
		   { :code 88	:english "Make a call" :serbian	"Позови" }
		   { :code 89	:english "Calling . . ." :serbian	"Позивање . . ." }
		   { :code 90	:english "Answering . . ." :serbian	"Јављање . . ." }
		   { :code 91	:english "Connected." :serbian	"Повезан." }
		   { :code 92 :english "Calendar" :serbian "Календар" }
		   { :code 93 :english "January" :serbian "Јануар" }
		   { :code 94 :english "February" :serbian "Фебруар" }
		   { :code 95 :english "March" :serbian "Март" }
		   { :code 96 :english "April" :serbian "Април" }
		   { :code 97 :english "May" :serbian "Мај" }
		   { :code 98 :english "Jun" :serbian "Јун" }
		   { :code 99 :english "July" :serbian "Јул" }
		   { :code 100 :english "August" :serbian "Август" }
		   { :code 101 :english "September" :serbian "Септембар" }
		   { :code 102 :english "October" :serbian "Окробар" }
		   { :code 103 :english "November" :serbian "Новембар" }
		   { :code 104 :english "December" :serbian "Децембар" }
		   { :code 105 :english "Monday" :serbian "Понедељак" }
		   { :code 106 :english "Tuesday" :serbian "Уторак" }
		   { :code 107 :english "Wednesday" :serbian "Среда" }
		   { :code 108 :english "Thursday" :serbian "Четвртак" }
		   { :code 109 :english "Friday" :serbian "Петак" }
		   { :code 110 :english "Saturday" :serbian "Субота" }
		   { :code 111 :english "Sunday" :serbian "Недеља" }
		   { :code 112 :english "Create item" :serbian "Креирај ставку" }
		   { :code 113 :english "Start date" :serbian "Почетни датум" }
		   { :code 114 :english "End date" :serbian "Крајњи датум" }
		   { :code 115 :english "Duration" :serbian "Трајање" }
		   { :code 116 :english "Type" :serbian "Тип" }
		   { :code 117 :english "Start time" :serbian "Почетно време" }
		   { :code 118 :english "End time" :serbian "Крајње време" }
		   { :code 119
		     :english "Start date is after end date."
		     :serbian "Почетни датум је након крајњег датума." }
		   { :code 120 :english "Description" :serbian "Опис" }
		   { :code 121
		     :english "Item's date and time is already taken."
		     :serbian "Датум и време ставке је заузето." }
     { :code 122 :english "Name" :serbian "Назив" }
     ]))

