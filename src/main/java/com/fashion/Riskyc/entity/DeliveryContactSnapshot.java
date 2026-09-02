package com.fashion.Riskyc.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A frozen copy of a {@link DeliveryContact}'s name/phone at the moment a
 * packaging-confirmation message was sent — stored on the {@link ChatMessage}
 * itself rather than referencing the live {@link DeliveryContact} row, so a
 * contact edited or deleted later doesn't rewrite what an already-sent
 * message showed the customer.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryContactSnapshot {
    private String name;
    private String phone;
}
