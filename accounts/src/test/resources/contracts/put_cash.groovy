package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "cash-service can put cash to account"
    request {
        method PUT()
        urlPath("/accounts/balance") {
            queryParameters {
                parameter("login", "john")
                parameter("amount", "50")
            }
        }
        headers {
            header("Authorization", "Bearer token")
        }
    }
    response {
        status OK()
    }
}