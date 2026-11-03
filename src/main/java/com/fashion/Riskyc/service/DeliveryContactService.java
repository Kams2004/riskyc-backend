package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.DeliveryContactRequest;
import com.fashion.Riskyc.dto.response.DeliveryContactResponse;
import com.fashion.Riskyc.entity.DeliveryContact;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.DeliveryContactRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
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
    private final EnterpriseRepository enterpriseRepository;

    /** Platform staff see the platform's own roster, exactly as before; a scoped admin sees only their own enterprise's. */
    @Transactional(readOnly = true)
    public List<DeliveryContactResponse> listAll() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<DeliveryContact> contacts = scopeId == null
                ? deliveryContactRepository.findAllByEnterpriseIsNullOrderByPositionAscCreatedAtAsc()
                : deliveryContactRepository.findAllByEnterpriseIdOrderByPositionAscCreatedAtAsc(scopeId);
        return contacts.stream().map(this::toResponse).toList();
    }

    public DeliveryContactResponse create(DeliveryContactRequest request) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        Enterprise enterprise = scopeId == null ? null
                : enterpriseRepository.findById(scopeId).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
        DeliveryContact saved = deliveryContactRepository.save(DeliveryContact.builder()
                .name(request.name())
                .phone(request.phone())
                .position(request.position() != null ? request.position() : 0)
                .enterprise(enterprise)
                .build());
        return toResponse(saved);
    }

    public DeliveryContactResponse update(UUID id, DeliveryContactRequest request) {
        DeliveryContact contact = getOrThrow(id);
        requireOwnedOrPlatform(contact);
        contact.setName(request.name());
        contact.setPhone(request.phone());
        if (request.position() != null) {
            contact.setPosition(request.position());
        }
        return toResponse(contact);
    }

    public void delete(UUID id) {
        DeliveryContact contact = getOrThrow(id);
        requireOwnedOrPlatform(contact);
        deliveryContactRepository.deleteById(id);
    }

    private void requireOwnedOrPlatform(DeliveryContact contact) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (contact.getEnterprise() == null || !scopeId.equals(contact.getEnterprise().getId())) {
            throw new ForbiddenException("This delivery contact belongs to a different seller.");
        }
    }

    private DeliveryContact getOrThrow(UUID id) {
        return deliveryContactRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("DeliveryContact", id));
    }

    private DeliveryContactResponse toResponse(DeliveryContact c) {
        return new DeliveryContactResponse(c.getId(), c.getName(), c.getPhone(), c.getPosition());
    }
}
