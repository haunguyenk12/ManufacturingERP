package com.erp.manufacturing.common.exception;

import com.erp.manufacturing.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EH-1: the container-level fallback must speak the same {@code {code,result,message}} envelope as
 * every other error path (error-handling.md 5.1). Before this controller existed, anything forwarded
 * to {@code /error} was answered by Spring Boot's {@code BasicErrorController} with
 * {@code {timestamp,status,error,path}} instead - a shape no client in this system parses.
 */
@DisplayName("ApiErrorController - container-level error envelope")
class ApiErrorControllerTest {

    private final ApiErrorController controller = new ApiErrorController();

    @Test
    @DisplayName("keeps the status the container decided and names it with an ErrorCode")
    void keepsTheContainerStatusAndNamesIt() {
        ResponseEntity<ApiResponse<Void>> response = controller.handleError(errorRequest(404));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND.code());
        assertThat(response.getBody().result()).isNull();
    }

    @Test
    @DisplayName("a server-side failure stays 500 INTERNAL_SERVER_ERROR")
    void serverFailure_staysInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = controller.handleError(errorRequest(500));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(BusinessErrorCode.INTERNAL_SERVER_ERROR.code());
    }

    /**
     * The status attribute is set by the container, not by this application, so it can be absent or a
     * value {@code HttpStatus} does not know. Falling back to 500 keeps the answer honest: something
     * went wrong on this side and the caller is not told it sent a bad request.
     */
    @Test
    @DisplayName("an absent or unknown container status falls back to 500, not to a made-up 4xx")
    void unknownContainerStatus_fallsBackTo500() {
        ResponseEntity<ApiResponse<Void>> noAttribute = controller.handleError(new MockHttpServletRequest());
        ResponseEntity<ApiResponse<Void>> unknownCode = controller.handleError(errorRequest(799));

        assertThat(noAttribute.getStatusCode().value()).isEqualTo(500);
        assertThat(unknownCode.getStatusCode().value()).isEqualTo(500);
        assertThat(unknownCode.getBody()).isNotNull();
        assertThat(unknownCode.getBody().code()).isEqualTo(BusinessErrorCode.INTERNAL_SERVER_ERROR.code());
    }

    private MockHttpServletRequest errorRequest(int status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/uoms");
        return request;
    }
}
