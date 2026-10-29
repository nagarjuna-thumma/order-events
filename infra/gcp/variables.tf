variable "project_id" {
  description = "GCP project to deploy into."
  type        = string
}

variable "region" {
  description = "Region for Cloud Run, Cloud SQL and the network."
  type        = string
  default     = "northamerica-northeast2" # Toronto
}

variable "environment" {
  description = "Short name used in resource names, such as dev or prod."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,10}$", var.environment))
    error_message = "Use lowercase letters, digits and dashes, up to 11 characters."
  }
}

variable "image_tag" {
  description = "Tag of the order-service and fulfilment-service images in Artifact Registry."
  type        = string
  default     = "latest"
}

variable "kafka_bootstrap_servers" {
  description = "Kafka brokers both services connect to, such as a Managed Service for Apache Kafka cluster or Confluent Cloud."
  type        = string
}

variable "db_tier" {
  description = "Cloud SQL machine tier. The default is the smallest shared-core tier, fine for dev."
  type        = string
  default     = "db-f1-micro"
}

variable "public_order_api" {
  description = "Allow unauthenticated calls to the order-service. Leave false outside a demo, and put an API gateway or IAP in front instead."
  type        = bool
  default     = false
}
