package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthLookupTest {

    @Mock
    private UserMapper userMapper;

    private UserAuthLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new UserAuthLookup();
        ReflectionTestUtils.setField(lookup, "userMapper", userMapper);
    }

    @Test
    void authorizationStateIsReadFreshForEveryRequest() {
        User normal = User.builder()
                .id(7)
                .userName("reader")
                .userRole(0)
                .authVersion(4)
                .accountStatus(AccountStatus.NORMAL.code())
                .isLogin(false)
                .build();
        User frozen = User.builder()
                .id(7)
                .userName("reader")
                .userRole(0)
                .authVersion(5)
                .accountStatus(AccountStatus.FROZEN.code())
                .isLogin(true)
                .build();
        when(userMapper.getById(7)).thenReturn(normal, frozen);

        assertFalse(lookup.getActiveUser(7).orElseThrow().getDisabled());
        assertTrue(lookup.getActiveUser(7).orElseThrow().getDisabled());
    }
}
