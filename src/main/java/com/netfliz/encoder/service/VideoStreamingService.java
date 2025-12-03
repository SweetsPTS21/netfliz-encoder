package com.netfliz.encoder.service;

import com.netfliz.encoder.model.UploadVideoResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VideoStreamingService {
    UploadVideoResponse uploadVideo(MultipartFile file, String movieId, String title);
}
