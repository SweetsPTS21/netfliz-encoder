package com.netfliz.encoder.controller;

import com.netfliz.encoder.model.ProcessingResponse;
import com.netfliz.encoder.model.StreamInfoResponse;
import com.netfliz.encoder.model.UploadVideoResponse;
import com.netfliz.encoder.service.VideoStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoStreamingController {
    private final VideoStreamingService videoStreamingService;

    /**
     * Upload và xử lý video
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadVideoResponse> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("objectId") Long objectId,
            @RequestParam("objectType") Integer objectType) {

        return ResponseEntity.ok(videoStreamingService.uploadVideo(file, objectId, objectType));
    }

    /**
     * Lấy thông tin streaming của video
     */
    @GetMapping("/stream-info")
    public ResponseEntity<StreamInfoResponse> getStreamInfo(@RequestParam Long objectId, @RequestParam Integer objectType) {
        return ResponseEntity.ok(videoStreamingService.getStreamInfo(objectId, objectType));
    }

    /**
     * Proxy endpoint (optional) - nếu muốn hide Cloudflare Worker URL
     */
    @GetMapping("/{movieId}/master.m3u8")
    public ResponseEntity<String> getMasterPlaylist(@PathVariable String movieId) {

        // Redirect hoặc proxy to Cloudflare Worker
        String workerUrl = String.format(
                "https://your-worker.workers.dev/movies/%s/master.m3u8",
                movieId);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", workerUrl)
                .build();
    }

    /**
     * Check processing status
     */
    @GetMapping("/status")
    public ResponseEntity<ProcessingResponse> getProcessingStatus(@RequestParam Long objectId, @RequestParam Integer objectType) {
        return ResponseEntity.ok(videoStreamingService.getProcessingStatus(objectId, objectType));
    }
}