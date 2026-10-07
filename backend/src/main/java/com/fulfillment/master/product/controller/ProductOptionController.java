package com.fulfillment.master.product.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.master.product.dto.ProductOption;
import com.fulfillment.master.product.service.ProductOptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products/{productId}/options")
public class ProductOptionController {
    private final ProductOptionService service;
    public ProductOptionController(ProductOptionService service) { this.service = service; }
    public record SaveRequest(@NotNull @Size(max = 500) List<@NotNull @Valid ProductOption> options) {}
    @GetMapping
    public ApiResponse<List<ProductOption>> list(@PathVariable String productId) {
        return ApiResponse.ok(service.list(CurrentUser.require(), productId));
    }
    @PutMapping
    public ApiResponse<List<ProductOption>> save(@PathVariable String productId, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(CurrentUser.require(), productId, request.options()));
    }
}
