#!/bin/bash
#
JAVA_OPTS="-Djava.library.path=/usr/lib/jni" 
JAVA_OPTS="$JAVA_OPTS -Dalfresco.home=/usr/local/alfresco"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote"
JAVA_OPTS="$JAVA_OPTS -XX:ReservedCodeCacheSize=1024m"
JAVA_OPTS="$JAVA_OPTS -Xms1024m -Xmx8500m -XX:MaxPermSize=1536M -Djavax.net.ssl.trustStore=/data/truststore.jks" # java-memory-settings

# service:jmx:rmi:///jndi/rmi://192.168.1.33:9999/jmxrmi
#JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.rmi.port=9999"
#JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1"

export JAVA_OPTS
