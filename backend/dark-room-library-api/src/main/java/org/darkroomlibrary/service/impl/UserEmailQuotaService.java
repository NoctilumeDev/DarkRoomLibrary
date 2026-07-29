package org.darkroomlibrary.service.impl;

import jakarta.annotation.Resource;
import org.darkroomlibrary.mapper.UserEmailQuotaMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserEmailQuotaService {

    public static final int MAX_ACCOUNTS_PER_EMAIL = 3;
    public static final String LIMIT_MESSAGE = "同一邮箱最多关联 3 个账号，请更换邮箱";

    @Resource
    private UserEmailQuotaMapper quotaMapper;

    @Resource
    private UserMapper userMapper;

    public String normalize(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveNewAccount(String email) {
        String normalized = requireEmail(email);
        if (initializeAndLockQuota(normalized) >= MAX_ACCOUNTS_PER_EMAIL) {
            return false;
        }
        return quotaMapper.incrementIfBelowLimit(normalized, MAX_ACCOUNTS_PER_EMAIL) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean moveAccount(String currentEmail, String requestedEmail) {
        String current = normalize(currentEmail);
        String requested = requireEmail(requestedEmail);
        if (Objects.equals(current, requested)) {
            return true;
        }

        List<String> lockOrder = java.util.stream.Stream.of(current, requested)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<String, Integer> lockedCounts = new java.util.LinkedHashMap<>();
        for (String email : lockOrder) {
            lockedCounts.put(email, initializeAndLockQuota(email));
        }

        if (lockedCounts.getOrDefault(requested, 0) >= MAX_ACCOUNTS_PER_EMAIL) {
            return false;
        }
        if (current != null && quotaMapper.decrement(current, 1) != 1) {
            throw new IllegalStateException("邮箱账号配额与用户数据不一致");
        }
        if (quotaMapper.incrementIfBelowLimit(requested, MAX_ACCOUNTS_PER_EMAIL) != 1) {
            throw new IllegalStateException("邮箱账号配额并发更新失败");
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAccounts(Collection<String> emails) {
        Map<String, Long> releaseCounts = emails.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<String> lockOrder = releaseCounts.keySet().stream().sorted().toList();

        for (String email : lockOrder) {
            initializeAndLockQuota(email);
        }
        for (String email : lockOrder) {
            int amount = Math.toIntExact(releaseCounts.get(email));
            if (quotaMapper.decrement(email, amount) != 1) {
                throw new IllegalStateException("邮箱账号配额与待删除用户不一致");
            }
        }
    }

    private int lockCount(String email) {
        Integer count = quotaMapper.findCountForUpdate(email);
        if (count == null) {
            throw new IllegalStateException("邮箱账号配额初始化失败");
        }
        return count;
    }

    private int initializeAndLockQuota(String email) {
        quotaMapper.ensureExists(email);
        int lockedCount = lockCount(email);
        if (lockedCount > 0) {
            return lockedCount;
        }
        int existingAccounts = Math.min(
                userMapper.countByNormalizedEmail(email),
                MAX_ACCOUNTS_PER_EMAIL);
        if (existingAccounts > 0 && quotaMapper.setCount(email, existingAccounts) != 1) {
            throw new IllegalStateException("邮箱账号配额初始化失败");
        }
        return existingAccounts;
    }

    private String requireEmail(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return normalized;
    }
}
