package com.erp.manufacturing.module.user.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.user.dto.CreateUserRequest;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link UserController} (`D7b`, group B).
 *
 * <p>Two things this locks that no other test does: the duplicate-username branch resolves to
 * {@code USERNAME_ALREADY_EXISTS} rather than the generic {@code RESOURCE_ALREADY_EXISTS} (both are
 * 409, so only the {@code $.code} assertion can tell them apart), and the response body never
 * carries a password field.
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController – response envelope contract")
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserService userService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean
    IpExtractor ipExtractor;
    @MockBean
    JwtTokenProvider jwtTokenProvider;
    @MockBean
    TokenStoreService tokenStoreService;
    @MockBean
    UserDetailsService userDetailsService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;
    @MockBean
    RateLimitProperties rateLimitProperties;

    private static final UUID USER_ID = UUID.randomUUID();

    private UserResponse sampleResponse() {
        return new UserResponse(USER_ID, "jdoe", "jdoe@example.test", "ACTIVE",
                Set.of("OPERATOR"), Instant.now(), Instant.now());
    }

    private String createBody(String username) {
        return """
                {"username":"%s","email":"jdoe@example.test","password":"Str0ng!Pass"}
                """.formatted(username);
    }

    @Test
    @DisplayName("create: valid request returns 201 and never echoes the password back")
    void create_validRequest_returns201WithoutPassword() throws Exception {
        when(userService.create(any(CreateUserRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("jdoe")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.result.username").value("jdoe"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.password").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate username returns 409 USERNAME_ALREADY_EXISTS, not the generic "
            + "RESOURCE_ALREADY_EXISTS (both are 409 — only $.code separates them)")
    void create_duplicateUsername_returns409UsernameAlreadyExists() throws Exception {
        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.USERNAME_ALREADY_EXISTS,
                        "Username already taken: jdoe"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("jdoe")))
                .andExpect(status().is(ValidationErrorCode.USERNAME_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.USERNAME_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: username violating the character pattern returns 400 with the field name")
    void create_invalidUsername_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("has spaces!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("get: unknown user returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        when(userService.findById(USER_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "User not found: " + USER_ID));

        mockMvc.perform(get("/api/v1/users/" + USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(userService.findAll(any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].username").value("jdoe"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("list: size=0 falls back to the A4 default of 20 rather than requesting an empty page "
            + "(`D7b.3`)")
    void list_nonPositiveSize_fallsBackToDefault() throws Exception {
        when(userService.findAll(any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/users").param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).findAll(pageable.capture());
        // Literal 20 for the same reason as the max-size assertion in ItemControllerTest (R6).
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("delete: returns 200 with a null result payload (§5.8 DELETE pattern)")
    void delete_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(userService).delete(USER_ID);
    }
}
