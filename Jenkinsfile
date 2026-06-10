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
        DOCKER_REGISTRY = "localhost:5000"
        CHART_PATH = "./banking-backend-chart"
        RELEASE_NAME = "banking-backend"
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
                        sh """
                            docker build --no-cache \
                              -t ${DOCKER_REGISTRY}/accounts-service:${IMAGE_TAG} \
                              ./accounts
                        """
                    }
                }

                stage('cash-service') {
                    steps {
                        sh """
                            docker build --no-cache \
                              -t ${DOCKER_REGISTRY}/cash-service:${IMAGE_TAG} \
                              ./cash
                        """
                    }
                }

                stage('transfer-service') {
                    steps {
                        sh """
                            docker build --no-cache \
                              -t ${DOCKER_REGISTRY}/transfer-service:${IMAGE_TAG} \
                              ./transfer
                        """
                    }
                }

                stage('notifications-service') {
                    steps {
                        sh """
                            docker build --no-cache \
                              -t ${DOCKER_REGISTRY}/notifications-service:${IMAGE_TAG} \
                              ./notifications
                        """
                    }
                }

                stage('gateway') {
                    steps {
                        sh """
                            docker build --no-cache \
                              -t ${DOCKER_REGISTRY}/gateway:${IMAGE_TAG} \
                              ./gateway
                        """
                    }
                }
            }
        }

        stage('Push Docker Images') {
            parallel {
                stage('Push accounts-service') {
                    steps {
                        sh "docker push ${DOCKER_REGISTRY}/accounts-service:${IMAGE_TAG}"
                    }
                }

                stage('Push cash-service') {
                    steps {
                        sh "docker push ${DOCKER_REGISTRY}/cash-service:${IMAGE_TAG}"
                    }
                }

                stage('Push transfer-service') {
                    steps {
                        sh "docker push ${DOCKER_REGISTRY}/transfer-service:${IMAGE_TAG}"
                    }
                }

                stage('Push notifications-service') {
                    steps {
                        sh "docker push ${DOCKER_REGISTRY}/notifications-service:${IMAGE_TAG}"
                    }
                }

                stage('Push gateway') {
                    steps {
                        sh "docker push ${DOCKER_REGISTRY}/gateway:${IMAGE_TAG}"
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
                          --set gateway.image=${DOCKER_REGISTRY}/gateway:${IMAGE_TAG} \
                          --set services.accounts.image=${DOCKER_REGISTRY}/accounts-service:${IMAGE_TAG} \
                          --set services.cash.image=${DOCKER_REGISTRY}/cash-service:${IMAGE_TAG} \
                          --set services.transfer.image=${DOCKER_REGISTRY}/transfer-service:${IMAGE_TAG} \
                          --set services.notifications.image=${DOCKER_REGISTRY}/notifications-service:${IMAGE_TAG}
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