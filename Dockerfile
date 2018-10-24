FROM quay.io/ukhomeofficedigital/openjdk8

ENV USER alfresco
ENV USER_ID 1000
ENV GROUP alfresco

RUN yum install -y unzip sed zip tar mysql libreoffice-headless libreoffice-draw libreoffice-impress libreoffice-writer libreoffice-calc \
    && yum clean all

ENV ALF_DOWNLOAD_URL https://download.alfresco.com/release/community/4.2.f-build-00012/alfresco-community-4.2.f.zip
ENV TOMCAT_TGZ_URL=http://archive.apache.org/dist/tomcat/tomcat-7/v7.0.82/bin/apache-tomcat-7.0.82.tar.gz

ENV ALF_HOME /usr/local/alfresco
ENV CATALINA_HOME /usr/local/tomcat

ENV DIST /tmp/alfresco
ENV PATH $CATALINA_HOME/bin:$ALF_HOME/bin:$PATH

RUN set -x \
	&& mkdir -p $CATALINA_HOME \
	&& curl -fSL "$TOMCAT_TGZ_URL" -o tomcat.tar.gz \
	&& curl -fSL "$TOMCAT_TGZ_URL.asc" -o tomcat.tar.gz.asc \
	&& tar -xvf tomcat.tar.gz --strip-components=1 -C $CATALINA_HOME \
	&& rm -f tomcat.tar.gz*


RUN set -x \
	&& mkdir -p $ALF_HOME \
	&& mkdir -p $DIST \
	&& curl -fSL "$ALF_DOWNLOAD_URL" -o alfresco-community-4.2.f.zip \
	&& unzip alfresco-community-4.2.f.zip -d /tmp/alfresco \
	&& rm -f alfresco-community-4.2.f.zip

WORKDIR $ALF_HOME

RUN set -x \
    	&& ln -s /usr/local/tomcat /usr/local/alfresco/tomcat \
        && mkdir -p $CATALINA_HOME/conf/Catalina/localhost \
        && mv $DIST/web-server/shared tomcat/ \
        && mv $DIST/web-server/lib/*.jar tomcat/lib/ \
        && mv $DIST/web-server/webapps/alfresco.war tomcat/webapps/ \
        && mv $DIST/bin . \
        && mv $DIST/licenses . \
        && mv $DIST/README.txt . \
        && rm -rf $CATALINA_HOME/webapps/docs \
        && rm -rf $CATALINA_HOME/webapps/examples \
        && mkdir $CATALINA_HOME/shared/lib $ALF_HOME/amps_share \
        && rm -rf $DIST

RUN zip -d tomcat/webapps/alfresco.war "WEB-INF/lib/httpclient-4.1.1.jar" \
 && zip -d tomcat/webapps/alfresco.war "WEB-INF/lib/httpclient-cache-4.1.1.jar" \
 && zip -d tomcat/webapps/alfresco.war "WEB-INF/lib/httpcore-4.1.3.jar"

COPY assets/tomcat/lib/*.jar tomcat/lib/
COPY assets/tomcat/catalina.properties tomcat/conf/catalina.properties
COPY assets/tomcat/setenv.sh tomcat/bin/setenv.sh
COPY assets/tomcat/server.xml tomcat/conf/server.xml
COPY assets/tomcat/context.xml tomcat/conf/context.xml
COPY assets/tomcat/catalina.policy tomcat/conf/catalina.policy
COPY assets/tomcat/tomcat-users.xml tomcat/conf/tomcat-users.xml
COPY assets/tomcat/logging.properties tomcat/conf/logging.properties
COPY assets/tomcat/web.xml tomcat/conf/web.xml

COPY assets/alfresco/alfresco-global.properties tomcat/shared/classes/alfresco-global.properties
COPY assets/alfresco/log4j.properties tomcat/shared/classes/alfresco/extension/log4j.properties
COPY assets/alfresco/caching-content-store-context.xml tomcat/shared/classes/alfresco/extension/caching-content-store-context.xml
COPY assets/alfresco/share-config-custom.xml tomcat/shared/classes/alfresco/web-extension/share-config-custom.xml

# AMPS installation
COPY homeoffice-cts-repo/target/homeoffice-cts-repo.amp amps/homeoffice-cts-repo.amp
COPY assets/alfresco/entrypoint.sh entrypoint.sh
RUN bash ./bin/apply_amps.sh -force -nobackup && chmod +x  entrypoint.sh

RUN groupadd -r ${GROUP} && \
    useradd -r -u ${USER_ID} -g ${GROUP} ${USER} -d $ALF_HOME  && \
    chown -R ${USER}:${GROUP} $ALF_HOME && \
    chown -R ${USER}:${GROUP} /usr/local/tomcat && \
    chown -R ${USER}:${GROUP} /usr/local/alfresco/

ENTRYPOINT ["./entrypoint.sh"]
EXPOSE 8080

USER ${USER_ID}

CMD ["catalina.sh", "run"]
