(ns baleen.gnip-test
  (:require [clojure.test :refer :all]
            [baleen.sources.gnip-dois :refer :all]))

; Taken from http://support.gnip.com/sources/twitter/data_format.html#SamplePayloads

(deftest extract-info-tweet
  (testing "All info can be extracted from GNIP tweet"
    (let [result (extract-info (slurp "resources/test/gnip-tweet.json"))]
      (is (= (:tweet-id result) "tag:search.twitter.com,2005:593895901623496704"))
      (is (= (:body result) "This is a #test tweet @LoveforTestingT with an image. http://t.co/ZvgHovKZq4"))
      (is (= (:verb result) "post"))
      ; All mentioned URLs for later matching.
      (is (= (:urls result) #{"http://t.co/ZvgHovKZq4", "http://twitter.com/johnd_test/status/593895901623496704/photo/1"})))))

(deftest extract-info-retweet
  (testing "All info can be extracted from GNIP retweet"
    (let [result (extract-info (slurp "resources/test/gnip-retweet.json"))]
      (is (= (:tweet-id result) "tag:search.twitter.com,2005:595648617588912128"))
      (is (= (:body result) "RT @stevedz: More geo testing. Dinner with my son and  a Petrus 50/50  http://t.co/NtoHc3cOY4 from @BrouwerijDB is just a bonus! http://t.c…"))
      (is (= (:verb result) "share"))
      ; All mentioned URLs for later matching.
      (is (= (:urls result) #{"http://t.co/NtoHc3cOY4" "http://www.brouwerijdebrabandere.be/en/merken/petrus" "http://t.co/nHPh4YMdhN" "http://twitter.com/stevedz/status/593935592573898753/photo/1"})))))

(deftest extract-info-quote-tweet
  (testing "All info can be extracted from GNIP quote tweet"
    (let [result (extract-info (slurp "resources/test/gnip-quote-tweet.json"))]
      (is (= (:tweet-id result) "tag:search.twitter.com,2005:600699303225466880"))
      (is (= (:body result) "Check it out here @johnd  https://t.co/F4H2ikacfs"))
      (is (= (:verb result) "post"))
      ; All mentioned URLs for later matching.
      (is (= (:urls result) #{"http://test.gnip.com/mock" "https://t.co/F4H2ikacfs"})))))