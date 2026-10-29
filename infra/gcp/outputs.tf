output "order_service_url" {
  value = google_cloud_run_v2_service.service["order-service"].uri
}

output "fulfilment_service_url" {
  value = google_cloud_run_v2_service.service["fulfilment-service"].uri
}

output "image_repository" {
  description = "Push images here, for example <repository>/order-service:<tag>."
  value       = local.image_base
}

output "database_private_ip" {
  value = google_sql_database_instance.postgres.private_ip_address
}
