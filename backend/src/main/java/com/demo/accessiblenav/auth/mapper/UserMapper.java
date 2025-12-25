package com.demo.accessiblenav.auth.mapper;

import com.demo.accessiblenav.auth.UserAccount;
import com.demo.accessiblenav.auth.dto.UserSummaryDto;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 用户对象映射器
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * UserAccount转UserSummaryDto
     */
    UserSummaryDto toSummaryDto(UserAccount user);

    /**
     * 批量转换
     */
    List<UserSummaryDto> toSummaryDtoList(List<UserAccount> users);
}
