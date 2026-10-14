package com.erp.manufacturing.module.sales.mapper;

import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getCompany().getCompanyId(),
                customer.getCode(),
                customer.getName(),
                customer.getContactName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getNotes(),
                customer.getStatus().name(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
