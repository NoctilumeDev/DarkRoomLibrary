package org.darkroomlibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserEmailQuotaMapper {

    int ensureExists(@Param("email") String email);

    int setCount(@Param("email") String email, @Param("accountCount") int accountCount);

    Integer findCountForUpdate(@Param("email") String email);

    int incrementIfBelowLimit(@Param("email") String email,
                              @Param("maxAccounts") int maxAccounts);

    int decrement(@Param("email") String email, @Param("amount") int amount);
}
