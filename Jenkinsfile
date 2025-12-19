pipeline {
    agent any

    tools {
        // Ensure these match Manage Jenkins -> Tools exactly
        maven 'M2_HOME'
        jdk 'JDK17'
    }

    environment {
        IMAGE_NAME = 'skander1174/skander-projet:latest'
        DOCKER_CREDENTIALS_ID = 'docker-hub-skander'
        
        // --- CORRECTED PATH for Jenkins Service installation ---
        KUBECONFIG = '/var/lib/jenkins/.kube/config'
        
        // --- STOP JENKINS FROM REDIRECTING KUBECTL TO PORT 8080 ---
        KUBERNETES_SERVICE_HOST = ''
        KUBERNETES_SERVICE_PORT = ''
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/skanders00/skander.git'
            }
        }

        stage('Build Test') {
            steps {
                echo 'Running Unit Tests...'
                sh "mvn clean test -Dspring.datasource.url=jdbc:h2:mem:testdb -Dspring.datasource.driverClassName=org.h2.Driver -Dspring.datasource.username=sa -Dspring.datasource.password= -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
            }
        }

        stage('SonarQube Analysis') {
            steps {
                // Ensure name matches Manage Jenkins -> System -> SonarQube
                withSonarQubeEnv('MVN SONARQUBE') {
                    sh "mvn sonar:sonar -Dsonar.projectKey=skander-project -Dsonar.projectName='skander-project'"
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
                withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS_ID}", usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    echo 'Logging into Docker Hub...'
                    sh 'echo $PASS | docker login -u $USER --password-stdin'
                    sh "docker push ${IMAGE_NAME}"
                }
            }
        }

        stage('Deploy Kubernetes') {
            steps {
                script {
                    echo 'Generating Manifests...'
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

                  def kconfig = "/var/lib/jenkins/.kube/config"
                    
                    // 2. Command to 'clean' the environment so kubectl doesn't look at port 8080
                    def kfix = "env -u KUBERNETES_SERVICE_HOST -u KUBERNETES_SERVICE_PORT"

                    echo 'Generating Manifests...'
                    // (Keep your writeFile blocks for mysql and spring here...)

                    echo 'Applying Deployments...'
                    
                    // Ensure the namespace exists
                    sh "${kfix} kubectl --kubeconfig=${kconfig} create namespace devops || true"

                    // --- THE FIX: We add --validate=false to skip the Jenkins login trap ---
                    sh "${kfix} kubectl --kubeconfig=${kconfig} apply -f mysql-deployment.yaml -n devops --validate=false"
                    sh "${kfix} kubectl --kubeconfig=${kconfig} apply -f spring-deployment.yaml -n devops --validate=false"

                    echo 'Restarting Spring Pods...'
                    sh "${kfix} kubectl --kubeconfig=${kconfig} rollout restart deployment/spring-app -n devops"
                }
            }
        }
    }

    post {
        success {
            echo "✔ Pipeline Executed Successfully"
        }
        failure {
            echo "❌ Pipeline Failed - Check Console Output"
        }
        always {
            // --- CRITICAL FOR 30GB DISK: Cleanup ---
            sh 'docker logout'
            sh 'docker image prune -f'
            cleanWs() 
        }
    }
}
