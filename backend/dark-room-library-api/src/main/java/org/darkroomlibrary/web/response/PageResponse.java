package org.darkroomlibrary.web.response;

/**
 * Response envelope for offset-based queries.
 */
public final class PageResponse<T> extends ApiResponse<T> {

    private Integer total;

    private PageResponse(T data, Integer total) {
        super(ApiStatusCode.SUCCESS.value(), "查询成功", data);
        this.total = total == null ? 0 : Math.max(total, 0);
    }

    public static <T> ApiResponse<T> success(T data, Integer total) {
        return new PageResponse<>(data, total);
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
