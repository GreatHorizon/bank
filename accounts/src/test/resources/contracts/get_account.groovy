package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return current user account"

    request {
        method GET()
        url "/accounts"
        headers {
            header "Authorization", "Bearer token"
        }
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                firstName: "John",
                lastName: "Smith",
                birthDate: "1990-01-01",
                balance: 100
        )
    }
}