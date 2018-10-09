#!/bin/bash

sed -i 's/${alfresco.s3.accesskey}/'"$ALF_S3_ACCESSKEY"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's@${alfresco.s3.secretkey}@'"$ALF_S3_SECRETKEY"'@' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's@${alfresco.s3.ssekey}@'"$ALF_S3_SSEKEY"'@' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.s3.bucketname}/'"$ALF_S3_BUCKETNAME"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.s3.hostname}/'"$ALF_S3_HOSTNAME"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.db.username}/'"$ALF_DB_USERNAME"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.db.password}/'"$ALF_DB_PASSWORD"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.db.host}/'"$ALF_DB_HOST"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.db.port}/'"$ALF_DB_PORT"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${alfresco.db.name}/'"$ALF_DB_NAME"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${admin.initial.password}/'"$ALF_ADMIN_INITIAL_PASSWORD"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${reporting.url}/'"$ALF_REPORTING_ENDPOINT"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${notify.apiKey}/'"$ALF_NOTIFY_APIKEY"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${notify.workFlowEmailTemplateId}/'"$ALF_NOTIFY_WF_TEMPLATE_ID"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${notify.resetPWTemplateId}/'"$ALF_NOTIFY_RESET_PW_TEMPLATE_ID"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${notify.bulkUserImportTemplateId}/'"$ALF_NOTIFY_BULK_USER_IMPORT_TEMPLATE_ID"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties
sed -i 's/${notify.caseCreatedEmailTemplateId}/'"$ALF_NOTIFY_CREATE_CASE_EMAIL_TEMPLATE_ID"'/' /usr/local/tomcat/shared/classes/alfresco-global.properties

exec "$@"