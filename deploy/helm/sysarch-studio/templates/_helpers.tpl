{{- define "sysarch.name" -}}{{ .Release.Name }}{{- end -}}

{{- define "sysarch.labels" -}}
app.kubernetes.io/part-of: sysarch-studio
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "sysarch.postgresHost" -}}{{ .Release.Name }}-postgres{{- end -}}

{{- define "sysarch.jdbcUrl" -}}
{{- if .Values.postgres.enabled -}}
jdbc:postgresql://{{ include "sysarch.postgresHost" . }}:5432/{{ .Values.postgres.database }}
{{- else -}}
{{ .Values.postgres.external.url }}
{{- end -}}
{{- end -}}

{{/* Environment of the jobs that talk to PostgreSQL with psql/pg_dump (host, database, user, password). */}}
{{- define "sysarch.pgEnv" -}}
- name: PGHOST
  value: {{ if .Values.postgres.enabled }}{{ include "sysarch.postgresHost" . }}{{ else }}{{ regexReplaceAll "^jdbc:postgresql://([^:/]+).*$" .Values.postgres.external.url "${1}" }}{{ end }}
- name: PGDATABASE
  value: {{ .Values.postgres.database }}
- name: PGUSER
  valueFrom: { secretKeyRef: { name: {{ .Values.backend.existingSecret }}, key: POSTGRES_USER } }
- name: PGPASSWORD
  valueFrom: { secretKeyRef: { name: {{ .Values.backend.existingSecret }}, key: POSTGRES_PASSWORD } }
{{- end -}}
