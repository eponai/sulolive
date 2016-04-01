(ns env.client.utils)

(defmacro dev-machine-ip
  "Is replaced with the ip to this computer."
  []
  (.getHostAddress (java.net.InetAddress/getLocalHost)))