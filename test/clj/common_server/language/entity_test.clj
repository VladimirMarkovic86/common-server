(ns common-server.language.entity-test
  (:require [clojure.test :refer :all]
            [common-server.language.entity :refer :all]))

(deftest test-format-code-field
  (testing "Test format code field"
    
    (let [raw-code nil
          chosen-language nil
          result (format-code-field
                   raw-code
                   chosen-language)]
      
      (is
        (nil?
          result)
       )
      
     )
    
    (let [raw-code "Test"
          chosen-language nil
          result (format-code-field
                   raw-code
                   chosen-language)]
      
      (is
        (nil?
          result)
       )
      
     )
    
    (let [raw-code 123
          chosen-language nil
          result (format-code-field
                   raw-code
                   chosen-language)]
      
      (is
        (= result
           123)
       )
      
     )
    
    (let [raw-code 123.123
          chosen-language nil
          result (format-code-field
                   raw-code
                   chosen-language)]
      
      (is
        (= result
           123)
       )
      
     )
    
   ))

