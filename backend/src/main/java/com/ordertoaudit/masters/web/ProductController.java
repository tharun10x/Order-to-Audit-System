package com.ordertoaudit.masters.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.masters.service.ProductService;
import com.ordertoaudit.security.Roles;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PreAuthorize(Roles.ORDER_ENTRY)
    @GetMapping
    public List<ProductResponse> list() {
        return productService.list();
    }
}
