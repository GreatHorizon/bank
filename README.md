# Banking Microservices

Проект представляет собой набор банковских микросервисов на Spring Boot, которые разворачиваются в Kubernetes через Helm. Keycloak используется как внешний Identity Provider, Gateway является единной точкой входа в backend, а Kafka используется для событий уведомлений между сервисами.

## Состав проекта

- `gateway` — API Gateway, доступен снаружи через NodePort `30080`.
- `accounts` — сервис счетов, работает с PostgreSQL.
- `cash` — сервис операций с наличными.
- `transfer` — сервис переводов.
- `notifications` — сервис уведомлений, Kafka consumer для событий из `accounts`, `cash` и `transfer`.
- `shared` — общий модуль с клиентами, DTO и общей логикой, включая Kafka `NotificationsClient`.
- Kafka — брокер сообщений внутри Kubernetes, используется для событий уведомлений.
- `banking-backend-chart` — Helm chart для развёртывания backend-сервисов в Kubernetes.
- `front` — frontend, запускается локально вне Kubernetes.
- Keycloak — запускается локально вне Kubernetes.

## Требования

Для локальной разработки и развёртывания нужны:

- Java 21
- Maven
- Docker
- Rancher Desktop или другой локальный Kubernetes
- kubectl
- Helm
- Keycloak
- Jenkins, если используется CI/CD

Проверьте инструменты:

```bash
java -version
mvn -version
docker version
kubectl version --client
helm version
```

## Архитектура локального запуска

В локальной среде backend-сервисы, PostgreSQL и Kafka запускаются в Kubernetes, а Keycloak и frontend запускаются вне Kubernetes на хост-машине.

Основные URL:

```text
Gateway:  http://localhost:30080
Keycloak: http://localhost:9090
Frontend: http://localhost:3000
Kafka:   kafka:9092 внутри Kubernetes
```

Сервисы внутри Kubernetes обращаются к Keycloak не через `localhost`, а через IP-адрес хоста, например:

```text
http://192.168.0.112:9090/realms/master
```

Актуальный IP хоста можно получить командой:

```bash
ipconfig getifaddr en0
```

## Запуск Keycloak локально

Keycloak запускается вне Kubernetes.

```bash
docker rm -f keycloak || true

docker run -d \
  --name keycloak \
  -p 0.0.0.0:9090:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0.5 \
  start-dev --http-enabled=true --hostname-strict=false --hostname-strict-https=false
```

Проверьте логи:

```bash
docker logs keycloak --tail=80
```

Admin Console:

```text
http://localhost:9090/admin/master/console/
```

Логин и пароль:

```text
admin / admin
```

Проверьте OpenID configuration с хоста:

```bash
curl -v http://127.0.0.1:9090/realms/master/.well-known/openid-configuration
```

Проверьте через IP хоста, который будет использовать Kubernetes:

```bash
curl -v http://192.168.0.112:9090/realms/master/.well-known/openid-configuration
```

Если используется старая версия Keycloak с `/auth` context path, URL будет таким:

```text
http://192.168.0.112:9090/auth/realms/master
```

В этом случае `issuerUri` и `jwkSetUri` в Helm values также должны содержать `/auth`.

## Настройка клиентов Keycloak

В realm `master` нужно создать клиентов для backend-сервисов и frontend.

Пример backend-клиентов:

```text
accounts-service
cash-service
transfer-service
notifications-service
gateway
```

Для backend-клиентов обычно используются confidential clients и client credentials. Client secret каждого сервиса нужно передать в Helm secret values.

Для frontend создайте public client, например:

```text
frontend-client
```

Для ролей backend-сервисов создайте client roles, например:

```text
accounts_role
cash_role
transfer_role
notifications_role
```

## Сборка проекта локально

Из корня проекта:

```bash
mvn clean package -DskipTests
```

Для запуска тестов:

```bash
mvn clean test
```

Для полной сборки с установкой модулей в локальный Maven repository:

```bash
mvn clean install
```

Если нужны Spring Cloud Contract stubs для `accounts`, выполните:

```bash
mvn -pl accounts -am clean install -DskipTests
```

## Сборка Docker images локально

Важно: образы должны быть собраны в том Docker runtime, который использует Kubernetes. Для Rancher Desktop обычно нужно переключиться на контекст `rancher-desktop`:

```bash
docker context use rancher-desktop
```

Соберите образы:

```bash
docker build --no-cache -t accounts-service:1.0.0 ./accounts
docker build --no-cache -t cash-service:1.0.0 ./cash
docker build --no-cache -t transfer-service:1.0.0 ./transfer
docker build --no-cache -t notifications-service:1.0.0 ./notifications
docker build --no-cache -t gateway:1.0.0 ./gateway
```

Проверьте:

```bash
docker images | grep -E 'accounts-service|cash-service|transfer-service|notifications-service|gateway'
```

Если Kubernetes использует локальные образы без registry, в Helm values нужно использовать:

```yaml
global:
  imagePullPolicy: IfNotPresent
```

или для CI/CD без registry:

```yaml
global:
  imagePullPolicy: Never
```

## Helm values

Основной файл `banking-backend-chart/values.yaml` можно хранить в Git. В нём не должно быть реальных секретов.

```yaml
global:
  environment: k8s
  imagePullPolicy: IfNotPresent

  keycloak:
    issuerUri: ""
    jwkSetUri: ""

  frontend:
    origin: "http://localhost:3000"

  kafka:
    enabled: true
    name: kafka
    image: apache/kafka:3.7.1
    imagePullPolicy: IfNotPresent
    port: 9092
    controllerPort: 9093
    storage: 2Gi
    topics:
      accountsEvents: accounts-events
      cashEvents: cash-events
      transferEvents: transfer-events
    topicDefinitions:
      - name: accounts-events
        partitions: 1
        replicationFactor: 1
      - name: cash-events
        partitions: 1
        replicationFactor: 1
      - name: transfer-events
        partitions: 1
        replicationFactor: 1

gateway:
  name: gateway
  image: gateway:1.0.1
  port: 8080
  replicas: 1

  service:
    type: NodePort
    port: 80
    nodePort: 30080

services:
  accounts:
    name: accounts-service
    image: accounts-service:1.0.1
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
    kafka:
      topic: accounts-events
      producer:
        enabled: true
    db:
      enabled: true
      name: accounts-db
      image: postgres:16
      port: 5432
      database: accountsdb
      username: ""
      password: ""
      storage: 2Gi

  cash:
    name: cash-service
    image: cash-service:1.0.1
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
    kafka:
      topic: cash-events
      producer:
        enabled: true

  transfer:
    name: transfer-service
    image: transfer-service:1.0.1
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
    kafka:
      topic: transfer-events
      producer:
        enabled: true

  notifications:
    name: notifications-service
    image: notifications-service:1.0.2
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
```

## Локальные секреты

Создайте файл `banking-backend-chart/values-local.yaml`. Этот файл нельзя коммитить.

```yaml
global:
  keycloak:
    issuerUri: "http://192.168.0.112:9090/realms/master"
    jwkSetUri: "http://192.168.0.112:9090/realms/master/protocol/openid-connect/certs"

services:
  accounts:
    db:
      username: "accountsuser"
      password: "accountspass"
    keycloak:
      clientSecret: "accounts-service-secret"

  cash:
    keycloak:
      clientSecret: "cash-service-secret"

  transfer:
    keycloak:
      clientSecret: "transfer-service-secret"

  notifications:
    keycloak:
      clientSecret: "notifications-service-secret"
```

Добавьте секретные values-файлы в `.gitignore`:

```gitignore
banking-backend-chart/values-local.yaml
banking-backend-chart/values-test.yaml
banking-backend-chart/values-prod.yaml
banking-backend-chart/values-secrets.yaml
*.secret.yaml
```

## Развёртывание backend локально в Kubernetes

```bash
helm upgrade --install banking-backend ./banking-backend-chart \
  -f ./banking-backend-chart/values.yaml \
  -f ./banking-backend-chart/values-local.yaml
```

Проверьте ресурсы:

```bash
kubectl get pods
kubectl get svc
kubectl get secret
kubectl get configmap
```

Ожидаемые pod'ы:

```text
accounts-db-0            1/1 Running
kafka-0                  1/1 Running
accounts-service         1/1 Running
cash-service             1/1 Running
gateway                  1/1 Running
notifications-service    1/1 Running
transfer-service         1/1 Running
```

Gateway должен быть доступен по адресу:

```text
http://localhost:30080
```

Проверка health endpoint:

```bash
curl http://localhost:30080/actuator/health
```

## Kafka в Kubernetes

Kafka разворачивается Helm chart'ом как `StatefulSet` с именем `kafka`. Для Kafka создаётся `ClusterIP` service `kafka:9092`, а данные брокера хранятся в PVC, смонтированном в `/tmp/kraft-combined-logs`.

После `helm upgrade --install` Helm hook job `banking-backend-kafka-topics` создаёт topics из `global.kafka.topicDefinitions`:

```text
accounts-events
cash-events
transfer-events
```

Проверить Kafka pod:

```bash
kubectl get pod kafka-0
kubectl logs kafka-0 --tail=100
```

Проверить topics:

```bash
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh   --bootstrap-server kafka:9092   --list
```

Описать topics:

```bash
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh   --bootstrap-server kafka:9092   --describe
```

Создать topics вручную, если hook job не выполнился:

```bash
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh   --bootstrap-server kafka:9092   --create --if-not-exists   --topic accounts-events   --partitions 1   --replication-factor 1

kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh   --bootstrap-server kafka:9092   --create --if-not-exists   --topic cash-events   --partitions 1   --replication-factor 1

kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh   --bootstrap-server kafka:9092   --create --if-not-exists   --topic transfer-events   --partitions 1   --replication-factor 1
```

Проверить consumer group сервиса уведомлений:

```bash
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh   --bootstrap-server kafka:9092   --describe   --group notifications-service
```

## Проверка сервисов внутри Kubernetes

Запустите временный curl pod:

```bash
kubectl run test-curl --rm -it --image=curlimages/curl -- sh
```

Внутри pod выполните:

```sh
curl http://gateway:80/actuator/health
curl http://accounts-service:8080/actuator/health
curl http://cash-service:8080/actuator/health
curl http://transfer-service:8080/actuator/health
curl http://notifications-service:8080/actuator/health
curl http://192.168.0.112:9090/realms/master/.well-known/openid-configuration

# Kafka доступен не через curl, а через Kafka CLI в kafka pod:
# kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

Выход из pod:

```sh
exit
```

## Helm tests

Запуск тестов Helm:

```bash
helm test banking-backend
```

Helm tests проверяют gateway health, health backend-сервисов, доступность Keycloak и Kafka/topics, если в chart добавлен `test-kafka-reachable.yaml` или `test-kafka-topics.yaml`.

Не используйте `--logs`, если test pod'ы удаляются через hook delete policy. В этом случае Helm может завершиться с ошибкой, даже если один из тестов прошёл успешно:

```text
unable to get pod logs for ... pod not found
```

Для отладки failed test pod:

```bash
kubectl get pods | grep banking-backend-test
kubectl logs banking-backend-test-keycloak-reachable
kubectl describe pod banking-backend-test-keycloak-reachable
```

Рекомендуется, чтобы curl в Helm test имел timeout:

```sh
curl --connect-timeout 5 --max-time 10 -f -v "$ISSUER_URI/.well-known/openid-configuration"
```

## Запуск frontend локально

Frontend должен обращаться к backend через Gateway:

```text
http://localhost:30080
```

Keycloak для браузера:

```text
http://localhost:9090
```

Пример `.env`:

```env
VITE_API_BASE_URL=http://localhost:30080
VITE_KEYCLOAK_URL=http://localhost:9090
VITE_KEYCLOAK_REALM=master
VITE_KEYCLOAK_CLIENT_ID=frontend-client
```

Запуск frontend:

```bash
cd front
npm install
npm run dev
```

## Jenkins CI/CD

Jenkins используется для автоматической сборки, тестирования Docker images и развёртывания микросервисов в Kubernetes через Helm.

### Требования к Jenkins

Jenkins должен иметь доступ к:

- Maven
- Docker
- kubectl
- Helm
- kubeconfig локального Kubernetes
- Jenkins credentials с Helm secret values

Если Jenkins запущен в Docker, пример запуска:

```bash
docker rm -f jenkins || true

docker run -d \
  --name jenkins \
  -p 8081:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -u root \
  jenkins/jenkins:lts-jdk21
```

Jenkins UI:

```text
http://localhost:8081
```

### Установка инструментов внутри Jenkins container

```bash
docker exec -u root -it jenkins bash
```

Внутри контейнера:

```bash
apt-get update
apt-get install -y maven git curl docker.io
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

Установите kubectl:

```bash
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then KARCH="arm64"; else KARCH="amd64"; fi
curl -LO "https://dl.k8s.io/release/v1.30.0/bin/linux/${KARCH}/kubectl"
install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
```

Проверьте:

```bash
mvn -version
docker ps
helm version
kubectl version --client
```

### kubeconfig для Jenkins

Скопируйте kubeconfig в контейнер Jenkins:

```bash
docker exec -u root jenkins mkdir -p /var/jenkins_home/.kube
docker cp ~/.kube/config jenkins:/var/jenkins_home/.kube/config
docker exec -u root jenkins chown -R jenkins:jenkins /var/jenkins_home/.kube
```

Если Jenkins работает в Docker на Mac, Kubernetes API может быть доступен как `host.docker.internal:6443`. Обновите kubeconfig внутри Jenkins:

```bash
docker exec -u root jenkins bash -c 'export KUBECONFIG=/var/jenkins_home/.kube/config; CTX=$(kubectl config current-context); CLUSTER=$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$CTX\")].context.cluster}"); kubectl config set-cluster "$CLUSTER" --server=https://host.docker.internal:6443 --insecure-skip-tls-verify=true; kubectl config unset clusters."$CLUSTER".certificate-authority-data; kubectl get nodes'
```

### Jenkins credentials для Helm values

Секретные файлы `values-test.yaml` и `values-prod.yaml` не должны храниться в Git. Добавьте их в Jenkins Credentials как Secret file.

В Jenkins UI:

```text
Manage Jenkins -> Credentials -> System -> Global credentials -> Add Credentials
```

Для test environment:

```text
Kind: Secret file
ID: helm-values-test
File: values-test.yaml
```

Для prod environment:

```text
Kind: Secret file
ID: helm-values-prod
File: values-prod.yaml
```

Пример `values-test.yaml`:

```yaml
global:
  keycloak:
    issuerUri: "http://192.168.0.112:9090/realms/master"
    jwkSetUri: "http://192.168.0.112:9090/realms/master/protocol/openid-connect/certs"

services:
  accounts:
    db:
      username: "accountsuser"
      password: "accountspass"
    keycloak:
      clientSecret: "accounts-service-secret"

  cash:
    keycloak:
      clientSecret: "cash-service-secret"

  transfer:
    keycloak:
      clientSecret: "transfer-service-secret"

  notifications:
    keycloak:
      clientSecret: "notifications-service-secret"
```

### Jenkinsfile

Jenkinsfile должен выполнять следующие этапы:

1. Checkout
2. Validate Maven и Helm chart
3. Build и tests
4. Установка contract stubs, если нужны contract tests
5. Сборка Docker images
6. Deploy to Kubernetes через Helm, включая Kafka `StatefulSet`, Kafka service и hook job для topics
7. Wait for Kubernetes rollouts
8. Verify Kafka topics
9. Helm tests

Пример Jenkinsfile:

```groovy
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
                sh 'helm lint ./banking-backend-chart'
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
                    def secretCredentialId = params.ENVIRONMENT == 'prod'
                        ? 'helm-values-prod'
                        : 'helm-values-test'

                    withCredentials([
                        file(credentialsId: secretCredentialId, variable: 'SECRET_VALUES_FILE')
                    ]) {
                        sh """
                            helm upgrade --install ${RELEASE_NAME} ${CHART_PATH} \
                              -f ${CHART_PATH}/values.yaml \
                              -f ${SECRET_VALUES_FILE} \
                              --wait \
                              --timeout 300s \
                              --set global.imagePullPolicy=Never \
                              --set global.kafka.enabled=true \
                              --set global.kafka.name=${KAFKA_NAME} \
                              --set global.kafka.port=${KAFKA_PORT} \
                              --set gateway.image=${GATEWAY_IMAGE} \
                              --set services.accounts.image=${ACCOUNTS_IMAGE} \
                              --set services.accounts.kafka.producer.enabled=true \
                              --set services.accounts.kafka.topic=accounts-events \
                              --set services.cash.image=${CASH_IMAGE} \
                              --set services.cash.kafka.producer.enabled=true \
                              --set services.cash.kafka.topic=cash-events \
                              --set services.transfer.image=${TRANSFER_IMAGE} \
                              --set services.transfer.kafka.producer.enabled=true \
                              --set services.transfer.kafka.topic=transfer-events \
                              --set services.notifications.image=${NOTIFICATIONS_IMAGE}
                        """
                    }
                }
            }
        }

        stage('Wait for Rollouts') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh """
                    kubectl rollout status statefulset/${KAFKA_NAME} --timeout=180s

                    kubectl rollout status deployment/accounts-service --timeout=180s
                    kubectl rollout status deployment/cash-service --timeout=180s
                    kubectl rollout status deployment/transfer-service --timeout=180s
                    kubectl rollout status deployment/notifications-service
kubectl rollout status statefulset/kafka --timeout=180s
                    kubectl rollout status deployment/gateway --timeout=180s
                """
            }
        }

        stage('Verify Kafka Topics') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh """
                    echo "Waiting for Kafka topics job..."

                    kubectl wait \
                      --for=condition=complete \
                      job/${RELEASE_NAME}-kafka-topics \
                      --timeout=180s || true

                    echo "Kafka topics:"
                    kubectl exec ${KAFKA_NAME}-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server ${KAFKA_NAME}:${KAFKA_PORT} \
                      --list

                    kubectl exec ${KAFKA_NAME}-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server ${KAFKA_NAME}:${KAFKA_PORT} \
                      --list | grep -q '^accounts-events$'

                    kubectl exec ${KAFKA_NAME}-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server ${KAFKA_NAME}:${KAFKA_PORT} \
                      --list | grep -q '^cash-events$'

                    kubectl exec ${KAFKA_NAME}-0 -- /opt/kafka/bin/kafka-topics.sh \
                      --bootstrap-server ${KAFKA_NAME}:${KAFKA_PORT} \
                      --list | grep -q '^transfer-events$'

                    echo "Kafka topics OK"
                """
            }
        }

        stage('Helm Tests') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                sh "helm test ${RELEASE_NAME} --timeout 60s"
            }
        }
    }

    post {
        success {
            echo "All services deployed successfully to ${params.ENVIRONMENT}"
        }

        failure {
            echo "Umbrella pipeline failed"

            sh """
                kubectl get pods || true
                kubectl get jobs || true
                kubectl logs job/${RELEASE_NAME}-kafka-topics || true
                kubectl logs statefulset/${KAFKA_NAME} || true
            """
        }
    }
}
```

Важно: не используйте `helm test --logs`, если Helm test pods удаляются после успешного выполнения.

### Запуск pipeline

1. Создайте Jenkins job типа Multibranch Pipeline или Pipeline from SCM.
2. Укажите Git repository проекта.
3. Убедитесь, что Jenkins видит `Jenkinsfile` в корне репозитория.
4. Добавьте credentials для GitHub, если репозиторий приватный или GitHub API rate limit превышен.
5. Добавьте Secret file credentials `helm-values-test` и `helm-values-prod`.
6. Запустите build с параметрами:

```text
ENVIRONMENT=test
RUN_DEPLOY=true
```

Для production:

```text
ENVIRONMENT=prod
RUN_DEPLOY=true
```

## Полезные команды для диагностики

Логи сервисов:

```bash
kubectl logs deploy/gateway --tail=100
kubectl logs deploy/accounts-service --tail=100
kubectl logs deploy/cash-service --tail=100
kubectl logs deploy/transfer-service --tail=100
kubectl logs deploy/notifications-service --tail=100
kubectl logs kafka-0 --tail=100
kubectl logs job/banking-backend-kafka-topics --tail=100
```

Перезапуск deployment:

```bash
kubectl rollout restart deployment/gateway
kubectl rollout restart deployment/accounts-service
kubectl rollout restart deployment/cash-service
kubectl rollout restart deployment/transfer-service
kubectl rollout restart deployment/notifications-service
kubectl rollout restart statefulset/kafka
```

Проверка rollout:

```bash
kubectl rollout status deployment/gateway
kubectl rollout status deployment/accounts-service
kubectl rollout status deployment/cash-service
kubectl rollout status deployment/transfer-service
kubectl rollout status deployment/notifications-service
kubectl rollout status statefulset/kafka
```

Проверка Helm values, которые реально применены:

```bash
helm get values banking-backend --all
```

Удаление релиза:

```bash
helm uninstall banking-backend
```

Проверка Kafka после deploy:

```bash
kubectl get statefulset kafka
kubectl get svc kafka
kubectl get job banking-backend-kafka-topics
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --list
```

Удаление старых test pod'ов:

```bash
kubectl delete pod banking-backend-test-keycloak-reachable --ignore-not-found
kubectl delete pod banking-backend-test-gateway-health --ignore-not-found
kubectl delete pod banking-backend-test-kafka-reachable --ignore-not-found
kubectl delete pod banking-backend-test-kafka-topics --ignore-not-found
```
