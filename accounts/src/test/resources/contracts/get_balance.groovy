package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "cash-service can get account balance"
    request {
        method GET()
        urlPath("/accounts/balance") {
            queryParameters {
                parameter("login", "john")
            }
        }
        headers {
            header("Authorization", "Bearer token")
        }
    }
    response {
        status OK()
        body(100)
        headers {
            contentType(applicationJson())
        }
    }
}