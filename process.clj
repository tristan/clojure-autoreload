(ns process
  (:import (java.lang.management ManagementFactory))
  (:use (clojure.contrib [duck-streams :only (reader writer)])))

(defn get-cmdline []
  (let [pid (last (first (re-seq #"([0-9]+)@" (.. ManagementFactory getRuntimeMXBean getName))))]
    (cond (re-find #"Windows" (System/getProperty "os.name"))
	  (let [p (.exec (Runtime/getRuntime) (str "wmic PROCESS where processid=" pid " get Commandline /value")
			 (into-array String (for [i (System/getenv)] (str (.getKey i) "=" (.getValue i)))))]
	    (let [w (writer (.getOutputStream p))]
	      (.println w "testing")
	      (.close w))
	    (let [r (with-open [rdr (reader (.getInputStream p))]
		      (apply #'str (for [l (line-seq rdr)] l)))] ; have to run str on all so we don't return a lazy-seq.
	      (.waitFor p)
	      (last (first (re-seq #"CommandLine=([\s\S]+)" r)))))
	  ;(re-find #"Unix|Linux" (System/getProperty "os.name")) ; only tested on ubuntu!
	  :else ; attempt to use unix type
	  (try
	   (let [p (.exec (Runtime/getRuntime) (str "ps -p " pid " w -o command --no-headers")
			  (into-array String (for [i (System/getenv)] (str (.getKey i) "=" (.getValue i)))))]
	     (let [r (with-open [rdr (reader (.getInputStream p))]
		       (apply #'str (for [l (line-seq rdr)] l)))]
	       (.waitFor p)
	       r))
	   (catch java.io.IOException e (throw (Exception. "unable to determine operating system to find command")))))))
	 
	  