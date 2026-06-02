package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "cash-service can withdraw cash from account"
    request {
        method POST()
        urlPath("/accounts/balance") {
            queryParameters {
                parameter("login", "john")
                parameter("amount", "40")
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