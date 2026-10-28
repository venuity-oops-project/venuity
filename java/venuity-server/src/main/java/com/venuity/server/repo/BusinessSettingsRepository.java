package com.venuity.server.repo;

import com.venuity.server.domain.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Byte> {
}
