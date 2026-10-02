package com.hookledger.api;

import com.hookledger.service.LedgerService;
import com.hookledger.service.SettlementGraphException;
import com.hookledger.service.InvalidMoneyEventException;
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

    @ExceptionHandler(SettlementGraphException.class)
    public ResponseEntity<Map<String, String>> handleSettlementGraph(SettlementGraphException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
