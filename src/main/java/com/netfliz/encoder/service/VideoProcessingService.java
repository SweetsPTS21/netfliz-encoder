package com.netfliz.encoder.service;

import com.netfliz.encoder.model.VideoProcessResult;
import com.netfliz.encoder.model.VideoQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {
    private final B2StorageService b2StorageService;
    private final String TEMP_DIR = "/tmp/video-processing";

    // Các profile chất lượng video
    private static final VideoQuality[] QUALITIES = {
            new VideoQuality("1080p", 1920, 1080, "5000k", "192k"),
            new VideoQuality("720p", 1280, 720, "3000k", "128k"),
            new VideoQuality("480p", 854, 480, "1500k", "128k"),
            new VideoQuality("360p", 640, 360, "800k", "96k")
    };

    public CompletableFuture<VideoProcessResult> processVideo(MultipartFile file, Long movieId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Tạo thư mục tạm
                String jobId = UUID.randomUUID().toString();
                Path workDir = Paths.get(TEMP_DIR, jobId);
                Files.createDirectories(workDir);

                // Lưu file gốc
                Path inputPath = workDir.resolve("input.mp4");
                file.transferTo(inputPath.toFile());

                log.info("Bắt đầu xử lý video: {}", movieId);

                // Encode multiple qualities
                List<String> playlistUrls = new ArrayList<>();
                for (VideoQuality quality : QUALITIES) {
                    String playlistUrl = encodeAndUpload(inputPath, workDir, quality, movieId);
                    if (playlistUrl != null) {
                        playlistUrls.add(playlistUrl);
                    }
                }

                // Tạo master playlist
                String masterPlaylistUrl = createMasterPlaylist(playlistUrls, movieId);

                // Cleanup
                deleteDirectory(workDir.toFile());

                return VideoProcessResult.builder()
                        .movieId(movieId)
                        .masterPlaylistUrl(masterPlaylistUrl)
                        .qualities(playlistUrls)
                        .build();

            } catch (Exception e) {
                log.error("Lỗi xử lý video", e);
                throw new RuntimeException("Video processing failed", e);
            }
        });
    }

    private String encodeAndUpload(Path input, Path workDir, VideoQuality quality, Long movieId) {
        try {
            String outputDir = workDir.resolve(quality.getName()).toString();
            Files.createDirectories(Paths.get(outputDir));

            // FFmpeg command for HLS encoding
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", input.toString(),
                    "-vf", String.format("scale=%d:%d", quality.getWidth(), quality.getHeight()),
                    "-c:v", "libx264",
                    "-b:v", quality.getVideoBitrate(),
                    "-c:a", "aac",
                    "-b:a", quality.getAudioBitrate(),
                    "-preset", "medium",
                    "-g", "48", // GOP size
                    "-sc_threshold", "0",
                    "-hls_time", "6", // Segment duration
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", outputDir + "/segment_%03d.ts",
                    outputDir + "/playlist.m3u8"
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg failed for quality: {}", quality.getName());
                return null;
            }

            log.info("Encoded successfully: {}", quality.getName());

            // Upload to B2
            String b2Path = String.format("movies/%s/%s/", movieId, quality.getName());
            uploadDirectoryToB2(Paths.get(outputDir), b2Path);

            return b2StorageService.getPresignedUrl(b2Path + "playlist.m3u8");

        } catch (Exception e) {
            log.error("Error encoding quality: {}", quality.getName(), e);
            return null;
        }
    }

    private String createMasterPlaylist(List<String> playlistUrls, Long movieId) {
        StringBuilder master = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n\n");

        for (int i = 0; i < QUALITIES.length && i < playlistUrls.size(); i++) {
            VideoQuality q = QUALITIES[i];
            String url = playlistUrls.get(i);

            master.append(String.format("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d\n",
                    parseBitrate(q.getVideoBitrate()) + parseBitrate(q.getAudioBitrate()),
                    q.getWidth(), q.getHeight()));
            master.append(url).append("\n\n");
        }

        // Upload master playlist
        String b2Path = String.format("movies/%s/master.m3u8", movieId);
        byte[] content = master.toString().getBytes();
        b2StorageService.uploadBytes(content, b2Path, "application/vnd.apple.mpegurl");

        return b2StorageService.getPresignedUrl(b2Path);
    }

    private void uploadDirectoryToB2(Path dir, String b2Prefix) throws IOException {
        Files.walk(dir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = dir.relativize(file).toString();
                        String b2Path = b2Prefix + relativePath;
                        b2StorageService.uploadFile(file.toFile(), b2Path);
                    } catch (Exception e) {
                        log.error("Error uploading file: {}", file, e);
                    }
                });
    }

    private int parseBitrate(String bitrate) {
        return Integer.parseInt(bitrate.replaceAll("[^0-9]", "")) * 1000;
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
