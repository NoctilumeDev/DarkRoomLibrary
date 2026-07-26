package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.StoredFilePageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.web.view.StoredFileView;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface FileStorageService {

    ApiResponse<String> upload(MultipartFile file);

    boolean bindSingle(String fileUrl, FileReferenceType refType, Integer refId);

    boolean bindFromHtml(String html, FileReferenceType refType, Integer refId);

    void releaseReference(FileReferenceType refType, Integer refId);

    void releaseReferences(FileReferenceType refType, List<Integer> refIds);

    void releaseUserBusinessFiles(List<Integer> userIds);

    String toDownloadUrl(String fileUrl);

    void writePublicFile(String fileName, HttpServletResponse response) throws IOException;

    void writeDownload(String fileName, HttpServletResponse response) throws IOException;

    ApiResponse<List<StoredFileView>> query(StoredFilePageQuery dto);

    ApiResponse<Void> deleteUnbound(String fileName);

    ApiResponse<Map<String, Object>> cleanupNow();
}
