package org.darkroomlibrary.service.support;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationSourceVersionServiceTest {

    @Mock
    private CacheService cacheService;

    private RecommendationSourceVersionService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationSourceVersionService();
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
    }

    @Test
    void combinesSharedAndReaderVersionsWithoutLoadingRecommendationSources() {
        when(cacheService.getString("recommendation:source:global"))
                .thenReturn(Optional.of("global-v3"));
        when(cacheService.getString("recommendation:source:user:42"))
                .thenReturn(Optional.of("reader-v7"));

        assertEquals(Optional.of("global-v3|reader-v7"), service.currentSeed(42));
    }

    @Test
    void fallsBackWhenTheSharedCacheIsUnavailable() {
        when(cacheService.getString("recommendation:source:global"))
                .thenReturn(Optional.empty());
        when(cacheService.setIfAbsent(
                org.mockito.ArgumentMatchers.eq("recommendation:source:global"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertTrue(service.currentSeed(42).isEmpty());
    }

    @Test
    void invalidatesBothScopesAfterACommittedSourceMutation() {
        service.invalidateUserAndGlobalAfterCommit(42);

        verify(cacheService).delete("recommendation:source:global");
        verify(cacheService).delete("recommendation:source:user:42");
    }
}
