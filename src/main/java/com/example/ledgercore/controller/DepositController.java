package com.example.ledgercore.controller;
import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller responsible for deposit operations.
 *
 * <p>
 *     Expose the API endpoints used by authenticated customers
 *     to deposit money into their accounts.
 * </p>
 *
 * <p>
 *     The controller is intentionally thin.It handles HTTP
 *     request/response concerns while the actual deposit business
 *     logic is delegated to {@link DepositService}.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@RestController
@RequestMapping("/transactions")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService)
    {
        this.depositService=depositService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request
            )
    {
        TransactionResponse response=depositService.deposit(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
