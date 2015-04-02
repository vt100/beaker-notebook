(ns bunsen.provisioner.lifecycle.docker
  (:require [bunsen.provisioner.helper.docker :as docker]
            [bunsen.provisioner.lifecycle :refer [Lifecycle lifecycle] :as lifecycle]))

(defmethod lifecycle :docker [{:keys [docker-url container-prefix
                                      container-port container-image
                                      container-path scratch-space-root]}]
  (reify Lifecycle

    (lifecycle/inspect [_ id]
      (when (docker/get-container docker-url (str container-prefix "_" id))
        {:id id}))

    (lifecycle/create! [_ {:keys [id token]}]
      (let [container-id (str container-prefix "_" id)
            host-path (str scratch-space-root "/" id)
            existed? (docker/get-container
                       docker-url
                       container-id)
            created? (when-not existed?
                       (docker/create-container
                         docker-url
                         (docker/container
                           {:id container-id
                            :token token
                            :port container-port
                            :image container-image
                            :host-path host-path
                            :container-path container-path})))]
        (when created?
          (docker/start-container
            docker-url
            container-id))
        (when (or existed? created?)
          {:id id})))))
