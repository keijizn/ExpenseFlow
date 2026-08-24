package com.finanzero.controller;

import com.finanzero.dto.DashboardSummary;
import com.finanzero.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping
    public DashboardSummary summary(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        return service.summary(month == null ? now.getMonthValue() : month, year == null ? now.getYear() : year);
    }
}
