package com.hookledger.api;

import com.hookledger.service.BankReconciliationException;
import com.hookledger.service.IdempotencyConflictException;
import com.hookledger.service.InvalidMoneyEventException;
import com.hookledger.service.PeriodClosedException;
import com.hookledger.service.SettlementGraphException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidMoneyEventException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPayload(InvalidMoneyEventException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BankReconciliationException.class)
    public ResponseEntity<Map<String, String>> handleBankReconciliation(BankReconciliationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SettlementGraphException.class)
    public ResponseEntity<Map<String, String>> handleSettlementGraph(SettlementGraphException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PeriodClosedException.class)
    public ResponseEntity<Map<String, String>> handlePeriodClosed(PeriodClosedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
