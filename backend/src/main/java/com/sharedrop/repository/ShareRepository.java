package com.sharedrop.repository;

import com.sharedrop.entity.Share;
import com.sharedrop.enums.ShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {

    Optional<Share> findByPublicToken(String publicToken);

    List<Share> findByShareStatusAndExpiresAtBefore(ShareStatus status, Instant time);

    List<Share> findByShareStatusIn(Collection<ShareStatus> statuses);

    @Modifying
    @Query("UPDATE Share s SET s.shareStatus = :status WHERE s.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") ShareStatus status);

    @Modifying
    @Query("UPDATE Share s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :id")
    void incrementDownloadCount(@Param("id") Long id);

    @Query("SELECT s FROM Share s WHERE s.shareStatus = 'ACTIVE' AND s.expiresAt < :now")
    List<Share> findExpiredActiveShares(@Param("now") Instant now);
}
