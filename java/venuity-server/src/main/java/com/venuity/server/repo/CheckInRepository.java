package com.venuity.server.repo;

import com.venuity.server.domain.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
}
