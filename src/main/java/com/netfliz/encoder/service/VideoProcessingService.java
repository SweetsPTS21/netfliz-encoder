package com.netfliz.encoder.service;

import com.netfliz.encoder.constant.CacheKey;
import com.netfliz.encoder.constant.CommonProperties;
import com.netfliz.encoder.constant.ProxyCndProperties;
import com.netfliz.encoder.constant.VideoProperties;
import com.netfliz.encoder.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {
    private final B2StorageService b2StorageService;
    private final ProxyCndProperties proxyCndProperties;
    private final RedisService redisService;
    private final VideoProgressService progressService;
    private final CommonProperties commonProperties;
    private final VideoProperties videoProperties;

    // Các profile chất lượng video
    private static final VideoQuality[] QUALITIES = {
            new VideoQuality("1080p", 1920, 1080, "5000k", "192k"),
            new VideoQuality("720p", 1280, 720, "3000k", "128k"),
            new VideoQuality("480p", 854, 480, "1500k", "128k"),
            new VideoQuality("360p", 640, 360, "800k", "96k")
    };

    public CompletableFuture<VideoProcessResult> processVideo(MultipartFile file, Long movieId) {
        return CompletableFuture.supplyAsync(() -> {
            String jobId = UUID.randomUUID().toString();
            Path workDir = null;

            try {
                // Tạo thư mục tạm nếu chưa tồn tại
                Path tempUploadDir = Paths.get(commonProperties.getLocalTempDir());
                if (!Files.exists(tempUploadDir)) {
                    Files.createDirectories(tempUploadDir);
                }

                // Lưu file vào thư mục tạm của ứng dụng
                String originalFilename = file.getOriginalFilename();
                Path tempFile = tempUploadDir.resolve(jobId + "_" + originalFilename);
                file.transferTo(tempFile);

                // Get file info
                long fileSize = file.getSize();

                // ProgressService: receiving file
                progressService.updateStage(movieId,
                        VideoProcessingProgress.ProcessingStatus.QUEUED,
                        "Starting", "Preparing to process video: " + originalFilename, 0);

                // Tiếp tục xử lý với file đã lưu
                workDir = Paths.get(commonProperties.getTomcatTempDir(), jobId);
                Files.createDirectories(workDir);

                // Lưu file gốc
                Path inputPath = workDir.resolve("input.mp4");
                Files.copy(tempFile, inputPath, StandardCopyOption.REPLACE_EXISTING);

                // Xóa file tạm sau khi đã copy xong
                Files.deleteIfExists(tempFile);

                // ProgressService: receiving file
                progressService.updateStage(movieId,
                        VideoProcessingProgress.ProcessingStatus.QUEUED,
                        "Uploading", "Receiving file...", 2);

                log.info("=== Bắt đầu xử lý video: {} ===", movieId);

                // Bước 0: Kiểm tra và xóa dữ liệu cũ trên B2
                cleanupOldVideoData(movieId);

                // ProgressService: analyzing file
                progressService.updateStage(movieId,
                        VideoProcessingProgress.ProcessingStatus.ANALYZING,
                        "Analyzing", "Analyzing video properties...", 10);

                // Bước 1: Phân tích video gốc
                VideoMetadata metadata = analyzeVideo(inputPath);
                log.info("Video gốc - Độ phân giải: {}x{}, Bitrate: {} kbps, Codec: {}",
                        metadata.getWidth(), metadata.getHeight(), metadata.getBitrate() / 1000, metadata.getVideoCodec());

                // Cập nhật thông tin phân tích vào progress
                VideoProcessingProgress.VideoAnalysis analysis = VideoProcessingProgress.VideoAnalysis.builder()
                        .fileName(originalFilename)
                        .fileSize(fileSize)
                        .fileSizeFormatted(formatFileSize(fileSize))
                        .width(metadata.getWidth())
                        .height(metadata.getHeight())
                        .resolution(metadata.getWidth() + "x" + metadata.getHeight())
                        .bitRate(metadata.getBitrate())
                        .videoCodec(metadata.getVideoCodec())
                        .frameRate(metadata.getFps())
                        .build();

                progressService.initializeProgress(movieId, jobId, analysis);

                // Bước 2: Xác định các chất lượng cần encode
                List<VideoQuality> targetQualities = determineTargetQualities(metadata);

                if (targetQualities.isEmpty()) {
                    log.warn("Video có độ phân giải quá thấp ({}x{}), chỉ dùng video gốc",
                            metadata.getWidth(), metadata.getHeight());

                    // ProgressService: processing quality
                    progressService.updateStage(movieId,
                            VideoProcessingProgress.ProcessingStatus.ENCODING,
                            "Processing original", "Processing original quality only...", 30);

                    // Upload video gốc như HLS
                    String originalUrl = encodeAndUpload(inputPath, workDir,
                            new VideoQuality("original", metadata.getWidth(), metadata.getHeight(),
                                    metadata.getBitrate() + "k", "128k"), movieId);

                    // ProgressService: creating playlist
                    progressService.updateStage(movieId,
                            VideoProcessingProgress.ProcessingStatus.CREATING_PLAYLIST,
                            "Finalizing", "Creating master playlist...", 90);

                    String masterPlaylistUrl = createMasterPlaylist(
                            List.of(originalUrl), List.of(metadata.getHeight() + "p"), movieId);

                    // ProgressService: completed
                    progressService.markCompleted(movieId, "Processing completed successfully!");

                    return VideoProcessResult.builder()
                            .movieId(movieId)
                            .masterPlaylistUrl(masterPlaylistUrl)
                            .sourceResolution(metadata.getWidth() + "x" + metadata.getHeight())
                            .processedQualities(List.of(metadata.getHeight() + "p"))
                            .skippedQualities(List.of())
                            .build();
                }

                log.info("Sẽ encode {} chất lượng: {}", targetQualities.size(),
                        targetQualities.stream().map(VideoQuality::getName).toList());

                // ProgressService: set qualities in progress
                progressService.setQualitiesToEncode(movieId,
                        targetQualities.stream().map(VideoQuality::getName).toList());

                // ProgressService: init encoding
                progressService.updateStage(movieId,
                        VideoProcessingProgress.ProcessingStatus.ENCODING,
                        "Encoding", "Encoding multiple qualities...", 15);

                // Bước 3: Encode các chất lượng
                List<String> playlistUrls = new ArrayList<>();
                List<String> processedQualityNames = new ArrayList<>();
                double progressPerQuality = 100.0 / targetQualities.size();
                int progressTotalPerQuality = 50 / targetQualities.size();
                int progressTotal = 35;

                for (VideoQuality quality : targetQualities) {
                    // update progress to cache
                    double currentProgress = processedQualityNames.size() * progressPerQuality;
                    updateProgress(movieId, currentProgress);

                    try {
                        // ProgressService: start encoding each quality
                        progressService.updateQualityProgress(movieId, quality.getName(),
                                VideoProcessingProgress.QualityStatus.ENCODING,
                                0, "Starting encode");

                        String playlistUrl = encodeAndUpload(inputPath, workDir, quality, movieId);
                        if (playlistUrl != null) {
                            playlistUrls.add(playlistUrl);
                            processedQualityNames.add(quality.getName());

                            // ProgressService: completed uploading each quality
                            progressService.updateQualityProgress(movieId, quality.getName(),
                                    VideoProcessingProgress.QualityStatus.COMPLETED,
                                    100, "Completed");

                            // ProgressService: update master stage
                            progressService.updateStage(movieId,
                                    VideoProcessingProgress.ProcessingStatus.UPLOADING,
                                    "Uploading", "Uploading to B2", progressTotal);

                            // Cập nhật progress sau khi encode thành công
                            updateProgress(movieId, (processedQualityNames.size() * progressPerQuality));
                            progressTotal += progressTotalPerQuality;

                            log.info("✓ Hoàn thành encode: {}", quality.getName());
                        }
                    } catch (Exception e) {
                        // ProgressService: failed
                        progressService.updateQualityProgress(movieId, quality.getName(),
                                VideoProcessingProgress.QualityStatus.FAILED,
                                0, "Failed: " + e.getMessage());

                        log.error("✗ Lỗi encode {}: {}", quality.getName(), e.getMessage());
                    }
                }

                if (playlistUrls.isEmpty()) {
                    throw new RuntimeException("Không encode được chất lượng nào");
                }

                // ProgressService: creating playlist
                progressService.updateStage(movieId,
                        VideoProcessingProgress.ProcessingStatus.CREATING_PLAYLIST,
                        "Finalizing", "Creating master playlist...", 85);

                String masterPlaylistUrl = createMasterPlaylist(playlistUrls,
                        processedQualityNames, movieId);

                // ProgressService: completed
                progressService.markCompleted(movieId,
                        String.format("Successfully processed %d qualities", playlistUrls.size()));

                log.info("=== Hoàn thành xử lý video: {} ===", movieId);

                return VideoProcessResult.builder()
                        .movieId(movieId)
                        .masterPlaylistUrl(masterPlaylistUrl)
                        .sourceResolution(metadata.getWidth() + "x" + metadata.getHeight())
                        .processedQualities(processedQualityNames)
                        .skippedQualities(getSkippedQualities(metadata, targetQualities))
                        .build();
            } catch (Exception e) {
                log.error("Lỗi xử lý video", e);

                // ProgressService: failed
                progressService.markFailed(movieId,
                        "Video processing failed", e.getMessage());

                throw new RuntimeException("Video processing failed", e);
            } finally {
                // Cleanup
                if (workDir != null) {
                    try {
                        deleteDirectory(workDir.toFile());
                        log.info("Đã xóa thư mục tạm: {}", workDir);
                    } catch (Exception e) {
                        log.error("Lỗi xóa thư mục tạm", e);
                    }
                }
            }
        });
    }

    /**
     * Kiểm tra và xóa dữ liệu cũ của movie trên B2
     */
    private void cleanupOldVideoData(Long movieId) {
        try {
            // ProgressService: cleaning
            progressService.updateStage(movieId,
                    VideoProcessingProgress.ProcessingStatus.CLEANING_OLD_FILES,
                    "Cleanup", "Checking for old files...", 5);

            String moviePrefix = String.format("movies/%s/", movieId);
            log.info("Kiểm tra dữ liệu cũ trên B2: {}", moviePrefix);

            List<B2FileInfo> existingFiles = b2StorageService.listFilesByPrefix(moviePrefix);

            if (existingFiles.isEmpty()) {
                log.info("✓ Không có dữ liệu cũ, tiếp tục xử lý");
                return;
            }

            log.warn("⚠ Tìm thấy {} file cũ, đang xóa...", existingFiles.size());

            // ProgressService: deleting
            progressService.updateStage(movieId,
                    VideoProcessingProgress.ProcessingStatus.CLEANING_OLD_FILES,
                    "Cleanup", String.format("Deleting %d old files...", existingFiles.size()), 7);

            int deleted = 0;
            int failed = 0;

            for (B2FileInfo fileInfo : existingFiles) {
                try {
                    b2StorageService.deleteFile(fileInfo.getFileName(), fileInfo.getFileId());
                    deleted++;

                    if (deleted % 50 == 0) {
                        log.info("  Đã xóa {}/{} files", deleted, existingFiles.size());

                        // ProgressService: cleaned
                        progressService.updateStage(movieId,
                                VideoProcessingProgress.ProcessingStatus.CLEANING_OLD_FILES,
                                "Cleanup",
                                String.format("Deleted %d/%d files", deleted, existingFiles.size()),
                                7 + (deleted * 3 / existingFiles.size()));
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Lỗi xóa file: {} ({})", fileInfo.getFileName(), e.getMessage());
                }
            }

            log.info("✓ Đã xóa {}/{} files cũ (thất bại: {})", deleted, existingFiles.size(), failed);

            if (failed > 0) {
                log.warn("⚠ Có {} files không xóa được, có thể gây trùng lặp", failed);
            }

        } catch (Exception e) {
            log.error("Lỗi khi cleanup dữ liệu cũ, tiếp tục xử lý...", e);
            // Không throw exception, vẫn tiếp tục xử lý video
        }
    }

    /**
     * Encode video và upload lên B2
     */
    private String encodeAndUpload(Path input, Path workDir, VideoQuality quality, Long movieId) {
        try {
            String outputDir = workDir.resolve(quality.getName()).toString();
            Files.createDirectories(Paths.get(outputDir));

            log.info("Đang encode {}...", quality.getName());

            // FFmpeg command for HLS encoding
            List<String> command = new ArrayList<>(List.of(
                    videoProperties.getProcessing().getFfmpegPath(),
                    "-i", input.toString(),
                    "-vf", String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2",
                            quality.getWidth(), quality.getHeight(), quality.getWidth(), quality.getHeight()),
                    "-c:v", "libx264",
                    "-profile:v", "high",
                    "-level", "4.0",
                    "-b:v", quality.getVideoBitrate(),
                    "-maxrate", quality.getVideoBitrate(),
                    "-bufsize", (parseBitrate(quality.getVideoBitrate()) * 2) + "k",
                    "-c:a", "aac",
                    "-b:a", quality.getAudioBitrate(),
                    "-ac", "2",
                    "-preset", "medium",
                    "-g", "48",
                    "-sc_threshold", "0",
                    "-keyint_min", "48",
                    "-hls_time", "6",
                    "-hls_playlist_type", "vod",
                    "-hls_segment_type", "mpegts",
                    "-hls_segment_filename", outputDir + "/segment_%03d.ts",
                    "-hls_flags", "independent_segments",
                    outputDir + "/playlist.m3u8"
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Log output để debug
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("time=") || line.contains("error") || line.contains("Error")) {
                        log.debug("FFmpeg: {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg failed for quality: {} (exit code: {})", quality.getName(), exitCode);
                return null;
            }

            log.info("✓ Encode thành công: {}", quality.getName());

            // ProgressService: finish encoding
            progressService.updateQualityProgress(movieId, quality.getName(),
                    VideoProcessingProgress.QualityStatus.ENCODING,
                    100, "Encode completed");

            // Đếm số segments
            long segmentCount = Files.list(Paths.get(outputDir))
                    .filter(p -> p.toString().endsWith(".ts"))
                    .count();
            log.info("  Đã tạo {} segments", segmentCount);

            // Upload to B2
            String b2Path = String.format("movies/%s/%s/", movieId, quality.getName());
            uploadDirectoryToB2(Paths.get(outputDir), b2Path, movieId, quality.getName());

            return String.format("%s/%splaylist.m3u8", proxyCndProperties.getStreamUrl(), b2Path);
        } catch (Exception e) {
            log.error("Error encoding quality: {}", quality.getName(), e);
            return null;
        }
    }

    /**
     * Phân tích metadata của video
     */
    private VideoMetadata analyzeVideo(Path videoPath) throws IOException, InterruptedException {
        log.info("Đang phân tích video...");

        ProcessBuilder pb = new ProcessBuilder(
                videoProperties.getProcessing().getFfprobePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,codec_name,bit_rate,r_frame_rate",
                "-of", "default=noprint_wrappers=1",
                videoPath.toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        VideoMetadata metadata = new VideoMetadata();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("width=")) {
                    metadata.setWidth(Integer.parseInt(line.split("=")[1]));
                } else if (line.startsWith("height=")) {
                    metadata.setHeight(Integer.parseInt(line.split("=")[1]));
                } else if (line.startsWith("codec_name=")) {
                    metadata.setVideoCodec(line.split("=")[1]);
                } else if (line.startsWith("bit_rate=")) {
                    try {
                        metadata.setBitrate(Integer.parseInt(line.split("=")[1]) / 1000); // to kbps
                    } catch (NumberFormatException e) {
                        metadata.setBitrate(0);
                    }
                } else if (line.startsWith("r_frame_rate=")) {
                    String[] parts = line.split("=")[1].split("/");
                    if (parts.length == 2) {
                        metadata.setFps(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFprobe failed with exit code: " + exitCode);
        }

        // Nếu không lấy được bitrate từ stream, ước tính từ file size
        if (metadata.getBitrate() == 0) {
            metadata.setBitrate(estimateBitrate(videoPath));
        }

        return metadata;
    }

    /**
     * Ước tính bitrate từ file size (fallback)
     */
    private int estimateBitrate(Path videoPath) throws IOException {
        long fileSize = Files.size(videoPath);
        // Giả sử video dài 2 giờ trung bình để ước tính
        // bitrate = (fileSize * 8) / (duration_seconds * 1000) kbps
        return (int) (fileSize * 8 / (7200 * 1000)); // 7200s = 2h
    }

    /**
     * Xác định các chất lượng cần encode (chỉ encode chất lượng thấp hơn hoặc bằng gốc)
     */
    private List<VideoQuality> determineTargetQualities(VideoMetadata metadata) {
        List<VideoQuality> targets = new ArrayList<>();
        int sourceHeight = metadata.getHeight();

        log.info("Xác định chất lượng cần encode cho video {}x{}", metadata.getWidth(), metadata.getHeight());

        for (VideoQuality quality : QUALITIES) {
            // Chỉ encode nếu độ phân giải thấp hơn video gốc
            if (quality.getHeight() < sourceHeight) {
                targets.add(quality);
                log.debug("  → Sẽ encode: {} ({}x{})", quality.getName(), quality.getWidth(), quality.getHeight());
            } else if (quality.getHeight() == sourceHeight) {
                // Nếu cùng độ phân giải, kiểm tra bitrate
                int sourceBitrate = metadata.getBitrate();
                int qualityBitrate = parseBitrate(quality.getVideoBitrate());

                if (qualityBitrate < sourceBitrate * 0.9) { // Chỉ encode nếu giảm >10% bitrate
                    targets.add(quality);
                    log.debug("  → Sẽ encode: {} (cùng độ phân giải nhưng giảm bitrate)", quality.getName());
                } else {
                    log.debug("  ✗ Bỏ qua: {} (cùng hoặc cao hơn video gốc)", quality.getName());
                }
            } else {
                log.debug("  ✗ Bỏ qua: {} (cao hơn video gốc)", quality.getName());
            }
        }

        return targets;
    }

    /**
     * Lấy danh sách các chất lượng bị bỏ qua
     */
    private List<String> getSkippedQualities(VideoMetadata metadata, List<VideoQuality> processed) {
        List<String> skipped = new ArrayList<>();
        int sourceHeight = metadata.getHeight();

        for (VideoQuality quality : QUALITIES) {
            if (quality.getHeight() >= sourceHeight &&
                    processed.stream().noneMatch(q -> q.getName().equals(quality.getName()))) {
                skipped.add(quality.getName() + " (cao hơn gốc)");
            }
        }

        return skipped;
    }

    /**
     * Tạo master playlist với thông tin bandwidth chính xác
     */
    private String createMasterPlaylist(List<String> playlistUrls,
                                        List<String> qualityNames,
                                        Long movieId) {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        for (int i = 0; i < playlistUrls.size(); i++) {
            String qualityName = qualityNames.get(i);
            String url = playlistUrls.get(i);

            // Tìm quality config tương ứng
            VideoQuality quality = findQualityByName(qualityName);
            if (quality != null) {
                int bandwidth = parseBitrate(quality.getVideoBitrate()) + parseBitrate(quality.getVideoBitrate());

                master.append(String.format("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d,CODECS=\"avc1.640028,mp4a.40.2\"\n",
                        bandwidth, quality.getWidth(), quality.getHeight()));
                master.append(url).append("\n\n");
            }
        }

        // Upload master playlist
        String b2Path = String.format("movies/%s/master.m3u8", movieId);
        byte[] content = master.toString().getBytes();
        b2StorageService.uploadBytes(content, b2Path, "application/vnd.apple.mpegurl");

        log.info("✓ Đã tạo master playlist với {} chất lượng", playlistUrls.size());

        return String.format("%s/%s", proxyCndProperties.getStreamUrl(), b2Path);
    }

    private VideoQuality findQualityByName(String name) {
        for (VideoQuality q : QUALITIES) {
            if (q.getName().equals(name)) {
                return q;
            }
        }
        return null;
    }

    /**
     * Upload toàn bộ thư mục lên B2
     */
    private void uploadDirectoryToB2(Path dir, String b2Prefix, Long movieId, String quality) throws IOException {
        log.info("Đang upload lên B2: {}", b2Prefix);

        // ProgressService: start uploading
        progressService.updateQualityProgress(movieId, quality,
                VideoProcessingProgress.QualityStatus.UPLOADING,
                0, "Start uploading");

        List<Path> files = Files.walk(dir)
                .filter(Files::isRegularFile)
                .toList();

        // Calculate total size for progress tracking
        long totalSize = files.stream().mapToLong(p -> {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }).sum();

        long uploadedBytes = 0;
        int uploadedFiles = 0;

        int uploaded = 0;
        for (Path file : files) {
            try {
                String relativePath = dir.relativize(file).toString();
                String b2Path = b2Prefix + relativePath;

                // Get file size before upload for progress calculation
                long fileSize = Files.size(file);

                // Upload the file
                b2StorageService.uploadFile(file.toFile(), b2Path);

                // Update progress
                uploadedBytes += fileSize;
                uploadedFiles++;

                // Calculate progress percentage
                int progress = (int) ((uploadedBytes * 100) / Math.max(totalSize, 1));

                // Send progress update
                progressService.updateUploadProgress(
                        movieId,
                        quality,
                        uploadedBytes,
                        totalSize
                );

                if (uploadedFiles % 5 == 0) {
                    log.debug("  Đã upload {}/{} files ({}%)",
                            uploadedFiles, files.size(), progress);
                }
            } catch (Exception e) {
                log.error("Lỗi upload file: {}", file, e);
            }
        }

        log.info("✓ Đã upload {}/{} files lên B2", uploaded, files.size());
    }

    /**
     * Update processing progress to cache
     */
    private void updateProgress(Long movieId, double progress) {
        String key = CacheKey.buildKey(CacheKey.CACHE_VIDEO_PROCESSING_PROGRESS, String.valueOf(movieId));
        redisService.set(key, progress);
    }

    private int parseBitrate(String bitrate) {
        // Nếu có 'k' ở cuối, chỉ lấy số và không nhân thêm 1000
        if (bitrate.toLowerCase().endsWith("k")) {
            return Integer.parseInt(bitrate.replaceAll("[^0-9]", ""));
        }
        // Nếu là số không có đơn vị, mặc định là kbps
        return Integer.parseInt(bitrate);
    }

    /**
     * Format file size from bytes to human readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Format duration in seconds to HH:MM:SS format
     */
    private String formatDuration(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
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
