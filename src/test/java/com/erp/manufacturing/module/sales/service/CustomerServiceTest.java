package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import com.erp.manufacturing.module.sales.dto.CustomerUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.CustomerMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService tests")
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock OrganizationLookupService organizationLookupService;

    CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, organizationLookupService,
                new CustomerMapper());
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_normalisesCodeBeforeValidatingAndStoresBlankOptionalsAsNull() {
        Company company = company(OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(customerRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "PARTNER-01"))
                .thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(UUID.randomUUID());
            return customer;
        });

        CustomerResponse response = service.create(company.getCompanyId(), new CustomerCreateRequest(
                " partner-01 ", "  Acme Retail  ", "   ", " buyer@acme.test ", "", null, "  note  "));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("PARTNER-01");
        assertThat(saved.getValue().getName()).isEqualTo("Acme Retail");
        assertThat(saved.getValue().getContactName()).isNull();
        assertThat(saved.getValue().getEmail()).isEqualTo("buyer@acme.test");
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getAddress()).isNull();
        assertThat(saved.getValue().getNotes()).isEqualTo("note");
        assertThat(saved.getValue().getCompany()).isSameAs(company);
        assertThat(response.code()).isEqualTo("PARTNER-01");
        assertThat(response.companyId()).isEqualTo(company.getCompanyId());
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE.name());
    }

    @Test
    void create_codeStillInvalidAfterNormalisation_isAFieldErrorOnCodeAndNothingIsWritten() {
        Company company = company(OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);

        assertThatThrownBy(() -> service.create(company.getCompanyId(), new CustomerCreateRequest(
                "acme retail!", "Acme", null, null, null, null, null)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException multi = (MultiErrorException) ex;
                    assertThat(multi.getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(multi.getFieldErrors()).containsOnlyKeys("code");
                });

        verify(customerRepository, never()).existsByCompanyCompanyIdAndCode(any(), anyString());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_codeLongerThanOneHundredCharacters_isAFieldErrorOnCode() {
        Company company = company(OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);

        assertThatThrownBy(() -> service.create(company.getCompanyId(), new CustomerCreateRequest(
                "A".repeat(101), "Acme", null, null, null, null, null)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("code"));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_codeOfExactlyOneHundredCharacters_isAccepted() {
        Company company = company(OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = service.create(company.getCompanyId(), new CustomerCreateRequest(
                "a".repeat(100), "Acme", null, null, null, null, null));

        assertThat(response.code()).isEqualTo("A".repeat(100));
    }

    @Test
    void create_duplicateCodeInTheSameCompany_throwsAlreadyExistsBeforeSaving() {
        Company company = company(OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(customerRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "CUS-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(company.getCompanyId(), new CustomerCreateRequest(
                "cus-001", "Acme", null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_underAnInactiveCompany_throwsResourceInactiveBeforeTouchingCustomers() {
        UUID companyId = UUID.randomUUID();
        when(organizationLookupService.getActiveCompany(companyId))
                .thenThrow(new AppException(BusinessErrorCode.RESOURCE_INACTIVE, "Inactive company"));

        assertThatThrownBy(() -> service.create(companyId, new CustomerCreateRequest(
                "CUS-001", "Acme", null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verifyNoInteractions(customerRepository);
    }

    // ── list ──────────────────────────────────────────────────────────────

    @Test
    void list_unknownCompany_throwsNotFoundBeforeSearching() {
        UUID companyId = UUID.randomUUID();
        when(organizationLookupService.companyExists(companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(companyId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(customerRepository);
    }

    @Test
    void list_trimsTheSearchTermAndForwardsTheStatusInOneQuery() {
        UUID companyId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(organizationLookupService.companyExists(companyId)).thenReturn(true);
        when(customerRepository.search(companyId, CustomerStatus.INACTIVE, "acme", pageable))
                .thenReturn(Page.empty(pageable));

        service.list(companyId, CustomerStatus.INACTIVE, "  acme ", pageable);

        verify(customerRepository, times(1)).search(companyId, CustomerStatus.INACTIVE, "acme", pageable);
    }

    @Test
    void list_blankSearchTermIsForwardedAsNull() {
        UUID companyId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(organizationLookupService.companyExists(companyId)).thenReturn(true);
        when(customerRepository.search(eq(companyId), isNull(), isNull(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.list(companyId, null, "   ", pageable);

        verify(customerRepository).search(eq(companyId), isNull(), isNull(), eq(pageable));
    }

    // ── get / update ──────────────────────────────────────────────────────

    @Test
    void get_unknownCustomer_throwsNotFound() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findWithCompanyByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(customerId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void update_nullKeepsTheCurrentValueAndBlankClearsAnOptionalField() {
        Customer customer = fullCustomer(company(OrganizationStatus.ACTIVE), CustomerStatus.ACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = service.update(customer.getCustomerId(), new CustomerUpdateRequest(
                null, "", "   ", null, " 9 New Street ", null));

        assertThat(response.name()).isEqualTo("Acme Retail");          // null ⇒ kept
        assertThat(response.contactName()).isNull();                      // ""   ⇒ cleared
        assertThat(response.email()).isNull();                            // "  " ⇒ cleared
        assertThat(response.phone()).isEqualTo("0900000000");            // null ⇒ kept
        assertThat(response.address()).isEqualTo("9 New Street");        // value ⇒ trimmed + set
        assertThat(response.notes()).isEqualTo("VIP");                    // null ⇒ kept
        assertThat(response.code()).isEqualTo("CUS-001");
    }

    @Test
    void update_blankName_isAFieldErrorOnNameAndNothingIsSaved() {
        Customer customer = fullCustomer(company(OrganizationStatus.ACTIVE), CustomerStatus.ACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.update(customer.getCustomerId(), new CustomerUpdateRequest(
                "  ", null, null, null, null, null)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException multi = (MultiErrorException) ex;
                    assertThat(multi.getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(multi.getFieldErrors()).containsOnlyKeys("name");
                });

        assertThat(customer.getName()).isEqualTo("Acme Retail");
        verify(customerRepository, never()).save(any());
    }

    // ── lifecycle ─────────────────────────────────────────────────────────

    @Test
    void activate_whileTheCompanyIsInactive_throwsResourceInactiveAndStaysInactive() {
        Customer customer = fullCustomer(company(OrganizationStatus.INACTIVE), CustomerStatus.INACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.activate(customer.getCustomerId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void activate_inactiveCustomer_becomesActive() {
        Customer customer = fullCustomer(company(OrganizationStatus.ACTIVE), CustomerStatus.INACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        assertThat(service.activate(customer.getCustomerId()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void activate_alreadyActiveCustomer_isIdempotent() {
        Customer customer = fullCustomer(company(OrganizationStatus.ACTIVE), CustomerStatus.ACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        assertThat(service.activate(customer.getCustomerId()).status()).isEqualTo("ACTIVE");
        assertThat(service.activate(customer.getCustomerId()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void deactivate_isIdempotentAndAllowedEvenUnderAnInactiveCompany() {
        Customer customer = fullCustomer(company(OrganizationStatus.INACTIVE), CustomerStatus.ACTIVE);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        assertThat(service.deactivate(customer.getCustomerId()).status()).isEqualTo("INACTIVE");
        assertThat(service.deactivate(customer.getCustomerId()).status()).isEqualTo("INACTIVE");
    }

    private Customer fullCustomer(Company company, CustomerStatus status) {
        return Customer.builder()
                .customerId(UUID.randomUUID())
                .company(company)
                .code("CUS-001")
                .name("Acme Retail")
                .contactName("Jane Buyer")
                .email("buyer@acme.test")
                .phone("0900000000")
                .address("1 Old Street")
                .notes("VIP")
                .status(status)
                .build();
    }

    private Company company(OrganizationStatus status) {
        return Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(status)
                .build();
    }
}
