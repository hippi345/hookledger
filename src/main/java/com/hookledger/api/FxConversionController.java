package com.hookledger.api;

import com.hookledger.service.FxConversionService;
import com.hookledger.service.FxConversionService.FxConversionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx-conversions")
@Tag(name = "FX conversions")
public class FxConversionController {

    private final FxConversionService fxConversionService;

    public FxConversionController(FxConversionService fxConversionService) {
        this.fxConversionService = fxConversionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert an amount between currencies at a stored rate (balanced legs)")
    public FxConversionResponse convert(@RequestBody FxConversionRequest request) {
        FxConversionResult result = fxConversionService.convert(
                request.id(),
                request.sourceAmountMinor(),
                request.sourceCurrency(),
                request.targetCurrency(),
                request.rate());
        return new FxConversionResponse(
                result.id(),
                result.rate(),
                result.sourceLeg().getAmountMinor(),
                result.sourceLeg().getCurrency(),
                result.targetLeg().getAmountMinor(),
                result.targetLeg().getCurrency(),
                EventResponse.from(result.sourceLeg()),
                EventResponse.from(result.targetLeg()));
    }
}
