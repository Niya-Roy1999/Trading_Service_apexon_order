package com.example.trading.order_service.repository;

import com.example.trading.order_service.entity.Assets;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface assetsRepository extends JpaRepository<Assets, Long> {
    List<Assets> findByUserId(Long userId);

    Optional<Assets> findByUserIdAndInstrumentId(Long userId, String instrumentId);

    boolean existsByUserIdAndInstrumentId(Long userId, String instrumentId);
}
