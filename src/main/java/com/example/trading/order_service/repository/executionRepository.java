package com.example.trading.order_service.repository;

import com.example.trading.order_service.entity.Executions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface executionRepository extends JpaRepository<Executions, Long> {
    List<Executions> findByOrderId(Long orderId);
}
