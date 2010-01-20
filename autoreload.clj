; whenever a clj file in the current directory (or child directories) is
; modified this will automatically kill the process and create a new one.

;TODO: modify the inputs to the form: directory to monitor, 
; file in that directory to load

(ns autoreload
  (:use clojure.set
	(clojure.contrib [duck-streams :only (reader)]))
  (:require process)
  (:import
   (java.io File)))

(defn list-all-clj [#^String dir]
  (for [f (filter #(and (.isFile %) (.endsWith (.. % getName toLowerCase) ".clj")) (file-seq (File. dir)))]
    {:file (.getPath f) :last-modified (.lastModified f)}))

(def cmdline
     (process/get-cmdline))

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
