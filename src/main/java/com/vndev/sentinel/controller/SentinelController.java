package com.vndev.sentinel.controller;

import com.vndev.sentinel.dto.TransactionDTO;
import com.vndev.sentinel.service.SentinelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sentinel")
@RequiredArgsConstructor
public class SentinelController {
    private final SentinelService sentinelService;

    @PostMapping("/analyze")
    public ResponseEntity<?> processTransaction(@RequestBody TransactionDTO transaction) {
        boolean isApproved = sentinelService.analyzeTransaction(transaction);

        if (isApproved) {
            return ResponseEntity.ok(Map.of(
               "status", "APPROVED", "message", "Transação processada com sucesso."
            ));
        } else {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "DENIED", "reason", "Suspeita de fraude: Alta frequência de transações."
            ));
        }
    }
}