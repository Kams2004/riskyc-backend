package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.DeliveryContactRequest;
import com.fashion.Riskyc.dto.response.DeliveryContactResponse;
import com.fashion.Riskyc.entity.DeliveryContact;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.DeliveryContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryContactService {

    private final DeliveryContactRepository deliveryContactRepository;

    @Transactional(readOnly = true)
    public List<DeliveryContactResponse> listAll() {
        return deliveryContactRepository.findAllByOrderByPositionAscCreatedAtAsc().stream().map(this::toResponse).toList();
    }

    public DeliveryContactResponse create(DeliveryContactRequest request) {
        DeliveryContact saved = deliveryContactRepository.save(DeliveryContact.builder()
                .name(request.name())
                .phone(request.phone())
                .position(request.position() != null ? request.position() : 0)
                .build());
        return toResponse(saved);
    }

    public DeliveryContactResponse update(UUID id, DeliveryContactRequest request) {
        DeliveryContact contact = getOrThrow(id);
        contact.setName(request.name());
        contact.setPhone(request.phone());
        if (request.position() != null) {
            contact.setPosition(request.position());
        }
        return toResponse(contact);
    }

    public void delete(UUID id) {
        if (!deliveryContactRepository.existsById(id)) {
            throw ResourceNotFoundException.of("DeliveryContact", id);
        }
        deliveryContactRepository.deleteById(id);
    }

    private DeliveryContact getOrThrow(UUID id) {
        return deliveryContactRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("DeliveryContact", id));
    }

    private DeliveryContactResponse toResponse(DeliveryContact c) {
        return new DeliveryContactResponse(c.getId(), c.getName(), c.getPhone(), c.getPosition());
    }
}
