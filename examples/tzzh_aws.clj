(require '[babashka.pods :as pods])
(pods/load-pod 'tzzh/aws "0.0.3")
(require '[pod.tzzh.dynamodb :as d])
(require '[pod.tzzh.s3 :as s3])
(require '[pod.tzzh.paginator :as p])


(d/list-tables)

(d/batch-get-item {:RequestItems
                    {"AmazingTable" {:Keys [{:some-property {:S "SomeValue"} 
                                             :something-else {:S "SomethingSomething"}}]}}})

(d/batch-write-item {:RequestItems
                    {"AmazingTable" [{:PutRequest {:Item {:some-property {:S "abxdggje"}
                                                          :something-else {:S "zxcmbnj"}
                                                          :another-thing {:S "asdasdsa"}}}}]}})

(d/get-item {:Key {:lalala {:S "zzzzzzzz"}
                   :bbbbbb {:S "abxbxbxx"}}
             :TableName "SomeTable"})

(d/describe-table {:TableName "SomeTable"})

(s3/list-buckets)

;; Paginators example
(let [s3-paginator (p/get-paginator s3/list-objects-v2-pages)]
    (s3-paginator {:Bucket "some-bucket"
                   :Prefix "some-prefix/something/"}))
;; this returns a list of all the pages i.e a list of ListObjectsV2Output that are lazily fetched

(let [glue-paginator (p/get-paginator g/list-crawlers)]
         (glue-paginator))
