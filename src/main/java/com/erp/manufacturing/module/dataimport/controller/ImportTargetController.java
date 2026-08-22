package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportTargetResponse;
import com.erp.manufacturing.module.dataimport.service.ImportTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Data Import Targets", description = "Importable master-data types and templates")
public class ImportTargetController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportTargetService service;

    @GetMapping("/v1/import-targets")
    @Operation(summary = "List supported import targets")
    public ResponseEntity<ApiResponse<List<ImportTargetResponse>>> list(
            @RequestParam(required = false) UUID companyId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(companyId)));
    }

    @GetMapping("/v1/import-targets/{type}/fields")
    @Operation(summary = "List fields accepted by an import target")
    public ResponseEntity<ApiResponse<ImportTargetResponse>> fields(
            @PathVariable ImportTargetType type,
            @RequestParam(required = false) UUID companyId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(type, companyId)));
    }

    @GetMapping("/v1/import-targets/{type}/template")
    @Operation(summary = "Download the standard .xlsx template for an import target")
    public ResponseEntity<byte[]> template(
            @PathVariable ImportTargetType type,
            @RequestParam(required = false) UUID companyId) {
        String filename = "import-" + type.name().toLowerCase(Locale.ROOT) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(service.template(type, companyId));
    }
}
