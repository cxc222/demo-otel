#!/bin/bash

# OpenTelemetry Java Agent 部署脚本
# 支持不同环境和配置选项

set -e

# 配置变量
APP_NAME="my-spring-boot-app"
APP_VERSION="1.0.0"
JAVA_OPTS=""
OTEL_AGENT_VERSION="1.31.0"
ENVIRONMENT=${ENVIRONMENT:-dev}

# OpenTelemetry配置
OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME:-$APP_NAME}
OTEL_SERVICE_VERSION=${OTEL_SERVICE_VERSION:-$APP_VERSION}
OTEL_RESOURCE_ATTRIBUTES="service.name=$OTEL_SERVICE_NAME,service.version=$OTEL_SERVICE_VERSION,deployment.environment=$ENVIRONMENT"

# 阿里云SLS配置
OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT:-"https://ignite.cn-hangzhou-intranet.log.aliyuncs.com:10010"}
OTEL_EXPORTER_OTLP_PROTOCOL=${OTEL_EXPORTER_OTLP_PROTOCOL:-"grpc"}
OTEL_EXPORTER_OTLP_COMPRESSION=${OTEL_EXPORTER_OTLP_COMPRESSION:-"gzip"}

# 阿里云SLS认证信息 - 从环境变量或配置文件读取
SLS_PROJECT=${SLS_PROJECT:-"ignite"}
SLS_INSTANCE_ID=${SLS_INSTANCE_ID:-"demo-test"}
SLS_AK_ID=${SLS_AK_ID:-""}
SLS_AK_SECRET=${SLS_AK_SECRET:-""}

# 构建SLS Headers
if [ -n "$SLS_AK_ID" ] && [ -n "$SLS_AK_SECRET" ]; then
    OTEL_EXPORTER_OTLP_HEADERS="x-sls-otel-project=$SLS_PROJECT,x-sls-otel-instance-id=$SLS_INSTANCE_ID,x-sls-otel-ak-id=$SLS_AK_ID,x-sls-otel-ak-secret=$SLS_AK_SECRET"
else
    echo "Warning: SLS_AK_ID and SLS_AK_SECRET not set. Please configure authentication."
    OTEL_EXPORTER_OTLP_HEADERS="x-sls-otel-project=$SLS_PROJECT,x-sls-otel-instance-id=$SLS_INSTANCE_ID"
fi

# 采样配置
OTEL_TRACE_SAMPLER=${OTEL_TRACE_SAMPLER:-"traceidratio"}
OTEL_TRACE_SAMPLER_ARG=${OTEL_TRACE_SAMPLER_ARG:-"1.0"}

# Agent下载函数
download_otel_agent() {
    local agent_file="opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar"

    if [ ! -f "$agent_file" ]; then
        echo "Downloading OpenTelemetry Java Agent v${OTEL_AGENT_VERSION}..."
        curl -L -o "$agent_file" \
            "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
        echo "Download completed: $agent_file"
    else
        echo "OpenTelemetry Java Agent already exists: $agent_file"
    fi

    echo "$agent_file"
}

# Java Agent启动函数
start_with_agent() {
    local agent_file=$(download_otel_agent)
    local jar_file=${1:-"target/${APP_NAME}.jar"}

    echo "Starting application with OpenTelemetry Java Agent..."
    echo "Agent: $agent_file"
    echo "Application: $jar_file"
    echo "Environment: $ENVIRONMENT"

    # Java Agent参数
    AGENT_OPTS="-javaagent:$agent_file"

    # OpenTelemetry基础配置
    OTEL_OPTS="-Dotel.service.name=$OTEL_SERVICE_NAME"
    OTEL_OPTS="$OTEL_OPTS -Dotel.service.version=$OTEL_SERVICE_VERSION"
    OTEL_OPTS="$OTEL_OPTS -Dotel.resource.attributes=$OTEL_RESOURCE_ATTRIBUTES"
    OTEL_OPTS="$OTEL_OPTS -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT"
    OTEL_OPTS="$OTEL_OPTS -Dotel.exporter.otlp.protocol=$OTEL_EXPORTER_OTLP_PROTOCOL"
    OTEL_OPTS="$OTEL_OPTS -Dotel.exporter.otlp.compression=$OTEL_EXPORTER_OTLP_COMPRESSION"
    OTEL_OPTS="$OTEL_OPTS -Dotel.exporter.otlp.headers=$OTEL_EXPORTER_OTLP_HEADERS"
    OTEL_OPTS="$OTEL_OPTS -Dotel.traces.sampler=$OTEL_TRACE_SAMPLER"
    OTEL_OPTS="$OTEL_OPTS -Dotel.traces.sampler.arg=$OTEL_TRACE_SAMPLER_ARG"

    # 禁用不需要的instrumentation
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.spring-boot-autoconfigure.enabled=false"
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.micrometer.enabled=false"
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.logback-appender.enabled=false"

    # HTTP客户端instrumentation配置
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.http.capture-headers.client.request=Authorization,Content-Type,Accept,User-Agent"
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.http.capture-headers.client.response=Content-Type,Content-Length,Cache-Control"

    # 数据库instrumentation配置
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.jdbc.statement-sanitizer.enabled=true"

    # Redis instrumentation配置
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.lettuce.experimental-span-attributes=true"

    # 过滤配置
    OTEL_OPTS="$OTEL_OPTS -Dotel.instrumentation.servlet.experimental.capture-request-parameters=false"

    # 环境特定配置
    case $ENVIRONMENT in
        "prod")
            echo "Production environment configuration..."
            OTEL_OPTS="$OTEL_OPTS -Dotel.traces.sampler.arg=0.1"  # 生产环境低采样率
            JAVA_OPTS="$JAVA_OPTS -Xms2g -Xmx4g -XX:+UseG1GC"  # 生产环境JVM参数
            ;;
        "staging")
            echo "Staging environment configuration..."
            OTEL_OPTS="$OTEL_OPTS -Dotel.traces.sampler.arg=0.5"  # 预发环境中等采样率
            JAVA_OPTS="$JAVA_OPTS -Xms1g -Xmx2g -XX:+UseG1GC"
            ;;
        "dev")
            echo "Development environment configuration..."
            OTEL_OPTS="$OTEL_OPTS -Dotel.traces.sampler.arg=1.0"   # 开发环境全采样
            OTEL_OPTS="$OTEL_OPTS -Dotel.javaagent.debug=true"     # 开启调试
            JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx1g"
            ;;
    esac

    # 构建完整启动命令
    FULL_COMMAND="java $AGENT_OPTS $OTEL_OPTS $JAVA_OPTS -jar $jar_file"

    echo "Starting with command:"
    echo "$FULL_COMMAND"
    echo ""

    exec $FULL_COMMAND
}

# Docker启动函数
start_in_docker() {
    local agent_file=$(download_otel_agent)

    cat > Dockerfile.otel << EOF
FROM openjdk:17-jre-slim

# 复制应用和agent
COPY target/${APP_NAME}.jar /app/app.jar
COPY $agent_file /app/opentelemetry-javaagent.jar

# 设置工作目录
WORKDIR /app

# 环境变量
ENV OTEL_SERVICE_NAME=$OTEL_SERVICE_NAME
ENV OTEL_SERVICE_VERSION=$OTEL_SERVICE_VERSION
ENV OTEL_RESOURCE_ATTRIBUTES=$OTEL_RESOURCE_ATTRIBUTES
ENV OTEL_EXPORTER_OTLP_ENDPOINT=$OTEL_EXPORTER_OTLP_ENDPOINT

# 启动命令
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
EOF

    echo "Generated Dockerfile.otel for containerized deployment"
}

# Kubernetes部署配置
generate_k8s_config() {
    cat > k8s-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $APP_NAME
  labels:
    app: $APP_NAME
    version: $APP_VERSION
spec:
  replicas: 3
  selector:
    matchLabels:
      app: $APP_NAME
  template:
    metadata:
      labels:
        app: $APP_NAME
        version: $APP_VERSION
    spec:
      containers:
      - name: $APP_NAME
        image: $APP_NAME:$APP_VERSION
        env:
        - name: OTEL_SERVICE_NAME
          value: "$APP_NAME"
        - name: OTEL_SERVICE_VERSION
          value: "$APP_VERSION"
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "service.name=$APP_NAME,service.version=$APP_VERSION,k8s.deployment.name=$APP_NAME,k8s.namespace.name=default"
        - name: OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://jaeger-collector:14250"
        - name: OTEL_EXPORTER_OTLP_PROTOCOL
          value: "grpc"
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/app/opentelemetry-javaagent.jar"
        ports:
        - containerPort: 8080
        resources:
          limits:
            memory: "1Gi"
            cpu: "500m"
          requests:
            memory: "512Mi"
            cpu: "250m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: $APP_NAME-service
spec:
  selector:
    app: $APP_NAME
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: ClusterIP
EOF

    echo "Generated k8s-deployment.yaml for Kubernetes deployment"
}

# 主函数
main() {
    case ${1:-"start"} in
        "start")
            start_with_agent $2
            ;;
        "docker")
            start_in_docker
            ;;
        "k8s")
            generate_k8s_config
            ;;
        "download")
            download_otel_agent
            ;;
        "help")
            echo "Usage: $0 [start|docker|k8s|download|help] [jar_file]"
            echo ""
            echo "Commands:"
            echo "  start [jar_file]  - Start application with OpenTelemetry Java Agent"
            echo "  docker           - Generate Dockerfile for containerized deployment"
            echo "  k8s              - Generate Kubernetes deployment configuration"
            echo "  download         - Download OpenTelemetry Java Agent"
            echo "  help             - Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  ENVIRONMENT              - Deployment environment (dev/staging/prod)"
            echo "  OTEL_SERVICE_NAME        - Service name for tracing"
            echo "  OTEL_SERVICE_VERSION     - Service version"
            echo "  OTEL_EXPORTER_OTLP_ENDPOINT - OTLP endpoint URL"
            echo "  OTEL_TRACE_SAMPLER_ARG   - Sampling ratio (0.0-1.0)"
            ;;
        *)
            echo "Unknown command: $1"
            echo "Use '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"