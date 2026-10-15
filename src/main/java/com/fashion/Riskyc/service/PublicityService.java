package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.response.PublicityItemResponse;
import com.fashion.Riskyc.entity.MediaType;
import com.fashion.Riskyc.entity.PublicityItem;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.PublicityItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicityService {

    private static final String MEDIA_FOLDER = "publicity";

    private final PublicityItemRepository publicityItemRepository;
    private final S3MediaService s3MediaService;

    @Transactional(readOnly = true)
    public List<PublicityItemResponse> listActive() {
        return publicityItemRepository.findAllByActiveTrueOrderByPositionAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicityItemResponse> listAll() {
        return publicityItemRepository.findAllByOrderByPositionAsc().stream().map(this::toResponse).toList();
    }

    public PublicityItemResponse addItem(MultipartFile file, String caption) {
        String key = s3MediaService.upload(file, MEDIA_FOLDER);
        int nextPosition = publicityItemRepository.findAllByOrderByPositionAsc().stream()
                .mapToInt(PublicityItem::getPosition).max().orElse(-1) + 1;
        PublicityItem item = publicityItemRepository.saveAndFlush(PublicityItem.builder()
                .storageKey(key)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .type(isVideo(file.getContentType()) ? MediaType.VIDEO : MediaType.IMAGE)
                .caption(caption)
                .position(nextPosition)
                .build());
        return toResponse(item);
    }

    public PublicityItemResponse updateItem(UUID id, String caption, Boolean active, String captionFont, String captionStyle) {
        PublicityItem item = getOrThrow(id);
        if (caption != null) item.setCaption(caption);
        if (active != null) item.setActive(active);
        if (captionFont != null) item.setCaptionFont(captionFont);
        if (captionStyle != null) item.setCaptionStyle(captionStyle);
        return toResponse(item);
    }

    /** Reassigns position = index in the given list — every existing item must be present, or it's left stranded at its old position. */
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            PublicityItem item = getOrThrow(orderedIds.get(i));
            item.setPosition(i);
        }
    }

    public void delete(UUID id) {
        PublicityItem item = getOrThrow(id);
        s3MediaService.delete(item.getStorageKey());
        publicityItemRepository.delete(item);
    }

    private PublicityItem getOrThrow(UUID id) {
        return publicityItemRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Publicity item", id));
    }

    private boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }

    private PublicityItemResponse toResponse(PublicityItem item) {
        return new PublicityItemResponse(
                item.getId(),
                s3MediaService.getPresignedUrl(item.getStorageKey()),
                item.getType(),
                item.getCaption(),
                item.getCaptionFont() != null ? item.getCaptionFont() : "sans",
                item.getCaptionStyle() != null ? item.getCaptionStyle() : "bold",
                item.getPosition(),
                item.isActive(),
                item.getUploadedAt()
        );
    }
}
