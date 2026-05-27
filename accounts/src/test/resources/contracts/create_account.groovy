package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should create account for current user"

    request {
        method POST()
        url "/accounts"
        headers {
            header "Authorization", "Bearer token"
            contentType applicationJson()
        }
        body(
                name: "John Smith",
                birthDate: "1990-01-01"
        )
    }

    response {
        status OK()
    }
}