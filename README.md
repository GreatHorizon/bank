# Banking Microservices

## Requirements

- Java 21
- Maven
- Docker
- Kubernetes / Rancher Desktop
- kubectl
- Helm
- Keycloak

## Start Keycloak

Keycloak runs outside Kubernetes.

```bash
docker run -d \
  --name keycloak \
  -p 9090:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e KC_HOSTNAME_STRICT=false \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

Open Keycloak Admin Console:

```text
http://localhost:9090/admin/master/console/
```

Get your host IP:

```bash
ipconfig getifaddr en0
```

Example host IP:

```text
192.168.0.100
```

Check Keycloak by host IP:

```bash
curl http://192.168.0.100:9090/realms/master/.well-known/openid-configuration
```

## Build Project

```bash
mvn clean package -DskipTests
```

## Build Docker Images

Use the Docker context used by Kubernetes.

```bash
docker context use rancher-desktop
```

```bash
docker build --no-cache -t accounts-service:1.0.0 ./accounts
docker build --no-cache -t cash-service:1.0.0 ./cash
docker build --no-cache -t transfer-service:1.0.0 ./transfer
docker build --no-cache -t notifications-service:1.0.0 ./notifications
docker build --no-cache -t gateway:1.0.0 ./gateway
```

## Helm Values

Create or update `banking-backend-chart/values.yaml`.

Do not put real secrets in this file.

```yaml
global:
  environment: k8s
  imagePullPolicy: IfNotPresent

  keycloak:
    issuerUri: ""
    jwkSetUri: ""

  frontend:
    origin: "http://localhost:3000"

gateway:
  name: gateway
  image: gateway:1.0.0
  port: 8080
  replicas: 1

  service:
    type: NodePort
    port: 80
    nodePort: 30080

services:
  accounts:
    name: accounts-service
    image: accounts-service:1.0.0
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
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
    image: cash-service:1.0.0
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""

  transfer:
    name: transfer-service
    image: transfer-service:1.0.0
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""

  notifications:
    name: notifications-service
    image: notifications-service:1.0.0
    port: 8080
    replicas: 1
    keycloak:
      clientSecret: ""
```

## Local Secrets

Create `banking-backend-chart/values-local.yaml`.

Do not commit this file.

```yaml
global:
  keycloak:
    issuerUri: "http://192.168.0.100:9090/realms/master"
    jwkSetUri: "http://192.168.0.100:9090/realms/master/protocol/openid-connect/certs"

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

Add to `.gitignore`:

```gitignore
banking-backend-chart/values-local.yaml
banking-backend-chart/values-secrets.yaml
*.secret.yaml
```

## Deploy Backend

```bash
helm upgrade --install banking-backend ./banking-backend-chart \
  -f ./banking-backend-chart/values.yaml \
  -f ./banking-backend-chart/values-local.yaml
```

## Check Resources

```bash
kubectl get pods
kubectl get svc
kubectl get secret
kubectl get configmap
```

Expected pods:

```text
accounts-db-0            1/1 Running
accounts-service         1/1 Running
cash-service             1/1 Running
gateway                  1/1 Running
notifications-service    1/1 Running
transfer-service         1/1 Running
```

## Test Gateway

```bash
curl http://localhost:30080/actuator/health
```

Gateway URL:

```text
http://localhost:30080
```

## Test Services Inside Cluster

```bash
kubectl run test-curl --rm -it --image=curlimages/curl -- sh
```

Inside the pod:

```sh
curl http://gateway:80/actuator/health
curl http://accounts-service:8080/actuator/health
curl http://cash-service:8080/actuator/health
curl http://transfer-service:8080/actuator/health
curl http://notifications-service:8080/actuator/health
curl http://192.168.0.100:9090/realms/master/.well-known/openid-configuration
```

Exit:

```sh
exit
```

## Run Helm Tests

```bash
helm test banking-backend --logs
```

## Frontend

Frontend should call backend through gateway:

```text
http://localhost:30080
```

Frontend should use Keycloak through browser:

```text
http://localhost:9090
```

Example `.env`:

```env
VITE_API_BASE_URL=http://localhost:30080
VITE_KEYCLOAK_URL=http://localhost:9090
VITE_KEYCLOAK_REALM=master
VITE_KEYCLOAK_CLIENT_ID=frontend-client
```

Start frontend:

```bash
cd front
npm install
npm run dev
```
