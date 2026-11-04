# Infra onboarding runbook

Goal: bring up the Terraform state bucket and cost budget in a fresh AWS account, then initialize the `dev` and `prod` roots. Everything is run from `SystemTurn/infra/`. Real account IDs and emails never go in git (`terraform.tfvars` and `backend.hcl` are gitignored).

## 1. AWS account (prerequisites)

- Tools: Terraform >= 1.11, AWS CLI v2, Git Bash or a Linux shell.
- Create an AWS account with a dedicated email. Stay in `us-east-1`.

## 2. Root MFA

Sign in as root once, enable MFA (authenticator app or passkey), then stop using root. Never create root access keys.

## 3. IAM Identity Center

In `us-east-1`, enable IAM Identity Center, create your user, and assign it `AdministratorAccess` on the account with a 1 hour session.

## 4. AWS CLI v2

Install AWS CLI v2 at version 2.22 or later and check it with `aws --version`.

## 5. aws configure sso

```bash
aws configure sso          # profile name e.g. systemturn
aws sso login --profile systemturn
export AWS_PROFILE=systemturn
aws sts get-caller-identity   # must show the new account
```

Repeat `aws sso login` whenever the session expires.

## 6. Bootstrap apply (phase 1, local state)

```bash
cd SystemTurn/infra/bootstrap
printf 'terraform {\n  backend "local" {}\n}\n' > backend_override.tf   # gitignored
cp terraform.tfvars.example terraform.tfvars   # set bucket name and alert_emails
terraform init
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Confirm the AWS Budgets subscription email sent to each address.

## 7. State migration (phase 2)

```bash
rm backend_override.tf
cp backend.hcl.example backend.hcl   # set the bucket name
terraform init -backend-config=backend.hcl -migrate-state
terraform plan   # expect: no changes
```

Answer `yes` to the copy prompt. Then delete the local `terraform.tfstate*` files. State now lives at `s3://<bucket>/bootstrap/terraform.tfstate`.

## 8. Environment roots

```bash
cd ../envs/dev      # then ../prod
cp backend.hcl.example backend.hcl   # same bucket, key is already distinct
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform plan
```

`prod` stays plan-only: the network and app modules are not wired into it yet.

## 8b. Dev cost switch (network now, app later)

`terraform apply` in `envs/dev` with default variables creates only the network: VPC, 6 subnets, internet gateway, route tables and the S3 gateway endpoint (free). No NAT, no EIP: about $0/month.

- **App on**: `enable_app = true` adds RDS, ALB, ECS, ECR and S3 + CloudFront. Tasks also need egress to pull images: `nat_mode = "single"` (cheapest) or `enable_vpc_endpoints = true`. Push an image, set `app_image_tag`, then raise `app_desired_count`. Details: [infra.md](infra.md), `infra/modules/*/README.md`.
- **Approx. monthly (us-east-1)**: RDS db.t4g.micro + 20 GB gp3 ~$14, ALB ~$16 + ~$7 public IPv4, Fargate 0.5 vCPU / 1 GB ~$18 per running task, single NAT ~$33 + $0.045/GB + ~$3.65 IPv4 (per_az ~$66-70), interface endpoints ~$58, secret $0.40; CloudFront, S3, ECR and logs cost cents.
- **Back to $0**: `enable_app = false` (and `nat_mode = "none"`), then `terraform apply`; or `terraform destroy`. Dev deletes the database without a final snapshot.
- **Why a switch, not "paused"**: a stopped RDS instance restarts itself after 7 days (and its storage and secret still bill), and the ALB bills every hour it exists.

## 9. Budget 100% response

Budget alerts do not stop spend. When an alert at 100% (actual or forecast) arrives, run `terraform destroy` in `SystemTurn/infra/envs/dev` and review Cost Explorer (data lags up to about 24 h).

## 10. Teardown

- Env roots first: `terraform destroy` in `envs/dev` and `envs/prod`.
- The state bucket is protected by `prevent_destroy`. Retiring bootstrap is deliberate: migrate state back to local, remove the lifecycle block, empty all object versions, then destroy.
- Deleting the bucket first loses the state of every root.

## Real-apply checklist

Offline tests cannot prove these. Tick them after the first real apply:

- [ ] `aws s3api get-bucket-versioning` shows `Enabled`; `get-bucket-encryption` shows `AES256`
- [ ] `aws s3api get-public-access-block` shows all four flags `true`
- [ ] A plain-HTTP request to the bucket is denied
- [ ] `terraform destroy` on bootstrap is blocked by `prevent_destroy`
- [ ] Two concurrent plans on one env: the second reports a `.tflock` lock
- [ ] Budget confirmation email received and clicked
