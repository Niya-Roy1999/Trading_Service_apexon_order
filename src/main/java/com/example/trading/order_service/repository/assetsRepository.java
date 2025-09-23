package com.example.trading.order_service.repository;

import com.example.trading.order_service.entity.Assets;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface assetsRepository extends JpaRepository<Assets, Long> {
    List<Assets> findByUserId(Long userId);

    Optional<Assets> findByUserIdAndInstrumentId(Long userId, String instrumentId);

    boolean existsByUserIdAndInstrumentId(Long userId, String instrumentId);
}
