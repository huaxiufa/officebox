# OfficeBox production deployment

## 1. Prepare

Create the certificate directory and place the TLS certificate/key there if HTTPS is required. The supplied Nginx config currently provides the HTTP reverse proxy; add a `listen 443 ssl` server block with your certificate paths before exposing 443 publicly.

## 2. Start

From the repository root:

```bash
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

## 3. Verify

```bash
docker compose -f deploy/docker-compose.prod.yml ps
curl http://127.0.0.1/healthz
```

The application health endpoint should report `status=ok`.

## 4. TLS

Use a certificate issued for the production hostname. Configure the certificate and key as read-only mounts and redirect port 80 to HTTPS after validating the certificate.

## 5. Data

Application input/output data is stored in the named Docker volume `officebox-data`. Back up this volume according to your retention policy.
