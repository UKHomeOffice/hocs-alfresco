package uk.gov.homeoffice.cts.storage;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.alfresco.repo.content.AbstractContentReader;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * S3 Content Reader
 *
 * @author Marcus Svartmark
 */
public class S3ContentReader extends AbstractContentReader implements AutoCloseable {

	private static final Log LOG = LogFactory.getLog(S3ContentReader.class);

	private final String key;
	private final AmazonS3 s3Client;
	private final String bucket;
	private S3Object s3Object;
	private ObjectMetadata s3ObjectMetadata;

	/**
	 * @param key the key to use when looking up data
	 * @param s3Client the s3 client to use for the connection
	 * @param contentUrl the content URL - this should be relative to the root of
	 * the store
	 * @param bucket the s3 bucket name
	 */
	protected S3ContentReader(String key, String contentUrl, AmazonS3 s3Client, String bucket) {
		super(contentUrl);
		this.key = key;
		this.s3Client = s3Client;
		this.bucket = bucket;
		//Do not initialize the s3 object on reader init. Use lazy initalization
	}

	/**
	 * Close file object
	 *
	 * @throws IOException Throws exception on error
	 */
	protected void closeFileObject() throws IOException {
		if (s3Object != null) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("Closing s3 file object for reader " + key);
			}
			s3Object.close();
			s3Object = null;
		}
	}

	/**
	 * Lazy initialize the file object
	 */
	protected void lazyInitFileObject() {
		if (s3Object == null) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("Lazy init for file object for " + bucket + " - " + key);
			}
			this.s3Object = getObject();
		}
	}

	/**
	 * Lazy initialize the file metadata
	 */
	protected void lazyInitFileMetadata() {
		if (s3ObjectMetadata == null) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("Lazy init for file metadata for " + bucket + " - " + key);
			}

			if (s3Object != null) {
				s3ObjectMetadata = s3Object.getObjectMetadata();
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("GETTING OBJECT METADATA - BUCKET: " + bucket + " KEY: " + key);
				}
				s3ObjectMetadata = s3Client.getObjectMetadata(bucket, key);
			}
		}
	}

	@Override
	protected ContentReader createReader() throws ContentIOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Called createReader for contentUrl -> " + getContentUrl() + ", Key: " + key);
		}
		return new S3ContentReader(key, getContentUrl(), s3Client, bucket);
	}

	@Override
	protected ReadableByteChannel getDirectReadableChannel() throws ContentIOException {
		lazyInitFileObject();
		if (!exists()) {
			throw new ContentIOException("Content object does not exist on S3");
		}

		try {
			//We need to close the s3 object so to ensure that the thread pools are updated
			ContentStreamListener s3StreamListener = new ContentStreamListener() {
				@Override
				public void contentStreamClosed() throws ContentIOException {
					try {
						LOG.trace("Closing s3 object stream on content stream closed.");
						closeFileObject();
					} catch (IOException e) {
						throw new ContentIOException("Failed to close underlying s3 object", e);
					}
				}
			};
			this.addListener(s3StreamListener);
			return Channels.newChannel(s3Object.getObjectContent());
		} catch (Exception e) {
			throw new ContentIOException("Unable to retrieve content object from S3", e);
		}

	}

	@Override
	public boolean exists() {
		lazyInitFileMetadata();
		return s3ObjectMetadata != null;
	}

	@Override
	public long getLastModified() {
		lazyInitFileMetadata();
		if (!exists()) {
			return 0L;
		}

		return s3ObjectMetadata.getLastModified().getTime();

	}

	@Override
	public long getSize() {
		lazyInitFileMetadata();
		if (!exists()) {
			return 0L;
		}

		return s3ObjectMetadata.getContentLength();
	}

	private S3Object getObject() {

		S3Object object = null;

		try {
			//if (LOG.isDebugEnabled()) {
				LOG.info("GETTING OBJECT - BUCKET: " + bucket + " KEY: " + key);
			//}
			object = s3Client.getObject(bucket, key);
		} catch (Exception e) {
			LOG.error("Unable to fetch S3 Object", e);
		}

		return object;
	}

	@Override
	public void close() throws Exception {
		closeFileObject();
	}
}