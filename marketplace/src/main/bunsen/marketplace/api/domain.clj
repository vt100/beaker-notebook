(ns bunsen.marketplace.api.domain
  (:require [bunsen.marketplace.base :as base]
            [bunsen.marketplace.categories :as cats]
            [bunsen.marketplace.datasets :as datasets]
            [bunsen.marketplace.mappings :as mappings]
            [bunsen.marketplace.simple.simple :as simple]
            [bunsen.marketplace.helper.api :as helper]
            [clojurewerkz.elastisch.rest.index :as ind]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clojurewerkz.elastisch.rest.response :refer :all]
            [clojurewerkz.elastisch.query :as query]))

(defn update-marketplace
  "Performs some common pre-processing tasks before kicking off the
  specified marketplace work.
  config = application config instance
  body = string representation of request body
  biz-fn = business task that we intend to perform"
  [config body biz-fn]
  (let [es-conn (helper/connect-to-es config)
        index-name (:indexName body)]
    (biz-fn es-conn index-name body)))

(defn get-status [ctx] "ok")

(defn get-formats
  [config]
  (helper/aggregate-term "format" (helper/connect-to-es config)))

(defn get-tags
  [config]
  (helper/aggregate-term "tags" (helper/connect-to-es config)))

(defn get-vendors
  [config]
  (helper/aggregate-term "vendor" (helper/connect-to-es config)))

(defn create-datasets
  "Returns true if datasets payload was succesfully sent to
  ElasticSearch, false otherwise."
  [es-conn index-name payload]
  (let [datasets (:datasets payload)
        categories (base/read-indexed-results es-conn index-name "categories")
        indexer (base/index! es-conn index-name "datasets" datasets
                             identity ; json already parsed
                             #(map (partial simple/prepare-dataset categories) %)
                             base/bulk-to-es!)]
    (await-for 5000 indexer)
    (= (:stage @indexer) :indexed)))

(defn create-dataset
  "Creates a single dataset based on the index-name provided"
  [config index-name document]
  (let [connection (helper/connect-to-es config)
        created_id (:_id (doc/create connection index-name "datasets" document))]
        ; set the ID attribute of a dataset to be the internal elastic search _id
        ; since the api consumers expect their to be an ID attribute on each dataset.
        (doc/update-with-partial-doc connection index-name "datasets" created_id {:id created_id})))

(defn delete-dataset
  [config index-name id]
  (-> (helper/connect-to-es config) (doc/delete index-name "datasets" id)))

(defn update-dataset
  "Updates dataset with given payload"
  [config index-name id document]
  (-> (helper/connect-to-es config) (doc/put index-name "datasets" id document)))

(defn update-counts
  [es-conn index-name payload]
  (let [categories (base/read-indexed-results es-conn index-name "categories")]
    (cats/update-counts! es-conn index-name categories)))

(defn update-mappings
  "Updates the ElasticSearch mappings necessary for the index's catalog
  metadata"
  [es-conn index-name payload]
  (let [categories (base/read-indexed-results es-conn index-name "categories")]
    (cats/update-mappings! es-conn index-name categories)))

(defn refresh-index
  [es-conn index-name payload]
  (ind/refresh es-conn index-name))

(defn get-indicies
  [config _]
  (let [categories (-> (doc/search (helper/connect-to-es config) "*" "categories"
                                   :query (query/filtered :filter {:regexp {:path {:value ".{0,3}"}}}))
                       :hits :hits)]
    (map (fn [m] {:index (:_index m) :name (-> m :_source :name)}) categories)))

(defn create-index
  [es-conn index-name payload]
  (ind/delete es-conn index-name)
  (ind/create es-conn index-name)
  (mappings/apply-mappings! es-conn index-name "seed/mappings.json"))
