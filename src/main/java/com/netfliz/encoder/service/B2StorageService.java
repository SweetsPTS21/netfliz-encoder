package com.netfliz.encoder.service;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.contentSources.B2FileContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.*;
import com.netfliz.encoder.constant.B2Properties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;

@Service
@Slf4j
@AllArgsConstructor
public class B2StorageService {
    private final B2Properties b2Properties;
    private final B2StorageClient client;

    /**
     * Upload file to B2
     */
    public String uploadFile(File file, String b2Path) {
        return uploadFile(file, b2Path, detectContentType(file.getName()));
    }

    public String uploadFile(File file, String b2Path, String contentType) {
        log.info("Uploading file to B2: {}", b2Path);

        try {
            B2ContentSource source = B2FileContentSource.build(file);

            B2UploadFileRequest request = B2UploadFileRequest
                    .builder(b2Properties.getBucketId(), b2Path, contentType, source)
                    .setCustomField("uploaded-by", "netfliz-encoder")
                    .build();

            B2FileVersion fileVersion = client.uploadSmallFile(request);

            log.info("File uploaded successfully: {} ({})", b2Path, fileVersion.getFileId());

            return fileVersion.getFileId();
        } catch (B2Exception e) {
            log.info("File upload failed: {}", b2Path);
        }

        return null;
    }

    /**
     * Upload bytes to B2
     */
    public String uploadBytes(byte[] data, String b2Path, String contentType) {
        log.info("Uploading bytes to B2: {} ({} bytes)", b2Path, data.length);

        try {
            B2ContentSource source = B2ByteArrayContentSource.build(data);

            B2UploadFileRequest request = B2UploadFileRequest
                    .builder(b2Properties.getBucketId(), b2Path, contentType, source)
                    .build();

            B2FileVersion fileVersion = client.uploadSmallFile(request);

            return fileVersion.getFileId();
        } catch (B2Exception e) {
            log.info("File upload failed: {} ({} bytes)", b2Path, data.length);
        }

        return null;
    }

    /**
     * Get presigned URL for file
     */
    public String getPresignedUrl(String b2Path) {
        return getPresignedUrl(b2Path, Duration.ofHours(24));
    }

    public String getPresignedUrl(String b2Path, Duration duration) {
        try {
            B2GetDownloadAuthorizationRequest request = B2GetDownloadAuthorizationRequest
                    .builder(b2Properties.getBucketId(), b2Path, (int) duration.getSeconds())
                    .build();

            B2DownloadAuthorization downloadAuthorization = client.getDownloadAuthorization(request);
            String authToken = downloadAuthorization.getAuthorizationToken();

            // Construct download URL
            B2AccountAuthorization auth = client.getAccountAuthorization();
            String downloadUrl = auth.getDownloadUrl();

            return String.format("%s/file/%s/%s?Authorization=%s",
                    downloadUrl, b2Properties.getBucketName(), b2Path, authToken);

        } catch (Exception e) {
            log.error("Error generating presigned URL for: {}", b2Path, e);

            // Fallback to public URL if bucket is public
            try {
                B2AccountAuthorization auth = client.getAccountAuthorization();
                return String.format("%s/file/%s/%s",
                        auth.getDownloadUrl(), b2Properties.getBucketName(), b2Path);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to generate URL", ex);
            }
        }
    }

    /**
     * Detect content type from filename
     */
    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        } else if (lower.endsWith(".ts")) {
            return "video/MP2T";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".webm")) {
            return "video/webm";
        }

        return "application/octet-stream";
    }

    /**
     * Get file info
     */
    public B2FileVersion getFileInfo(String fileId) throws B2Exception {
        B2GetFileInfoRequest request = B2GetFileInfoRequest
                .builder(fileId)
                .build();

        return client.getFileInfo(request);
    }
}
