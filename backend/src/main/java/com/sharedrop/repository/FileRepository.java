package com.sharedrop.repository;

import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<SharedFile, Long> {

    List<SharedFile> findByShareId(Long shareId);

    Optional<SharedFile> findByIdAndShareId(Long id, Long shareId);

    List<SharedFile> findByUploadStatus(UploadStatus status);
}
