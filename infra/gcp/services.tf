locals {
  services = {
    order-service = {
      database = "orders"
      # Public only if asked for. It is the API clients call.
      ingress = var.public_order_api ? "INGRESS_TRAFFIC_ALL" : "INGRESS_TRAFFIC_INTERNAL_ONLY"
    }
    fulfilment-service = {
      database = "fulfilment"
      # Nothing outside the project needs to call it. Its webhooks go out, not in.
      ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"
    }
  }

  image_base = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}

resource "google_artifact_registry_repository" "images" {
  repository_id = "order-events"
  location      = var.region
  format        = "DOCKER"

  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }
  depends_on = [google_project_service.apis]
}

# One identity per service, with no project-wide roles.
resource "google_service_account" "service" {
  for_each     = local.services
  account_id   = "${each.key}-${var.environment}"
  display_name = "${each.key} (${var.environment})"
}

resource "google_cloud_run_v2_service" "service" {
  for_each            = local.services
  name                = "${each.key}-${var.environment}"
  location            = var.region
  ingress             = each.value.ingress
  deletion_protection = var.environment == "prod"

  template {
    service_account = google_service_account.service[each.key].email

    # The outbox relay, the Kafka consumer and the webhook dispatcher all run in the background,
    # so keep one instance up with CPU always allocated. With request-based CPU they'd stall
    # between HTTP requests.
    scaling {
      min_instance_count = 1
      max_instance_count = 4
    }

    vpc_access {
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.run.id
      }
      egress = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = "${local.image_base}/${each.key}:${var.image_tag}"

      ports {
        container_port = 8080
      }

      resources {
        cpu_idle = false
        limits = {
          cpu    = "1"
          memory = "768Mi"
        }
      }

      env {
        name  = "SERVER_PORT"
        value = "8080"
      }
      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/${each.value.database}?sslmode=require"
      }
      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = google_sql_user.app.name
      }
      env {
        name = "SPRING_DATASOURCE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "SPRING_KAFKA_BOOTSTRAP_SERVERS"
        value = var.kafka_bootstrap_servers
      }
      # One JSON object per log line. Cloud Logging picks up the fields, including traceId.
      env {
        name  = "LOGGING_STRUCTURED_FORMAT_CONSOLE"
        value = "ecs"
      }
      env {
        name  = "MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED"
        value = "false"
      }

      startup_probe {
        http_get {
          path = "/actuator/health/liveness"
        }
        initial_delay_seconds = 10
        period_seconds        = 5
        failure_threshold     = 24 # up to two minutes for a cold JVM and Flyway
      }
      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
        }
        period_seconds = 15
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_iam_member.db_password,
    google_secret_manager_secret_version.db_password,
    google_sql_database.service,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "public_orders" {
  count    = var.public_order_api ? 1 : 0
  name     = google_cloud_run_v2_service.service["order-service"].name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
