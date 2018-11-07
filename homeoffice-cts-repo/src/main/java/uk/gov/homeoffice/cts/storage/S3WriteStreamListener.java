package uk.gov.homeoffice.cts.storage;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

/**
 * Stream listener which is used to copy the temp file contents into S3
 */
public class S3WriteStreamListener implements ContentStreamListener {

	private static final Log LOG = LogFactory.getLog(S3WriteStreamListener.class);

	private final S3ContentWriter writer;

	public S3WriteStreamListener(S3ContentWriter writer) {

		this.writer = writer;

	}

	@Override
	public void contentStreamClosed() throws ContentIOException {

		File file = writer.getTempFile();
		long size = file.length();
		writer.setSize(size);

		LOG.info("Writing to s3://" + writer.getBucketName() + "/" + writer.getKey());

		TransferManager transferManager = writer.getTransferManager();

		SSEAwsKeyManagementParams sseParams = new SSEAwsKeyManagementParams(writer.getSseKey());

		PutObjectRequest request = new PutObjectRequest(writer.getBucketName(), writer.getKey(), writer.getTempFile()).withSSEAwsKeyManagementParams(sseParams);

		Upload upload = transferManager.upload(request);
		//To have transactional consistency it is necessary to wait for the upload to go through before allowing the transaction to commit!
		try {

			LOG.info("Waiting for upload result for bucket " + writer.getBucketName() + " with key " + writer.getKey());
			upload.waitForUploadResult();

			LOG.info("Upload completed for bucket " + writer.getBucketName() + " with key " + writer.getKey());
		} catch (Exception e) {
			LOG.info("Upload failed for bucket " + writer.getBucketName() + " with key " + writer.getKey());
			LOG.info(e.toString());
			throw new ContentIOException("S3WriterStreamListener Failed to Upload File for bucket " + writer.getBucketName() + " with key " + writer.getKey(), e);
		} finally {
			//Remove the temp file
			writer.getTempFile().delete();
		}

	}
}