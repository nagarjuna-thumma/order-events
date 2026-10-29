terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.8"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # State goes in a GCS bucket created once by hand, so it's shared and locked:
  #   terraform init -backend-config="bucket=<your-state-bucket>"
  backend "gcs" {
    prefix = "order-events"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
