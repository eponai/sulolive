FROM java:8

ADD target/uberjar/budget-0.1.0-SNAPSHOT-standalone.jar /srv/production.jar

EXPOSE 8080

WORKDIR "/srv"
CMD ["java", "-server", "-Xmx700m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50", "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-Dclojure.compiler.direct-linking=true", "-cp", "production.jar", "eponai.server.core"]

