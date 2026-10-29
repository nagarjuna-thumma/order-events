resource "google_sql_database_instance" "postgres" {
  name             = "order-events-${var.environment}"
  database_version = "POSTGRES_16"
  region           = var.region

  settings {
    tier              = var.db_tier
    availability_type = var.environment == "prod" ? "REGIONAL" : "ZONAL"
    disk_autoresize   = true

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = var.environment == "prod"
    }

    insights_config {
      query_insights_enabled = true
    }
  }

  deletion_protection = var.environment == "prod"
  depends_on          = [google_service_networking_connection.sql]
}

# One database per service, as in docker compose. Each service owns its schema through Flyway.
resource "google_sql_database" "service" {
  for_each = local.services
  name     = each.value.database
  instance = google_sql_database_instance.postgres.name
}

resource "google_sql_user" "app" {
  name     = "app"
  instance = google_sql_database_instance.postgres.name
  password = random_password.db.result
}

resource "random_password" "db" {
  length  = 32
  special = false
}
