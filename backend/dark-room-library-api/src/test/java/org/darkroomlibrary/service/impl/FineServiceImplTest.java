package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.service.FineService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 罚款服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FineServiceImplTest extends BaseTest {

    @Resource
    private FineService fineService;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("按时归还 - 罚款为 0")
    void testCalculateFineOnTime() {
        LocalDateTime dueDate = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime returnTime = LocalDateTime.of(2026, 7, 10, 0, 0);
        BigDecimal fine = fineService.calculateFine(dueDate, returnTime);
        assertEquals(BigDecimal.ZERO, fine);
    }

    @Test
    @Order(2)
    @DisplayName("提前归还 - 罚款为 0")
    void testCalculateFineEarly() {
        LocalDateTime dueDate = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime returnTime = LocalDateTime.of(2026, 7, 5, 0, 0);
        BigDecimal fine = fineService.calculateFine(dueDate, returnTime);
        assertEquals(BigDecimal.ZERO, fine);
    }

    @Test
    @Order(3)
    @DisplayName("逾期 1 天 - 罚款 0.5 元")
    void testCalculateFineOneDay() {
        LocalDateTime dueDate = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime returnTime = LocalDateTime.of(2026, 7, 11, 0, 0);
        BigDecimal fine = fineService.calculateFine(dueDate, returnTime);
        assertEquals(new BigDecimal("0.50"), fine);
    }

    @Test
    @Order(4)
    @DisplayName("逾期 5 天 - 罚款 2.5 元")
    void testCalculateFineFiveDays() {
        LocalDateTime dueDate = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime returnTime = LocalDateTime.of(2026, 7, 15, 0, 0);
        BigDecimal fine = fineService.calculateFine(dueDate, returnTime);
        assertEquals(new BigDecimal("2.50"), fine);
    }

    @Test
    @Order(5)
    @DisplayName("空日期处理 - 返回 0")
    void testCalculateFineNullDates() {
        BigDecimal fine1 = fineService.calculateFine(null, LocalDateTime.now());
        assertEquals(BigDecimal.ZERO, fine1);

        BigDecimal fine2 = fineService.calculateFine(LocalDateTime.now(), null);
        assertEquals(BigDecimal.ZERO, fine2);
    }
}