package com.gymos.common.storage;

import com.gymos.common.api.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
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
		return putObject(objectKey, contentType, size, inputStream);
	}

	public String putObject(String objectKey, String contentType, long size, InputStream inputStream) {
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
			throw new BusinessException("OBJECT_STORAGE_UPLOAD_FAILED", "No fue posible guardar el archivo.");
		}
	}

	public void removeObject(String objectKey) {
		try {
			minioClient.removeObject(RemoveObjectArgs.builder()
					.bucket(properties.bucket())
					.object(objectKey)
					.build());
		} catch (Exception exception) {
			throw new BusinessException("OBJECT_STORAGE_DELETE_FAILED", "No fue posible eliminar el archivo.");
		}
	}

	public StoredObject getObject(String objectKey) {
		try {
			var stat = minioClient.statObject(StatObjectArgs.builder()
					.bucket(properties.bucket())
					.object(objectKey)
					.build());
			try (var stream = minioClient.getObject(GetObjectArgs.builder()
					.bucket(properties.bucket())
					.object(objectKey)
					.build())) {
				return new StoredObject(stream.readAllBytes(), stat.contentType());
			}
		} catch (Exception exception) {
			throw new BusinessException("OBJECT_STORAGE_READ_FAILED", "No fue posible leer el archivo.");
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

	public record StoredObject(byte[] content, String contentType) {}
}
