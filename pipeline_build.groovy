def image
def dockerPrefix = "ninghandockerid/"
def tag
def commitid
def timestamp
def username

pipeline {
  agent any
  environment{
    JAVA_HOME = "/usr/lib/jvm/java-11-openjdk"
    M2_HOME = "/opt/maven"
    PATH = "${M2_HOME}/bin:${env.PATH}"

    DOCKERHUB_CREDS = credentials('DockerHub-UP')
    sonarqube_scan = "${sonarqube_scan}"
  }

  stages {
    stage('checkout microservice repository') {
      steps {
        git branch: "${BRANCH}", credentialsId: 'github', url: "${repo}"
        sh 'ls *'
      }
    }

    stage('sonarqube scan') {
      when {
        environment name: 'sonarqube_scan', value: 'true'
      }

      steps{
        withSonarQubeEnv('sonaqube-server') {
          sh "mvn clean compile sonar:sonar"
        }
      }
    }

    stage('mvn build') {
      steps {
        sh 'mvn clean package'
      }
    }

    stage('docker build') {
      steps {
        script {
          commitid = sh (script: 'git rev-parse HEAD', returnStdout: true).trim()
          tag = commitid.substring(0,7)
        }

        sh "docker build -t ninghandockerid/\"${application}\":\"${tag}\" ."
      }
    }

    stage('docker login') {
        steps {
            sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
        }
    }

    stage('docker push') {
        steps {
            sh "docker push ninghandockerid/\"${application}\":\"${tag}\""
        }
    }

    stage('record build history') {
      steps {
        script {
          timestamp = java.time.LocalDateTime.now()
          image = dockerPrefix + "${application}"
          wrap([$class: 'BuildUser']) { username = env.BUILD_USER_ID }
        }
        
        sh "aws dynamodb put-item --region us-east-1 --table-name jenkins-build-info --item \'{\"application\":{\"S\":\"${application}\"},\"timestamp\":{\"S\":\"${timestamp}\"},\"image\":{\"S\":\"${image}\"},\"tag\":{\"S\":\"${tag}\"},\"revision\":{\"S\":\"${commitid}\"},\"branch\":{\"S\":\"${BRANCH}\"},\"username\":{\"S\":\"${username}\"}}\'"
      }
    }
  }

  post {
    always {
      sh 'docker logout'
    }
  }
}