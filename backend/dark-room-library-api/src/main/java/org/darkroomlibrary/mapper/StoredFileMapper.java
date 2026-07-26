package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.StoredFilePageQuery;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.web.view.StoredFileView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFile> {

    List<StoredFileView> query(StoredFilePageQuery dto);

    Integer queryCount(StoredFilePageQuery dto);

    List<StoredFile> findByReference(@Param("refType") String refType,
                                     @Param("refId") Integer refId);

    int bind(@Param("fileName") String fileName,
             @Param("uploaderId") Integer uploaderId,
             @Param("refType") String refType,
             @Param("refId") Integer refId,
             @Param("now") LocalDateTime now);

    int markDeletePending(@Param("fileNames") List<String> fileNames,
                          @Param("now") LocalDateTime now);

    List<StoredFile> findCleanupCandidates(@Param("temporaryCutoff") LocalDateTime temporaryCutoff,
                                           @Param("limit") Integer limit);

    Integer countLegacyReferences(@Param("fileName") String fileName);

    List<StoredFile> findUserBusinessFiles(@Param("userIds") List<Integer> userIds);
}
