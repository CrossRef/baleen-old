(ns baleen.util-test
  (:require [clojure.test :refer :all]
            [baleen.util :refer :all]))

(deftest doi-extraction
  (testing "Can extract DOIs from commonly found URL formats"
    (is (= (is-doi-url? "http://dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (is-doi-url? "//dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (is-doi-url? "http://doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (is-doi-url? "http://dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (is-doi-url? "//doi.org/10.5555/12345678") "10.5555/12345678"))))


(deftest test-remove-all
  (is (= "one  three  five" (remove-all "one two three four five" ["two" "four"]))))