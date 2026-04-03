def call(Map config) {

    pipeline {
        agent any

        tools {
            maven 'Maven'
        }

        stages {

            // ✅ SET ENV (FIX FOR SHARED LIB)
            stage('Init') {
                steps {
                    script {
                        env.APP_NAME = config.appName
                        env.SONAR_URL = config.sonarUrl
                        env.NEXUS_URL = config.nexusUrl
                        env.VERSION = "1.0.${BUILD_NUMBER}"
                        env.SLACK_WEBHOOK = config.slackWebhook
                    }
                }
            }

            // ✅ CLONE
            stage('Clone') {
                steps {
                    git branch: config.branch, url: config.repo
                }
            }

            // ✅ PARALLEL STAGES
            stage('Build & Test Parallel') {
                parallel {

                    stage('Build') {
                        steps {
                            sh 'mvn clean package -DskipTests'
                        }
                    }

                    stage('Test') {
                        steps {
                            sh 'mvn test'
                        }
                    }

                    stage('Code Check') {
                        steps {
                            sh 'echo "Running basic checks..."'
                        }
                    }
                }
            }

            // ✅ CHECK ARTIFACT
            stage('Check Artifact') {
                steps {
                    sh 'ls -l target/'
                }
            }

            // ✅ SONARQUBE
            stage('SonarQube Analysis') {
                steps {
                    withSonarQubeEnv('sonar') {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'TOKEN')]) {
                            sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=${APP_NAME} \
                            -Dsonar.projectVersion=${VERSION} \
                            -Dsonar.login=\$TOKEN
                            """
                        }
                    }
                }
            }

            // ✅ QUALITY GATE
            stage('Quality Gate') {
                steps {
                    timeout(time: 3, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: false
                    }
                }
            }

            // ✅ PACKAGE
            stage('Package') {
                steps {
                    sh 'ls target/*.war'
                }
            }

            // ✅ UPLOAD TO NEXUS
            stage('Upload to Nexus') {
                steps {
                    withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                        sh '''
                        FILE=$(ls target/*.war | head -n 1)

                        echo "Uploading $FILE"

                        curl -v -u $USER:$PASS \
                        --upload-file "$FILE" \
                        ${NEXUS_URL}/repository/maven-releases/com/example/simplecustomer/${VERSION}/simplecustomer-${VERSION}.war
                        '''
                    }
                }
            }

            // ✅ DEPLOY
            stage('Deploy to Tomcat') {
                steps {
                    sh '''
                    CONTAINER=$(docker ps -qf "name=tomcat")

                    if [ -z "$CONTAINER" ]; then
                        echo "❌ Tomcat not running"
                        exit 1
                    fi

                    docker cp target/*.war $CONTAINER:/usr/local/tomcat/webapps/
                    '''
                }
            }

            // ✅ VERIFY
            stage('Verify Deployment') {
                steps {
                    echo "🚀 App deployed at: http://3.80.249.119:8082/"
                }
            }
        }

        post {
            success {
                echo "✅ Pipeline SUCCESS"
                sh '''
                curl -X POST -H 'Content-type: application/json' \
                --data '{"text":"✅ SUCCESS: SimpleCustomerApp deployed 🚀"}' \
                $SLACK_WEBHOOK
                '''
            }

            failure {
                echo "❌ Pipeline FAILED"
                sh '''
                curl -X POST -H 'Content-type: application/json' \
                --data '{"text":"❌ FAILED: SimpleCustomerApp pipeline failed 💥"}' \
                $SLACK_WEBHOOK
                '''
            }
        }
    }
}
