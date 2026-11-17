package com.sharedrop.repository;

import com.sharedrop.entity.SharedTextEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SharedTextRepository extends JpaRepository<SharedTextEntry, Long> {
    List<SharedTextEntry> findByShareIdOrderByCreatedAtAsc(Long shareId);
}
