package org.darkroomlibrary.aop;

import org.darkroomlibrary.web.dto.query.PageQuery;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PageQueryNormalizationAspect {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 100_000;

    @Around("@annotation(org.darkroomlibrary.aop.NormalizePageQuery)")
    public Object normalize(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof PageQuery query) {
                applyOffset(query);
            }
        }
        return joinPoint.proceed(args);
    }

    private void applyOffset(PageQuery query) {
        int page = query.getCurrent() == null || query.getCurrent() < DEFAULT_PAGE
                ? DEFAULT_PAGE
                : Math.min(query.getCurrent(), MAX_PAGE);
        int size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_SIZE
                : Math.min(query.getSize(), MAX_SIZE);
        query.setCurrent((page - 1) * size);
        query.setSize(size);
    }
}
