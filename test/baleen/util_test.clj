(ns baleen.util-test
  (:require [clojure.test :refer :all]
            [baleen.util :refer :all]))

(deftest a-test
  (testing "Can extract DOIs from commonly found URL formats"
    (is (= (extract-doi-from-url "http://dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (extract-doi-from-url "//dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (extract-doi-from-url "http://doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (extract-doi-from-url "http://dx.doi.org/10.5555/12345678") "10.5555/12345678"))
    (is (= (extract-doi-from-url "//doi.org/10.5555/12345678") "10.5555/12345678"))))
