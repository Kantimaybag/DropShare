package com.sharedrop.repository;

import com.sharedrop.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByShareId(Long shareId);

    List<OtpVerification> findByExpiresAtBefore(Instant time);
}
