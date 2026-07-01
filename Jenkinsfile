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

        REGISTRY = "ghcr.io"
        IMAGE_NAMESPACE = "greathorizon/bank"
        DOCKER_REGISTRY_CREDENTIALS = "ghcr-credentials"

        ACCOUNTS_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/accounts-service:${BUILD_NUMBER}"
        CASH_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/cash-service:${BUILD_NUMBER}"
        TRANSFER_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/transfer-service:${BUILD_NUMBER}"
        NOTIFICATIONS_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/notifications-service:${BUILD_NUMBER}"
        GATEWAY_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/gateway:${BUILD_NUMBER}"
        FRONT_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/front:${BUILD_NUMBER}"

        KAFKA_NAME = "kafka"
        KAFKA_PORT = "9092"

        KUBECONFIG = "/var/jenkins_home/.kube/config"
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
                sh 'helm version'
                sh '''
                    helm lint "$CHART_PATH"
                '''
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
                sh 'find ~/.m2/repository/com/example/accounts -name "*stubs*" || true'
            }
        }

        stage('Build and Test') {
            steps {
                sh 'mvn clean install'
            }
        }

        stage('Docker Login') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKER_REGISTRY_CREDENTIALS}",
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login "$REGISTRY" \
                          -u "$DOCKER_USERNAME" \
                          --password-stdin
                    '''
                }
            }
        }

        stage('Build and Push Docker Images') {
            parallel {
                stage('accounts-service') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$ACCOUNTS_IMAGE" ./accounts
                            docker push "$ACCOUNTS_IMAGE"
                        '''
                    }
                }

                stage('cash-service') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$CASH_IMAGE" ./cash
                            docker push "$CASH_IMAGE"
                        '''
                    }
                }

                stage('transfer-service') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$TRANSFER_IMAGE" ./transfer
                            docker push "$TRANSFER_IMAGE"
                        '''
                    }
                }

                stage('notifications-service') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$NOTIFICATIONS_IMAGE" ./notifications
                            docker push "$NOTIFICATIONS_IMAGE"
                        '''
                    }
                }

                stage('gateway') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$GATEWAY_IMAGE" ./gateway
                            docker push "$GATEWAY_IMAGE"
                        '''
                    }
                }

                stage('front') {
                    steps {
                        sh '''
                            docker build --no-cache -t "$FRONT_IMAGE" ./front
                            docker push "$FRONT_IMAGE"
                        '''
                    }
                }
            }
        }

        stage('Render Helm Chart') {
            steps {
                script {
                    def secretCredentialId = params.ENVIRONMENT == 'prod'
                        ? 'helm-values-prod'
                        : 'helm-values-test'

                    withCredentials([
                        file(credentialsId: secretCredentialId, variable: 'SECRET_VALUES_FILE')
                    ]) {
                        sh '''
                            helm template "$RELEASE_NAME" "$CHART_PATH" \
                              -f "$CHART_PATH/values.yaml" \
                              -f "$SECRET_VALUES_FILE" \
                              --set global.imagePullPolicy=IfNotPresent \
                              --set gateway.image="$GATEWAY_IMAGE" \
                              --set front.enabled=true \
                              --set front.image="$FRONT_IMAGE" \
                              --set services.accounts.image="$ACCOUNTS_IMAGE" \
                              --set services.cash.image="$CASH_IMAGE" \
                              --set services.transfer.image="$TRANSFER_IMAGE" \
                              --set services.notifications.image="$NOTIFICATIONS_IMAGE" \
                              > rendered.yaml

                            kubectl apply --dry-run=client -f rendered.yaml
                        '''
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
                    def secretCredentialId = params.ENVIRONMENT == 'prod'
                        ? 'helm-values-prod'
                        : 'helm-values-test'

                    withCredentials([
                        file(credentialsId: secretCredentialId, variable: 'SECRET_VALUES_FILE')
                    ]) {
                        sh '''
                            helm upgrade --install "$RELEASE_NAME" "$CHART_PATH" \
                              -f "$CHART_PATH/values.yaml" \
                              -f "$SECRET_VALUES_FILE" \
                              --wait \
                              --timeout 10m \
                              --set global.imagePullPolicy=IfNotPresent \
                              --set global.kafka.enabled=true \
                              --set global.kafka.name="$KAFKA_NAME" \
                              --set global.kafka.port="$KAFKA_PORT" \
                              --set gateway.image="$GATEWAY_IMAGE" \
                              --set front.enabled=true \
                              --set front.image="$FRONT_IMAGE" \
                              --set services.accounts.image="$ACCOUNTS_IMAGE" \
                              --set services.accounts.kafka.producer.enabled=true \
                              --set services.accounts.kafka.topic=accounts-events \
                              --set services.cash.image="$CASH_IMAGE" \
                              --set services.cash.kafka.producer.enabled=true \
                              --set services.cash.kafka.topic=cash-events \
                              --set services.transfer.image="$TRANSFER_IMAGE" \
                              --set services.transfer.kafka.producer.enabled=true \
                              --set services.transfer.kafka.topic=transfer-events \
                              --set services.notifications.image="$NOTIFICATIONS_IMAGE" \
                              --set services.notifications.kafka.consumer.enabled=true
                        '''
                    }
                }
            }
        }

        stage('Wait for Rollouts') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh '''
                    kubectl rollout status statefulset/"$KAFKA_NAME" --timeout=180s || true

                    kubectl rollout status deployment/accounts-service --timeout=180s
                    kubectl rollout status deployment/cash-service --timeout=180s
                    kubectl rollout status deployment/transfer-service --timeout=180s
                    kubectl rollout status deployment/notifications-service --timeout=180s
                    kubectl rollout status deployment/gateway --timeout=180s
                    kubectl rollout status deployment/front --timeout=180s

                    kubectl rollout status deployment/zipkin --timeout=180s || true
                    kubectl rollout status deployment/prometheus --timeout=180s || true
                    kubectl rollout status deployment/grafana --timeout=180s || true
                    kubectl rollout status deployment/elasticsearch --timeout=180s || true
                    kubectl rollout status deployment/logstash --timeout=180s || true
                    kubectl rollout status deployment/kibana --timeout=180s || true
                '''
            }
        }

        stage('Verify Kafka Topics') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh '''
                    echo "Waiting for Kafka topics job..."

                    kubectl wait \
                      --for=condition=complete \
                      job/"$RELEASE_NAME"-kafka-topics \
                      --timeout=180s || true

                    echo "Kafka topics:"
                    kubectl exec "$KAFKA_NAME"-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server "$KAFKA_NAME":"$KAFKA_PORT" \
                      --list

                    kubectl exec "$KAFKA_NAME"-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server "$KAFKA_NAME":"$KAFKA_PORT" \
                      --list | grep -q '^accounts-events'

                    kubectl exec "$KAFKA_NAME"-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server "$KAFKA_NAME":"$KAFKA_PORT" \
                      --list | grep -q '^cash-events'

                    kubectl exec "$KAFKA_NAME"-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server "$KAFKA_NAME":"$KAFKA_PORT" \
                      --list | grep -q '^transfer-events'

                    echo "Kafka topics OK"
                '''
            }
        }

        stage('Helm Tests') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh '''
                    helm test "$RELEASE_NAME" --logs
                '''
            }
        }
    }

    post {
        success {
            echo "All services deployed successfully to ${params.ENVIRONMENT}"
        }

        failure {
            echo "Umbrella pipeline failed"

            sh '''
                kubectl get pods || true
                kubectl get svc || true
                kubectl get jobs || true

                kubectl logs job/"$RELEASE_NAME"-kafka-topics || true

                kubectl logs statefulset/"$KAFKA_NAME" || true
                kubectl logs deployment/accounts-service || true
                kubectl logs deployment/cash-service || true
                kubectl logs deployment/transfer-service || true
                kubectl logs deployment/notifications-service || true
                kubectl logs deployment/gateway || true
                kubectl logs deployment/front || true
                kubectl logs deployment/logstash || true
                kubectl logs deployment/elasticsearch || true
            '''
        }
    }
}
