#!/bin/bash
#
JAVA_OPTS="-Djava.library.path=/usr/lib/jni" 
JAVA_OPTS="$JAVA_OPTS -Dalfresco.home=/usr/local/alfresco"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote"
JAVA_OPTS="$JAVA_OPTS -XX:ReservedCodeCacheSize=1024m"
JAVA_OPTS="$JAVA_OPTS -Xms4096m -Xmx17500m -XX:+UseG1GC -Djavax.net.ssl.trustStore=/data/truststore.jks" # java-memory-settings

export JAVA_OPTS