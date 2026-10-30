package com.irvinsegura.wallet.audit.application;

import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLog;

    AuditLogService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public AuditLogRepository.AuditSlice search(UUID actorId, UUID targetId, int page, int size) {
        return auditLog.find(actorId, targetId, page, size);
    }
}
