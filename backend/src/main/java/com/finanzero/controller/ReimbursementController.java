package com.finanzero.controller;

import com.finanzero.dto.BatchReimbursementSendRequest;
import com.finanzero.dto.ReimbursementReceiveRequest;
import com.finanzero.dto.SendReimbursementRequest;
import com.finanzero.model.FinanceTransaction;
import com.finanzero.model.ReimbursementStatus;
import com.finanzero.service.ReimbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reimbursements")
@RequiredArgsConstructor
public class ReimbursementController {
    private final ReimbursementService service;

    @GetMapping
    public List<FinanceTransaction> list(@RequestParam(required = false) ReimbursementStatus status) {
        return service.list(status);
    }

    @PostMapping("/{transactionId}/send")
    public FinanceTransaction send(@PathVariable Long transactionId, @RequestBody(required = false) SendReimbursementRequest request) {
        System.out.println("Solicitação de envio de reembolso recebida. ID=" + transactionId + ", email=" + (request == null ? null : request.email()));
        return service.send(transactionId, request);
    }

    @PostMapping("/send-batch")
    public List<FinanceTransaction> sendBatch(@RequestBody BatchReimbursementSendRequest request) {
        System.out.println("Solicitação de envio de reembolsos em lote recebida. IDs=" + (request == null ? null : request.transactionIds()) + ", email=" + (request == null ? null : request.email()));
        return service.sendBatch(request);
    }

    @PostMapping("/{transactionId}/received")
    public FinanceTransaction markReceived(@PathVariable Long transactionId, @RequestBody ReimbursementReceiveRequest request) {
        return service.markReceived(transactionId, request);
    }

    @PostMapping("/{transactionId}/reject")
    public FinanceTransaction reject(@PathVariable Long transactionId) {
        return service.reject(transactionId);
    }
}
