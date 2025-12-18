package com.netfliz.encoder.service.impl;

import com.netfliz.encoder.constant.CacheKey;
import com.netfliz.encoder.entity.MovieProcessLogsEntity;
import com.netfliz.encoder.entity.enums.MovieAssetType;
import com.netfliz.encoder.entity.enums.ProcessLogsObjectType;
import com.netfliz.encoder.entity.enums.ProcessLogsStatus;
import com.netfliz.encoder.model.ProcessingResponse;
import com.netfliz.encoder.model.StreamInfoResponse;
import com.netfliz.encoder.model.UploadVideoResponse;
import com.netfliz.encoder.model.VideoProcessResult;
import com.netfliz.encoder.model.event.UpdateMovieAssetEvent;
import com.netfliz.encoder.repository.MovieProcessLogRepository;
import com.netfliz.encoder.service.KafkaProducerService;
import com.netfliz.encoder.service.RedisService;
import com.netfliz.encoder.service.VideoProcessingService;
import com.netfliz.encoder.service.VideoStreamingService;
import com.netfliz.encoder.utils.CommonUtils;
import com.netfliz.encoder.utils.JsonUtils;
import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@AllArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {
    private final VideoProcessingService videoProcessingService;
    private final MovieProcessLogRepository movieProcessLogRepository;
    private final RedisService redisService;
    private final KafkaProducerService kafkaProducerService;

    @Override
    public UploadVideoResponse uploadVideo(MultipartFile file,
                                           Long objectId,
                                           Integer objectType,
                                           String drm,
                                           String rendition) {
        validate(file, objectId, objectType);
        checkProcessingCache(objectId, objectType);

        log.info("Receiving video upload for movie: {}", objectId);

        // save processing logs
        var logs = initProcessingResult(objectType, objectId);
        updateProcessingCache(objectId, objectType, false);

        CompletableFuture<VideoProcessResult> future =
                videoProcessingService.processVideo(file, objectId);

        // Return job ID immediately
        UploadVideoResponse response = UploadVideoResponse.builder()
                .message("Video processing started")
                .movieId(objectId)
                .status("processing")
                .build();

        // Handle completion asynchronously
        future.thenAccept(result -> {
            log.info("Video processing completed for {}: {}", ProcessLogsObjectType.fromId(objectType), objectId);
            // Save to database or notify via WebSocket
            updateProcessingResult(logs, result, ProcessLogsStatus.COMPLETED, 100);
            updateProcessingCache(objectId, objectType, true);
            createMovieAsset(objectId, objectType, drm, rendition, file, result);
        }).exceptionally(ex -> {
            log.error("Video processing failed for {}: {}", ProcessLogsObjectType.fromId(objectType), objectId, ex);
            updateProcessingResult(logs, null, ProcessLogsStatus.FAILED, 0);
            updateProcessingCache(objectId, objectType, true);
            return null;
        });

        return response;
    }

    @Override
    public StreamInfoResponse getStreamInfo(Long objectId, Integer objectType) {
        validate(objectId, objectType);

        MovieProcessLogsEntity entity = movieProcessLogRepository.getFirstStreamInfo(objectId, ProcessLogsObjectType.fromId(objectType))
                .orElseThrow(() -> new ValidationException("Không tìm thấy phim đang xử lý"));

        if (entity.getStatus().equals(ProcessLogsStatus.PROCESSING)) {
            throw new ValidationException("Phim đang được xử lý");
        }

        if (entity.getStatus().equals(ProcessLogsStatus.FAILED)) {
            throw new ValidationException("Có lỗi trong quá trình xử lý");
        }

        return StreamInfoResponse.buildFromEntity(entity);
    }

    @Override
    public ProcessingResponse getProcessingStatus(Long objectId, Integer objectType) {
        validate(objectId, objectType);

        MovieProcessLogsEntity entity = movieProcessLogRepository.getFirstStreamInfo(objectId, ProcessLogsObjectType.fromId(objectType))
                .orElseThrow(() -> new ValidationException("Không tìm thấy phim đang xử lý"));

        String key = CacheKey.buildKey(CacheKey.CACHE_VIDEO_PROCESSING_PROGRESS, String.valueOf(objectId));
        double progress = Optional.ofNullable(redisService.get(key, Double.class)).orElse(0.0);

        if (entity.getStatus().equals(ProcessLogsStatus.COMPLETED)) {
            progress = 100;
        }

        return ProcessingResponse.builder()
                .objectId(objectId)
                .objectType(objectType)
                .status(entity.getStatus().name())
                .progress(progress)
                .build();
    }

    /**
     * Khởi tạo kết quả xử lý video vào DB
     */
    private MovieProcessLogsEntity initProcessingResult(Integer objectType,
                                                        Long objectId) {
        MovieProcessLogsEntity entity = new MovieProcessLogsEntity();
        entity.setObjectId(objectId);
        entity.setObjectType(ProcessLogsObjectType.fromId(objectType));
        entity.setStatus(ProcessLogsStatus.fromId(1));
        entity.setProgress(0);
        return movieProcessLogRepository.save(entity);
    }

    /**
     * Cập nhật kết quả xử lý video vào DB
     */
    private void updateProcessingResult(MovieProcessLogsEntity entity,
                                        VideoProcessResult result,
                                        ProcessLogsStatus status,
                                        Integer progress) {
        entity.setStatus(status);
        entity.setProgress(progress);
        if (Objects.nonNull(result)) {
            entity.setConfigs(JsonUtils.parse(result));
        }

        movieProcessLogRepository.save(entity);
    }

    private void createMovieAsset(Long objectId,
                                  Integer objectType,
                                  String drm,
                                  String rendition,
                                  MultipartFile file,
                                  VideoProcessResult result) {
        var filePayload = UpdateMovieAssetEvent.FilePayload.builder()
                .fileCategory("video encode")
                .fileExtension(CommonUtils.getFileExtension(file.getOriginalFilename()))
                .fileName(file.getOriginalFilename())
                .fileOwner("admin")
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .fileUploader("admin")
                .build();

        var payload = UpdateMovieAssetEvent.MovieAssetPayload.builder()
                .objectId(objectId)
                .objectType(objectType)
                .assetType(MovieAssetType.VIDEO_ENCODE.getId())
                .name(file.getOriginalFilename())
                .format(CommonUtils.getFileExtension(file.getOriginalFilename()))
                .url(result.getMasterPlaylistUrl())
                .drm(drm)
                .rendition(rendition)
                .file(filePayload)
                .build();

        kafkaProducerService.sendUpdateMovieAssetEvent(UpdateMovieAssetEvent.create(payload));
    }

    /**
     * Cache kiểm tra video có đang được xử lý hay không
     * Tránh xử lý video trùng lặp
     */
    private void checkProcessingCache(Long objectId, Integer objectType) {
        String key = CacheKey.buildKey(CacheKey.CACHE_PROCESSING_VIDEO, objectType.toString(), objectId.toString());
        if (redisService.hasKey(key)) {
            throw new ValidationException("Phim đang được xử lý");
        }
    }

    /**
     * Thêm/xóa cache khi video hoàn thành xử lý
     */
    private void updateProcessingCache(Long objectId, Integer objectType, boolean finish) {
        String key = CacheKey.buildKey(CacheKey.CACHE_PROCESSING_VIDEO, objectType.toString(), objectId.toString());
        if (finish) {
            redisService.delete(key);
        } else {
            redisService.set(key, true);
        }
    }

    private void validate(MultipartFile file, Long objectId, Integer objectType) {
        if (file.isEmpty()) {
            throw new ValidationException("Cần truyền lên file");
        }

        if (Objects.isNull(objectId)) {
            throw new ValidationException("Cần truyền lên objectId");
        }

        if (Objects.isNull(objectType)) {
            throw new ValidationException("Cần truyền lên objectType");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new ValidationException("Invalid file type. Must be video.");
        }

        if (Objects.isNull(ProcessLogsObjectType.fromId(objectType))) {
            throw new ValidationException("Chỉ hỗ trợ movie/episode");
        }
    }

    private void validate(Long objectId, Integer objectType) {
        if (Objects.isNull(objectId)) {
            throw new ValidationException("Cần truyền lên objectId");
        }

        if (Objects.isNull(objectType)) {
            throw new ValidationException("Cần truyền lên objectType");
        }

        if (Objects.isNull(ProcessLogsObjectType.fromId(objectType))) {
            throw new ValidationException("Chỉ hỗ trợ movie/episode");
        }
    }
}
