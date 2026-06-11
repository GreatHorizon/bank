pipeline {
    agent any

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['test', 'prod'],
            description: 'Target Kubernetes environment'
        )

        booleanParam(
            name: 'RUN_DEPLOY',
            defaultValue: true,
            description: 'Deploy all services with Helm'
        )
    }

    environment {
        IMAGE_TAG = "${BUILD_NUMBER}"
        CHART_PATH = "./banking-backend-chart"
        RELEASE_NAME = "banking-backend"
        ACCOUNTS_IMAGE = "local/accounts-service:${BUILD_NUMBER}"
        CASH_IMAGE = "local/cash-service:${BUILD_NUMBER}"
        TRANSFER_IMAGE = "local/transfer-service:${BUILD_NUMBER}"
        NOTIFICATIONS_IMAGE = "local/notifications-service:${BUILD_NUMBER}"
        GATEWAY_IMAGE = "local/gateway:${BUILD_NUMBER}"

        TESTCONTAINERS_HOST_OVERRIDE = "host.docker.internal"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Validate') {
            steps {
                sh 'mvn -version'
                sh 'helm lint ./banking-backend-chart'
            }
        }

        stage('Build All') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Install Accounts Stubs') {
          steps {
              sh 'mvn -pl accounts -am clean install -DskipTests'
              sh 'find ~/.m2/repository/com/example/accounts -name "*stubs*"'
          }
        }

        stage('Build and Test') {
          steps {
              sh 'mvn clean install'
          }
        }


        stage('Build Docker Images') {
            parallel {
                stage('accounts-service') {
                    steps {
                        sh "docker build --no-cache -t ${ACCOUNTS_IMAGE} ./accounts"
                    }
                }

                stage('cash-service') {
                    steps {
                        sh "docker build --no-cache -t ${CASH_IMAGE} ./cash"
                    }
                }

                stage('transfer-service') {
                    steps {
                        sh "docker build --no-cache -t ${TRANSFER_IMAGE} ./transfer"
                    }
                }

                stage('notifications-service') {
                    steps {
                        sh "docker build --no-cache -t ${NOTIFICATIONS_IMAGE} ./notifications"
                    }
                }

                stage('gateway') {
                    steps {
                        sh "docker build --no-cache -t ${GATEWAY_IMAGE} ./gateway"
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
          when {
              expression { return params.RUN_DEPLOY }
          }
          steps {
              script {
                  def valuesFile = params.ENVIRONMENT == 'prod'
                      ? './banking-backend-chart/values-prod.yaml'
                      : './banking-backend-chart/values-test.yaml'

                  sh """
                      helm upgrade --install ${RELEASE_NAME} ${CHART_PATH} \
                        -f ${CHART_PATH}/values.yaml \
                        -f ${valuesFile} \
                        --set global.imagePullPolicy=Never \
                        --set gateway.image=${GATEWAY_IMAGE} \
                        --set services.accounts.image=${ACCOUNTS_IMAGE} \
                        --set services.cash.image=${CASH_IMAGE} \
                        --set services.transfer.image=${TRANSFER_IMAGE} \
                        --set services.notifications.image=${NOTIFICATIONS_IMAGE}
                  """
              }
          }
        }

        stage('Helm Tests') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh "helm test ${RELEASE_NAME} --logs"
            }
        }
    }

    post {
        success {
            echo "All services deployed successfully to ${params.ENVIRONMENT}"
        }

        failure {
            echo "Umbrella pipeline failed"
        }
    }
}