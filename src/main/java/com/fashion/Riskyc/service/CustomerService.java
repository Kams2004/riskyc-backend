package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.request.RegisterCustomerRequest;
import com.fashion.Riskyc.dto.response.CustomerResponse;
import com.fashion.Riskyc.entity.Customer;
import com.fashion.Riskyc.entity.CustomerStatus;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerResponse register(RegisterCustomerRequest request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Customer customer = customerRepository.saveAndFlush(Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(CustomerStatus.ACTIVE)
                .lastLogin(Instant.now())
                .build());
        return toResponse(customer);
    }

    public CustomerResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }
        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        customer.setLastLogin(Instant.now());
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponse updateStatus(UUID id, CustomerStatus status) {
        Customer customer = getOrThrow(id);
        customer.setStatus(status);
        return toResponse(customer);
    }

    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Customer", id);
        }
        customerRepository.deleteById(id);
    }

    private Customer getOrThrow(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), c.getStatus(), c.getCreatedAt(), c.getLastLogin());
    }
}
