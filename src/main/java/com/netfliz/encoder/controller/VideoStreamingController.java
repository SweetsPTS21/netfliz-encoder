package com.netfliz.encoder.controller;

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
            @RequestParam("movieId") String movieId,
            @RequestParam(value = "title", required = false) String title) {

        return ResponseEntity.ok(videoStreamingService.uploadVideo(file, movieId, title));
    }

    /**
     * Lấy thông tin streaming của video
     */
    @GetMapping("/{movieId}/stream-info")
    public ResponseEntity<Map<String, Object>> getStreamInfo(@PathVariable String movieId) {

        // Lấy từ database
        String cloudflareWorkerUrl = "https://your-worker.workers.dev";
        String masterPlaylistUrl = String.format("%s/movies/%s/master.m3u8",
                cloudflareWorkerUrl, movieId);

        Map<String, Object> response = new HashMap<>();
        response.put("movieId", movieId);
        response.put("streamUrl", masterPlaylistUrl);
        response.put("type", "hls");
        response.put("qualities", new String[]{"1080p", "720p", "480p", "360p"});

        return ResponseEntity.ok(response);
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
    @GetMapping("/{movieId}/status")
    public ResponseEntity<Map<String, Object>> getProcessingStatus(@PathVariable String movieId) {

        // Check from database or cache
        Map<String, Object> response = new HashMap<>();
        response.put("movieId", movieId);
        response.put("status", "completed"); // processing, completed, failed
        response.put("progress", 100);

        return ResponseEntity.ok(response);
    }
}