{{/*
Common chart name.
*/}}
{{- define "banking-backend.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{/*
Common labels.
*/}}
{{- define "banking-backend.labels" -}}
app.kubernetes.io/name: {{ include "banking-backend.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}