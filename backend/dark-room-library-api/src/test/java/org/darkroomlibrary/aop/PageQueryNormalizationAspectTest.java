package org.darkroomlibrary.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageQueryNormalizationAspectTest {

    @Test
    void capsPageSizeAndDeepOffset() throws Throwable {
        PageQuery query = new PageQuery();
        query.setCurrent(Integer.MAX_VALUE);
        query.setSize(Integer.MAX_VALUE);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{query});
        when(joinPoint.proceed(any(Object[].class))).thenReturn(null);

        new PageQueryNormalizationAspect().normalize(joinPoint);

        assertEquals(100, query.getSize());
        assertEquals(100_000, query.getCurrent());
    }
}
