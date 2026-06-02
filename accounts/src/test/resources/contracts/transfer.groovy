package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should transfer money to another account"

    request {
        method POST()
        urlPath("/accounts/transfer") {
            queryParameters {
                parameter "login": "john"
            }
        }
        headers {
            header "Authorization", "Bearer token"
            contentType applicationJson()
        }
        body(
                login: "anna",
                amount: 30
        )
    }

    response {
        status OK()
    }
}