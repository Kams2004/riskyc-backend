package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeviceSaleBatchRequest(@NotEmpty @Size(max = 200) @Valid List<DeviceSaleRequest> sales) {
}
