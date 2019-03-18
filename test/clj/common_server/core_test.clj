(ns common-server.core-test
  (:require [clojure.test :refer :all]
            [common-server.core :refer :all]))

(deftest test-print-request
  (testing "Test print request"
    
    (is
      (= (print-request
           {})
         {}))
    
    (is
      (= (print-request
           {:body "test"
            :content-length "4"})
         {:body "test"
          :content-length "4"}))
    
    (is
      (= (print-request
           {:body "test"
            :content-length "300"})
         {:content-length "300"}))
    
    (is
      (= (print-request
           {:body "test"})
         {:body "test"}))
    
    (is
      (= (print-request
           {:content-length "1"})
         {:content-length "1"}))
    
    (is
      (= (print-request
           {:body "test123456test123456test123456test123456test123456test123456test123456test123456test123456  test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456"})
         {}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 4
                        :websocket-message "Test"}})
         {:websocket {:websocket-message-length 4
                      :websocket-message "Test"}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 300
                        :websocket-message "Test"}})
         {:websocket {:websocket-message-length 300}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message "Test"}})
         {:websocket {:websocket-message "Test"}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message "test123456test123456test123456test123456test123456test123456test123456test123456test123456  test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456test123456test123456test123456test123456test123456test123456 test123456test123456test123456"}})
         {:websocket {}}))
    
    (is
      (= (print-request
           {:websocket {:websocket-message-length 4}})
         {:websocket {:websocket-message-length 4}}))

   ))

