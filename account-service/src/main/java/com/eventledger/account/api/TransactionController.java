package com.eventledger.account.api;

import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.api.dto.ApplyTransactionResponse;
import com.eventledger.account.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApplyTransactionResponse> apply(@Valid @RequestBody ApplyTransactionRequest request) {
        ApplyTransactionResponse response = transactionService.apply(request);
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
