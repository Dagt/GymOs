package com.gymos.common.storage;

import com.gymos.common.api.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {
	private final MinioClient minioClient;
	private final StorageProperties properties;

	public ObjectStorageService(MinioClient minioClient, StorageProperties properties) {
		this.minioClient = minioClient;
		this.properties = properties;
	}

	public String putReceipt(String objectKey, String contentType, long size, InputStream inputStream) {
		try {
			ensureBucket();
			minioClient.putObject(PutObjectArgs.builder()
					.bucket(properties.bucket())
					.object(objectKey)
					.contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
					.stream(inputStream, size, -1)
					.build());
			return objectKey;
		} catch (Exception exception) {
			throw new BusinessException("OBJECT_STORAGE_UPLOAD_FAILED", "No fue posible guardar el comprobante.");
		}
	}

	private void ensureBucket() throws Exception {
		boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
				.bucket(properties.bucket())
				.build());
		if (!exists) {
			minioClient.makeBucket(MakeBucketArgs.builder()
					.bucket(properties.bucket())
					.build());
		}
	}
}
