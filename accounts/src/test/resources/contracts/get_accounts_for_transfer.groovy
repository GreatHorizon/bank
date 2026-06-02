package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return accounts available for transfer"

    request {
        method GET()
        url "/accounts/accounts-for-transfer"
        headers {
            header "Authorization", "Bearer token"
        }
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                [
                        login    : "anna",
                        firstName: "Anna",
                        lastName : "Ivanova"
                ],
                [
                        login    : "petr",
                        firstName: "Petr",
                        lastName : "Petrov"
                ]
        ])
    }
}