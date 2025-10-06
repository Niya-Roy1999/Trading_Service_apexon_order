package com.example.trading.order_service.repository;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId);

    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId);

    List<Order> findByUserIdAndInstrumentIdOrderByPlacedAtDesc(Long userId, String instrumentId);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    Optional<Order> findById(Long id);
}
