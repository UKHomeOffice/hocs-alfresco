package uk.gov.homeoffice.cts.storage;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.ContentStoreCreatedEvent;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.GUID;
import org.alfresco.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Map;

/**
 * A s3 content store
 *
 * @author Marcus Svartmark - Redpill Linpro
 */
public class S3ContentStore extends AbstractContentStore
        implements ApplicationContextAware, ApplicationListener<ApplicationEvent>, InitializingBean {

    private static final Log LOG = LogFactory.getLog(S3ContentStore.class);
    private ApplicationContext applicationContext;

    private AmazonS3 s3Client;
    private TransferManager transferManager;

    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String regionName;
    private String rootDirectory;
    private String endpoint;
    private String signatureVersion;
    private int connectionTimeout = 50000;
    private int maxErrorRetry = 5;
    private long connectionTTL = 60000L;
    private long multipartUploadThreshold = 16777216L;

    /**
     * @see
     * com.amazonaws.services.s3.transfer.TransferManagerConfiguration#multipartUploadThreshold
     * @param multipartUploadThreshold The multipart upload threshold
     */
    public void setMultipartUploadThreshold(long multipartUploadThreshold) {
        this.multipartUploadThreshold = multipartUploadThreshold;
    }

    /**
     * @see com.amazonaws.ClientConfiguration#setConnectionTTL(long)
     * @param connectionTTL set TTL for connection
     */
    public void setConnectionTTL(long connectionTTL) {
        this.connectionTTL = connectionTTL;
    }

    /**
     * @see com.amazonaws.ClientConfiguration#setMaxErrorRetry(int)
     * @param maxErrorRetry set max retries
     */
    public void setMaxErrorRetry(int maxErrorRetry) {
        this.maxErrorRetry = maxErrorRetry;
    }

    /**
     * @see com.amazonaws.ClientConfiguration#setConnectionTimeout(int)
     * @param connectionTimeout set connection timeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public boolean isWriteSupported() {
        return true;
    }

    @Override
    public ContentReader getReader(String contentUrl) {

        String key = makeS3Key(contentUrl);
        return new S3ContentReader(key, contentUrl, s3Client, bucketName);

    }

    /**
     * Get the currently used s3 client
     *
     * @return Returns a s3 client
     */
    public AmazonS3 getS3Client() {
        return s3Client;
    }

    /**
     * Initialize the content store
     */
    public void init() {
        AmazonS3ClientBuilder s3builder = AmazonS3ClientBuilder
                .standard()
                .withRegion(regionName)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withClientConfiguration(new ClientConfiguration());

        s3Client = s3builder.enablePathStyleAccess().disableChunkedEncoding().build();

        transferManager = TransferManagerBuilder
                .standard()
                .withS3Client(s3Client)
                .withMultipartUploadThreshold(multipartUploadThreshold)
                .build();
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setSignatureVersion(String signatureVersion) {
        this.signatureVersion = signatureVersion;
    }

    public void setRootDirectory(String rootDirectory) {

        String dir = rootDirectory;
        if (dir.startsWith("/")) {
            dir = dir.substring(1);
        }

        this.rootDirectory = dir;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    protected ContentWriter getWriterInternal(ContentReader existingContentReader, String newContentUrl) {

        String contentUrl = newContentUrl;

        if (StringUtils.isBlank(contentUrl)) {
            contentUrl = createNewUrl();
        }

        String key = makeS3Key(contentUrl);

        return new S3ContentWriter(bucketName, key, contentUrl, existingContentReader, s3Client, transferManager);

    }

    /**
     * Create a hashed URL for storage
     *
     * @return Returns a string containing the URL for storage
     */
    public static String createNewUrl() {

        Calendar calendar = new GregorianCalendar();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;  // 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        // create the URL
        StringBuilder sb = new StringBuilder(20);
        sb.append(FileContentStore.STORE_PROTOCOL)
                .append(ContentStore.PROTOCOL_DELIMITER)
                .append(year).append('/')
                .append(month).append('/')
                .append(day).append('/')
                .append(hour).append('/')
                .append(minute).append('/')
                .append(GUID.generate()).append(".bin");
        String newContentUrl = sb.toString();
        // done
        return newContentUrl;

    }

    private String makeS3Key(String contentUrl) {
        // take just the part after the protocol
        Pair<String, String> urlParts = super.getContentUrlParts(contentUrl);
        String protocol = urlParts.getFirst();
        String relativePath = urlParts.getSecond();
        // Check the protocol
        if (!protocol.equals(FileContentStore.STORE_PROTOCOL)) {
            throw new UnsupportedContentUrlException(this, protocol + PROTOCOL_DELIMITER + relativePath);
        }

        return rootDirectory + "/" + relativePath;

    }

    @Override
    public boolean delete(String contentUrl) {

        try {
            String key = makeS3Key(contentUrl);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Deleting object from S3 with url: " + contentUrl + ", key: " + key);
            }
            s3Client.deleteObject(bucketName, key);
            return true;
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Error deleting S3 Object", e);
            }
        }
        return false;
    }

    /**
     * Publishes an event to the application context that will notify any
     * interested parties of the existence of this content store.
     *
     * @param context the application context
     * @param extendedEventParams
     */
    private void publishEvent(ApplicationContext context, Map<String, Serializable> extendedEventParams) {
        context.publishEvent(new ContentStoreCreatedEvent(this, extendedEventParams));
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // Once the context has been refreshed, we tell other interested beans about the existence of this content store
        // (e.g. for monitoring purposes)
        if (event instanceof ContextRefreshedEvent && event.getSource() == this.applicationContext) {
            publishEvent(((ContextRefreshedEvent) event).getApplicationContext(), Collections.<String, Serializable>emptyMap());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Do nothing
    }
}