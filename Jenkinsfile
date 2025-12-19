pipeline {
    agent any

    tools {
        // Name must match Global Tool Configuration in Jenkins
        maven 'M2_HOME'
        jdk 'JDK17'
    }

    environment {
        // Update your docker hub username if different
        IMAGE_NAME = 'skander1174/skander-projet:latest'
        DOCKER_CREDENTIALS_ID = 'docker-hub-skander'
        KUBECONFIG = '/var/jenkins_home/.kube/config'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/skanders00/skander.git'
            }
        }

        stage('Build Test') {
            steps {
                echo 'Running Unit Tests with In-Memory Database...'
                // Using 'sh' for Linux/Vagrant.
                // Using '\' for line breaks instead of '^'
                sh """
                    mvn clean test \\
                    -Dspring.datasource.url=jdbc:h2:mem:testdb \\
                    -Dspring.datasource.driverClassName=org.h2.Driver \\
                    -Dspring.datasource.username=sa \\
                    -Dspring.datasource.password= \\
                    -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect
                """
            }
        }

        stage('SonarQube Analysis') {
            steps {
                // Ensure 'MVN SONARQUBE' matches Manage Jenkins -> System -> SonarQube Servers
                withSonarQubeEnv('MVN SONARQUBE') {
                    sh """
                        mvn sonar:sonar \\
                        -Dsonar.projectKey=skander-project \\
                        -Dsonar.projectName="skander-project" \\
                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    """
                }
            }
        }

        stage('Build & Package') {
            steps {
                echo 'Packaging application...'
                sh 'mvn package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                echo 'Building Docker Image...'
                sh "docker build -t ${IMAGE_NAME} ."
            }
        }

        stage('Docker Push') {
             steps {
                withCredentials([usernamePassword(credentialsId: "$DOCKER_CREDENTIALS_ID", usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    echo 'Logging into Docker Hub...'
                    // Secure login for Linux
                    sh 'echo $PASS | docker login -u $USER --password-stdin'
                    echo 'Pushing image...'
                    sh "docker push ${IMAGE_NAME}"
                }
            }
        }

        stage('Deploy Kubernetes') {
            steps {
                echo 'Generating K8s Manifests...'

                // --- 1. MYSQL DEPLOYMENT ---
                writeFile file: 'mysql-deployment.yaml', text: '''
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
  labels:
    type: local
spec:
  storageClassName: manual
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/data/mysql"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
spec:
  storageClassName: manual
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
spec:
  selector:
    matchLabels:
      app: mysql
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - image: mysql:8.0
        name: mysql
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: root123
        - name: MYSQL_DATABASE
          value: springdb
        ports:
        - containerPort: 3306
          name: mysql
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
      volumes:
      - name: mysql-storage
        persistentVolumeClaim:
          claimName: mysql-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
spec:
  selector:
    app: mysql
  ports:
    - port: 3306
      targetPort: 3306
  type: ClusterIP
'''

                // --- 2. SPRING BOOT DEPLOYMENT (Updated for Port 8089) ---
                writeFile file: 'spring-deployment.yaml', text: '''
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-config
data:
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql-service:3306/springdb
---
apiVersion: v1
kind: Secret
metadata:
  name: spring-secret
type: Opaque
data:
  # Base64 for "root"
  SPRING_DATASOURCE_USERNAME: cm9vdA==
  # Base64 for "root123"
  SPRING_DATASOURCE_PASSWORD: cm9vdDEyMw==
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: spring-app
  template:
    metadata:
      labels:
        app: spring-app
    spec:
      containers:
      - name: spring-app
        image: skander1174/skander-projet:latest
        ports:
        - containerPort: 8089
        envFrom:
          - configMapRef:
              name: spring-config
          - secretRef:
              name: spring-secret
---
apiVersion: v1
kind: Service
metadata:
  name: spring-service
spec:
  selector:
    app: spring-app
  type: NodePort
  ports:
    - port: 8089
      targetPort: 8089
      nodePort: 30080
'''

                
              def kubeConfig = "/var/lib/jenkins/.kube/config"

            echo 'Applying Deployments...'
            
            // 1. Ensure the namespace exists
            sh "kubectl --kubeconfig=${kubeConfig} create namespace devops || true"

            // 2. Apply files using the explicit config path
            sh "kubectl --kubeconfig=${kubeConfig} apply -f mysql-deployment.yaml -n devops"
            sh "kubectl --kubeconfig=${kubeConfig} apply -f spring-deployment.yaml -n devops"

            echo 'Restarting Spring Pods...'
            sh "kubectl --kubeconfig=${kubeConfig} rollout restart deployment/spring-app -n devops"
            }
        }
    }

    post {
        success {
            echo "✔ Pipeline Executed Successfully"
        }
        failure {
            echo "❌ Pipeline Failed"
        }
        always {
            sh 'docker logout'
        }
    }
}
