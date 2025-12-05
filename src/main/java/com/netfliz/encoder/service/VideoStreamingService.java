package com.netfliz.encoder.service;

import com.netfliz.encoder.model.ProcessingResponse;
import com.netfliz.encoder.model.StreamInfoResponse;
import com.netfliz.encoder.model.UploadVideoResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VideoStreamingService {
    UploadVideoResponse uploadVideo(MultipartFile file, Long objectId, Integer objectType);

    StreamInfoResponse getStreamInfo(Long objectId, Integer objectType);

    ProcessingResponse getProcessingStatus(Long objectId, Integer objectType);
}
