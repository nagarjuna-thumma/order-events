# Runs `terraform test` against mocked providers, so CI checks the important settings
# without a GCP account or credentials.

mock_provider "google" {}
mock_provider "random" {}

variables {
  project_id              = "test-project"
  kafka_bootstrap_servers = "kafka:9092"
}

run "database_is_private_and_encrypted" {
  command = plan

  assert {
    condition     = google_sql_database_instance.postgres.settings[0].ip_configuration[0].ipv4_enabled == false
    error_message = "Cloud SQL must not get a public IP."
  }
  assert {
    condition     = google_sql_database_instance.postgres.settings[0].ip_configuration[0].ssl_mode == "ENCRYPTED_ONLY"
    error_message = "Connections to Cloud SQL must use TLS."
  }
  assert {
    condition     = toset([for d in google_sql_database.service : d.name]) == toset(["orders", "fulfilment"])
    error_message = "Each service gets its own database."
  }
}

run "services_stay_private_by_default" {
  command = plan

  assert {
    condition     = google_cloud_run_v2_service.service["order-service"].ingress == "INGRESS_TRAFFIC_INTERNAL_ONLY"
    error_message = "order-service should not be public unless public_order_api is set."
  }
  assert {
    condition     = google_cloud_run_v2_service.service["fulfilment-service"].ingress == "INGRESS_TRAFFIC_INTERNAL_ONLY"
    error_message = "fulfilment-service is never public."
  }
  assert {
    condition     = length(google_cloud_run_v2_service_iam_member.public_orders) == 0
    error_message = "No allUsers invoker unless asked for."
  }
}

run "background_work_keeps_cpu" {
  command = plan

  assert {
    condition = alltrue([for s in google_cloud_run_v2_service.service :
      s.template[0].containers[0].resources[0].cpu_idle == false && s.template[0].scaling[0].min_instance_count >= 1
    ])
    error_message = "The outbox relay, consumer and webhook dispatcher need an instance with CPU always on."
  }
}

run "password_comes_from_secret_manager" {
  command = plan

  assert {
    condition = alltrue([for s in google_cloud_run_v2_service.service :
      length([for e in s.template[0].containers[0].env : e if e.name == "SPRING_DATASOURCE_PASSWORD" && e.value == null]) == 1
    ])
    error_message = "The database password must be a secret reference, not a plain value."
  }
}

run "public_demo_and_prod_settings" {
  command = plan

  variables {
    environment      = "prod"
    public_order_api = true
  }

  assert {
    condition     = google_cloud_run_v2_service.service["order-service"].ingress == "INGRESS_TRAFFIC_ALL"
    error_message = "public_order_api opens the order-service."
  }
  assert {
    condition     = length(google_cloud_run_v2_service_iam_member.public_orders) == 1
    error_message = "public_order_api adds the allUsers invoker."
  }
  assert {
    condition     = google_sql_database_instance.postgres.settings[0].availability_type == "REGIONAL" && google_sql_database_instance.postgres.deletion_protection
    error_message = "prod gets a regional database with deletion protection."
  }
}

run "environment_name_is_checked" {
  command = plan

  variables {
    environment = "Prod_1"
  }

  expect_failures = [var.environment]
}
