; whenever a clj file in the current directory (or child directories) is
; modified this will automatically kill the process and create a new one.

;TODO: modify the inputs to the form: directory to monitor, 
; file in that directory to load

(ns autoreload
  (:use clojure.set
	(clojure.contrib [duck-streams :only (reader writer)]))
  (:import
   (java.lang.management ManagementFactory)
   (java.io File)))

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

(defn list-all-clj [#^String dir]
  (for [f (filter #(and (.isFile %) (.endsWith (.. % getName toLowerCase) ".clj")) (file-seq (File. dir)))]
    {:file (.getPath f) :last-modified (.lastModified f)}))

(def cmdline (get-cmdline))

(defn file-watching-thread []
  (loop [files (set (list-all-clj "."))]
    (let [new-files (set (list-all-clj "."))]
      (if (empty? (difference files new-files))
	(do
	  ;(println "nothing has changed!")
	  (Thread/sleep 1000)
	  (recur files))
	(do
	  (println "reloading due to file changes:" (difference files new-files))
	  (System/exit 3))))))

(defn stream-reader [prefix stream]
  (with-open [rdr (reader stream)]
    (doseq [l (line-seq rdr)] (println (str prefix l)))))

(defn run []
  (if (nil? (System/getenv "AUTORELOADER"))
    (loop []
      (let [p (.exec (Runtime/getRuntime) cmdline
		     (into-array String (cons "AUTORELOADER=TRUE" (for [i (System/getenv)] (str (.getKey i) "=" (.getValue i))))))]
	(.start (Thread. (fn [] (stream-reader "std>>> " (.getInputStream p)))))
	(.start (Thread. (fn [] (stream-reader "err>>> " (.getErrorStream p)))))
	; fixes issue where ctrl-c leaves the server process running
	(.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.destroy p))))
	(if (= 3 (.waitFor p))
	  (recur)
	  nil)))
    (let [fwt (.start (Thread. file-watching-thread))]
      (load-file 
       (loop [args *command-line-args*]
	 (if (empty? args)
	   (throw (Exception. "Unable to find .clj file in command args"))
	   (if (.endsWith (first args) ".clj")
	     (first args)
	     (recur args))))))))

(run)
