(loop []
  (Thread/sleep 1000)
  (println "ping...")
  (recur))
