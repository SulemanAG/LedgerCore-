package com.example.ledgercore.dto.request;


import com.example.ledgercore.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the information required to create a LedgerCore authentication
 * identity for an existing customer.
 *
 * <p>
 *     The password is supplied only through the request and is encoded by the
 *     LedgerCore persistence. System-generated fields such as userID, enabled state
 *     and customer relationships are not controlled directly by the client.
 * </p>
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Username cannot be blank")
    @Size(max=100,message = "Username cannot exceed 100 characters")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Size(min=8,max=100,
            message = "Password must contain between 8 and 100 characters")
    private String password;

    @NotNull(message="Role is required")
    private Role role;

    public CreateUserRequest()
    {
    }

    public CreateUserRequest(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

}
