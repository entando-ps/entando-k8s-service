FROM registry.access.redhat.com/ubi8/openjdk-8
FROM entando/entando-k8s-service:6.3.4

COPY target/entando-k8s-service.jar /opt/app.jar
WORKDIR /opt
CMD ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "app.jar"]
