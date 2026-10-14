package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import com.erp.manufacturing.module.sales.dto.CustomerUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.CustomerMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Customer master data (G2-1). Company-scoped, same lifecycle shape as {@code SupplierService} plus an
 * explicit {@code activate}, which refuses while the owning company is {@code INACTIVE} (B108).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9._-]+$");
    private static final int CODE_MAX_LENGTH = 100;

    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final OrganizationLookupService organizationLookupService;
    private final CustomerMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_CUSTOMER_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.CUSTOMER_CREATED, entityType = "Customer", entityIdExpression = "customerId.toString()",
               companyId = "#result?.companyId()")
    public CustomerResponse create(UUID companyId, CustomerCreateRequest request) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        String code = normalizeCode(request.code());
        if (customerRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Customer code", code);
        }
        Customer customer = Customer.builder()
                .company(company)
                .code(code)
                .name(requireName(request.name()))
                .contactName(trimToNull(request.contactName()))
                .email(trimToNull(request.email()))
                .phone(trimToNull(request.phone()))
                .address(trimToNull(request.address()))
                .notes(trimToNull(request.notes()))
                .build();
        return mapper.toResponse(customerRepository.save(customer));
    }

    /**
     * Lists customers of an existing company regardless of the company's status — reading the
     * catalogue of a deactivated company is legitimate, writing to it is not (see {@link #create}).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_CUSTOMER_READ', 'COMPANY', #companyId)")
    public PageResult<CustomerResponse> list(UUID companyId, CustomerStatus status, String search, Pageable pageable) {
        ensureCompanyExists(companyId);
        return PageResult.from(customerRepository.search(companyId, status, trimToNull(search), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasCustomerAccess(authentication, 'PERM_CUSTOMER_READ', #customerId)")
    public CustomerResponse get(UUID customerId) {
        return mapper.toResponse(findCustomer(customerId));
    }

    /**
     * {@code null} keeps a field; for the optional text fields a blank string clears it
     * (NEXT_PHASE_PLAN.md §9.2 D3 — the repo-wide PATCH convention, not FE's "null clears").
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasCustomerAccess(authentication, 'PERM_CUSTOMER_MANAGE', #customerId)")
    @Auditable(action = AuditAction.CUSTOMER_UPDATED, entityType = "Customer", entityIdExpression = "customerId.toString()",
               companyId = "#result?.companyId()")
    public CustomerResponse update(UUID customerId, CustomerUpdateRequest request) {
        Customer customer = findCustomer(customerId);
        if (request.name() != null) {
            customer.setName(requireName(request.name()));
        }
        if (request.contactName() != null) {
            customer.setContactName(trimToNull(request.contactName()));
        }
        if (request.email() != null) {
            customer.setEmail(trimToNull(request.email()));
        }
        if (request.phone() != null) {
            customer.setPhone(trimToNull(request.phone()));
        }
        if (request.address() != null) {
            customer.setAddress(trimToNull(request.address()));
        }
        if (request.notes() != null) {
            customer.setNotes(trimToNull(request.notes()));
        }
        return mapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasCustomerAccess(authentication, 'PERM_CUSTOMER_MANAGE', #customerId)")
    @Auditable(action = AuditAction.CUSTOMER_ACTIVATED, entityType = "Customer", entityIdExpression = "customerId.toString()",
               companyId = "#result?.companyId()")
    public CustomerResponse activate(UUID customerId) {
        Customer customer = findCustomer(customerId);
        if (!customer.getCompany().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Cannot activate a customer while its company is inactive: " + customerId);
        }
        customer.activate();
        return mapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasCustomerAccess(authentication, 'PERM_CUSTOMER_MANAGE', #customerId)")
    @Auditable(action = AuditAction.CUSTOMER_DEACTIVATED, entityType = "Customer", entityIdExpression = "customerId.toString()",
               companyId = "#result?.companyId()")
    public CustomerResponse deactivate(UUID customerId) {
        Customer customer = findCustomer(customerId);
        customer.deactivate();
        return mapper.toResponse(customerRepository.save(customer));
    }

    private Customer findCustomer(UUID customerId) {
        return customerRepository.findWithCompanyByCustomerId(customerId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Customer", customerId));
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId);
        }
    }

    /**
     * Normalises before validating, unlike the {@code @Pattern} on Item/Plant/Warehouse codes, so
     * padding and lower case are corrected instead of rejected. What is still invalid after that is a
     * field error on {@code code}, not a generic message.
     */
    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > CODE_MAX_LENGTH || !CODE_PATTERN.matcher(code).matches()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("code",
                    "must be 1-" + CODE_MAX_LENGTH + " characters of A-Z, 0-9, '.', '_' or '-'"));
        }
        return code;
    }

    private String requireName(String value) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("name", "must not be blank"));
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
