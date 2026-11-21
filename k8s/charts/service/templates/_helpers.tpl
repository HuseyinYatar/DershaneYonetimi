{{/* Pieces shared by the Deployments, CronJobs and the migration Job of one service. */}}

{{- define "service.name" -}}
{{- required "name is required" .Values.name -}}
{{- end -}}

{{- define "service.labels" -}}
app.kubernetes.io/name: {{ include "service.name" . }}
app.kubernetes.io/part-of: dershane
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "service.image" -}}
{{ required "image.repository is required" .Values.image.repository }}:{{ required "image.tag is required" .Values.image.tag }}
{{- end -}}

{{- define "service.hasSecrets" -}}
{{- if or .Values.secretEnv .Values.secretTemplates -}}true{{- end -}}
{{- end -}}

{{/* Settings every container reads: the ConfigMap and, when the service has any, the Secret. */}}
{{- define "service.envFrom" -}}
- configMapRef:
    name: {{ include "service.name" . }}-env
{{- if include "service.hasSecrets" . }}
- secretRef:
    name: {{ include "service.name" . }}-secrets
{{- end }}
{{- end -}}

{{/* IRSA: where the AWS SDK finds the role and the token (see the aws-token volume). */}}
{{- define "service.awsEnv" -}}
{{- if .Values.aws.roleArn }}
- name: AWS_ROLE_ARN
  value: {{ .Values.aws.roleArn | quote }}
- name: AWS_WEB_IDENTITY_TOKEN_FILE
  value: /var/run/secrets/eks.amazonaws.com/serviceaccount/token
- name: AWS_REGION
  value: {{ .Values.aws.region | quote }}
- name: AWS_STS_REGIONAL_ENDPOINTS
  value: regional
{{- end }}
{{- end -}}

{{- define "service.volumeMounts" -}}
- name: tmp
  mountPath: /tmp
{{- range $i, $dir := .Values.writableDirs }}
- name: writable-{{ $i }}
  mountPath: {{ $dir }}
{{- end }}
{{- range $i, $file := .Values.files }}
- name: files
  mountPath: {{ $file.path }}
  subPath: file-{{ $i }}
  readOnly: true
{{- end }}
{{- if .Values.aws.roleArn }}
- name: aws-token
  mountPath: /var/run/secrets/eks.amazonaws.com/serviceaccount
  readOnly: true
{{- end }}
{{- end -}}

{{- define "service.volumes" -}}
- name: tmp
  emptyDir: {}
{{- range $i, $dir := .Values.writableDirs }}
- name: writable-{{ $i }}
  emptyDir: {}
{{- end }}
{{- if .Values.files }}
- name: files
  configMap:
    name: {{ include "service.name" . }}-files
{{- end }}
{{- if .Values.aws.roleArn }}
# The kubelet asks the API server for a token of this pod's ServiceAccount, for STS (audience), and renews
# it before it expires. kube-apiserver signs it with the issuer Terraform and Ansible set up (IRSA).
- name: aws-token
  projected:
    sources:
      - serviceAccountToken:
          audience: sts.amazonaws.com
          expirationSeconds: 86400
          path: token
{{- end }}
{{- end -}}

{{/* Pod-level settings shared by every pod of the service. */}}
{{- define "service.podCommon" -}}
serviceAccountName: {{ include "service.name" . }}
# The services never call the Kubernetes API; only the AWS token above is mounted.
automountServiceAccountToken: false
securityContext:
  {{- toYaml .Values.podSecurityContext | nindent 2 }}
{{- end -}}
