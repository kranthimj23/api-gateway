pipeline {
    agent any

    environment {
        SERVICE_NAME = 'api-gateway'
        DOCKER_REGISTRY = credentials('docker-registry-url')
        DOCKER_CREDENTIALS = credentials('docker-registry-credentials')
        SONARQUBE_TOKEN = credentials('sonarqube-token')
        KUBECONFIG = credentials('kubeconfig')
        GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        VERSION = "${env.BUILD_NUMBER}-${GIT_COMMIT_SHORT}"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Target environment')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip tests')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy to Kubernetes')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_MSG = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                }
            }
        }

        stage('Build') {
            steps {
                dir("services/${SERVICE_NAME}") {
                    sh '''
                        chmod +x mvnw
                        ./mvnw clean compile -DskipTests -B
                    '''
                }
            }
        }

        stage('Unit Tests') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                dir("services/${SERVICE_NAME}") {
                    sh './mvnw test -B'
                }
            }
            post {
                always {
                    junit "services/${SERVICE_NAME}/target/surefire-reports/*.xml"
                }
            }
        }

        stage('Code Quality Analysis') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                dir("services/${SERVICE_NAME}") {
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            ./mvnw sonar:sonar \
                                -Dsonar.projectKey=${SERVICE_NAME} \
                                -Dsonar.projectName=${SERVICE_NAME} \
                                -Dsonar.host.url=${SONAR_HOST_URL} \
                                -Dsonar.login=${SONARQUBE_TOKEN}
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Package') {
            steps {
                dir("services/${SERVICE_NAME}") {
                    sh './mvnw package -DskipTests -B'
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir("services/${SERVICE_NAME}") {
                    script {
                        docker.build("${DOCKER_REGISTRY}/${SERVICE_NAME}:${VERSION}")
                        docker.build("${DOCKER_REGISTRY}/${SERVICE_NAME}:${params.ENVIRONMENT}")
                        if (params.ENVIRONMENT == 'prod') {
                            docker.build("${DOCKER_REGISTRY}/${SERVICE_NAME}:latest")
                        }
                    }
                }
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-credentials') {
                        docker.image("${DOCKER_REGISTRY}/${SERVICE_NAME}:${VERSION}").push()
                        docker.image("${DOCKER_REGISTRY}/${SERVICE_NAME}:${params.ENVIRONMENT}").push()
                        if (params.ENVIRONMENT == 'prod') {
                            docker.image("${DOCKER_REGISTRY}/${SERVICE_NAME}:latest").push()
                        }
                    }
                }
            }
        }

        stage('Helm Lint') {
            steps {
                dir("helm/charts/${SERVICE_NAME}") {
                    sh 'helm lint .'
                }
            }
        }

        stage('Helm Dry Run') {
            steps {
                dir("helm/charts/${SERVICE_NAME}") {
                    sh """
                        helm upgrade --install ${SERVICE_NAME} . \
                            --namespace mobile-banking-${params.ENVIRONMENT} \
                            -f values-${params.ENVIRONMENT}.yaml \
                            --set image.tag=${VERSION} \
                            --dry-run
                    """
                }
            }
        }

        stage('Deploy') {
            when {
                expression { params.DEPLOY }
            }
            steps {
                dir("helm/charts/${SERVICE_NAME}") {
                    sh """
                        helm upgrade --install ${SERVICE_NAME} . \
                            --namespace mobile-banking-${params.ENVIRONMENT} \
                            -f values-${params.ENVIRONMENT}.yaml \
                            --set image.tag=${VERSION} \
                            --wait \
                            --timeout 5m
                    """
                }
            }
        }

        stage('Verify Deployment') {
            when {
                expression { params.DEPLOY }
            }
            steps {
                sh """
                    kubectl rollout status deployment/${SERVICE_NAME} \
                        -n mobile-banking-${params.ENVIRONMENT} \
                        --timeout=5m
                """
            }
        }

        stage('Smoke Tests') {
            when {
                expression { params.DEPLOY && !params.SKIP_TESTS }
            }
            steps {
                script {
                    def serviceUrl = sh(
                        script: "kubectl get svc ${SERVICE_NAME} -n mobile-banking-${params.ENVIRONMENT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'",
                        returnStdout: true
                    ).trim()
                    
                    sh """
                        curl -f http://${serviceUrl}:8080/health || exit 1
                        curl -f http://${serviceUrl}:8080/ready || exit 1
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            slackSend(
                channel: '#deployments',
                color: 'good',
                message: "SUCCESS: ${SERVICE_NAME} v${VERSION} deployed to ${params.ENVIRONMENT}"
            )
        }
        failure {
            slackSend(
                channel: '#deployments',
                color: 'danger',
                message: "FAILED: ${SERVICE_NAME} deployment to ${params.ENVIRONMENT}"
            )
        }
    }
}
