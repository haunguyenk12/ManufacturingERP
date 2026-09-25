package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemResponse;
import com.erp.manufacturing.module.inventory.service.ItemService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link ItemController} (`D7b`, group B) – locks the
 * {@code {code,result,message}} envelope (`.claude/rules/error-handling.md` §5.1).
 *
 * <p>Item master data is where §5.3 deliberately <b>keeps</b> {@code OPERATION_NOT_ALLOWED} at 422:
 * an inactive company is bad <em>input data</em>, not a document in the wrong state. That branch is
 * asserted here so the next "let's make the codes consistent" pass cannot quietly promote it to 409.
 *
 * <p>Permission checks (`@PreAuthorize`) live in the service layer (C1) and are covered by
 * {@code InventoryMethodSecurityTest} – not re-tested here.
 */
@WebMvcTest(controllers = ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemController – response envelope contract")
class ItemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ItemService itemService;

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

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID    = UUID.randomUUID();

    private ItemResponse sampleResponse() {
        return new ItemResponse(ITEM_ID, COMPANY_ID, "MAT-001", "Steel Sheet",
                "RAW_MATERIAL", "KG", false, false, "ACTIVE", Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the created item")
    void create_validRequest_returns201Created() throws Exception {
        when(itemService.createItem(eq(COMPANY_ID), any(ItemCreateRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/companies/" + COMPANY_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MAT-001","name":"Steel Sheet","type":"RAW_MATERIAL",
                                 "unit":"KG","lotTracked":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("MAT-001"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("create: blank code returns 400 VALIDATION_ERROR and reports the field twice "
            + "(§5.1 — errors is an array, one field may violate several constraints)")
    void create_blankCode_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/v1/companies/" + COMPANY_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","name":"Steel Sheet","type":"RAW_MATERIAL",
                                 "unit":"KG","lotTracked":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[1].field").value("code"))
                .andExpect(jsonPath("$.result").doesNotExist());

        // R5: @Valid rejects the payload before the controller delegates
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("create: inactive company stays 422 OPERATION_NOT_ALLOWED (§5.3 — master data, "
            + "not a document state conflict)")
    void create_inactiveCompany_returns422ResourceInactive() throws Exception {
        when(itemService.createItem(eq(COMPANY_ID), any(ItemCreateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Cannot create item under inactive company: " + COMPANY_ID));

        mockMvc.perform(post("/v1/companies/" + COMPANY_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MAT-001","name":"Steel Sheet","type":"RAW_MATERIAL",
                                 "unit":"KG","lotTracked":false}
                                """))
                .andExpect(status().is(BusinessErrorCode.RESOURCE_INACTIVE.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.RESOURCE_INACTIVE.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown item returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        when(itemService.getItem(ITEM_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item not found: " + ITEM_ID));

        mockMvc.perform(get("/v1/items/" + ITEM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(itemService.listItems(eq(COMPANY_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/companies/" + COMPANY_ID + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("MAT-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("list: size beyond the A4 ceiling is clamped to 100 before it reaches the service "
            + "(`D7b.3` — proves best-practices.md A4 is enforced, not just documented)")
    void list_sizeAboveMax_isClampedToHundred() throws Exception {
        when(itemService.listItems(eq(COMPANY_ID), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/v1/companies/" + COMPANY_ID + "/items").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(itemService).listItems(eq(COMPANY_ID), pageable.capture());
        // The literal 100 is the number best-practices.md A4 promises clients, so it is asserted
        // literally: comparing against PageableFactory.MAX_PAGE_SIZE would move with the constant
        // and stay green even if the ceiling were raised (R6).
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("deactivate: returns 200 with a null result payload (§5.8 DELETE pattern)")
    void deactivate_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/v1/items/" + ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(itemService).deactivateItem(ITEM_ID);
    }

    @Test
    @DisplayName("activate: returns 200 with the ACTIVE item")
    void activate_returns200WithTheActivatedItem() throws Exception {
        when(itemService.activateItem(ITEM_ID)).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/items/" + ITEM_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));

        verify(itemService).activateItem(ITEM_ID);
    }

    @Test
    @DisplayName("activate: inactive parent company stays 422 OPERATION_NOT_ALLOWED (§5.3 — master data, "
            + "not a document state conflict)")
    void activate_inactiveCompany_returns422ResourceInactive() throws Exception {
        when(itemService.activateItem(ITEM_ID))
                .thenThrow(new AppException(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Cannot activate an item while its company is inactive: " + ITEM_ID));

        mockMvc.perform(post("/v1/items/" + ITEM_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.RESOURCE_INACTIVE.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.RESOURCE_INACTIVE.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }
}
