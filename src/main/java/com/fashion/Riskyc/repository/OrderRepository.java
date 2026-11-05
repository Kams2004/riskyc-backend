package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Order;
import com.fashion.Riskyc.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByStatusInOrderByCreatedAtDesc(Collection<OrderStatus> statuses);
    List<Order> findByEnterpriseIdOrderByCreatedAtDesc(UUID enterpriseId);
    List<Order> findByEnterpriseIdAndStatusInOrderByCreatedAtDesc(UUID enterpriseId, Collection<OrderStatus> statuses);
    /** The sibling Orders produced by one checkout — see Order#checkoutGroupId. */
    List<Order> findByCheckoutGroupIdOrderByCreatedAtDesc(UUID checkoutGroupId);
    List<Order> findByEnterpriseIsNull();

    /** Unpaid orders still holding stock — see UnpaidOrderExpiryJob. */
    @Query("select o.id from Order o where o.status in :statuses and o.paymentScreenshotKey is null and o.createdAt < :before")
    List<UUID> findUnpaidIdsCreatedBefore(@Param("statuses") Collection<OrderStatus> statuses, @Param("before") java.time.Instant before);

    /**
     * Row-locks the order for the rest of the transaction — used by
     * startPackaging so two admins claiming the same order at the same
     * moment are serialized instead of both reading VALIDATED and both
     * "winning" (the second's write silently clobbering the first's).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    /** Orders keep their own customerInfo snapshot, so they stay readable after the customer account is deleted. */
    @Modifying
    @Query("update Order o set o.customer = null where o.customer.id = :customerId")
    void detachCustomer(UUID customerId);
}
