# The database password lives in Secret Manager. Cloud Run reads it at startup, so it never
# appears in the service definition, and each service account can read only this secret.
resource "google_secret_manager_secret" "db_password" {
  secret_id = "order-events-${var.environment}-db-password"
  replication {
    auto {}
  }
  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}

resource "google_secret_manager_secret_iam_member" "db_password" {
  for_each  = local.services
  secret_id = google_secret_manager_secret.db_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.service[each.key].email}"
}
