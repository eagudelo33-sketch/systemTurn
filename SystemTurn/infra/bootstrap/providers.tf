provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "systemturn"
      Environment = "shared"
      ManagedBy   = "terraform"
      Owner       = var.owner
    }
  }
}
