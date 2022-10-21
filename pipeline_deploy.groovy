def repo = "https://github.com/dragonflly/cicd.git"
def PROJECT = "Project-A"

pipeline {
  agent any
  environment {
    branchName = "${environment}"
    projectName = "${PROJECT}"
    version = "${version}"
    tag = version.split(':')[0].trim()
    user = version.split(':')[2].trim()
    commitInfo = "${user} deploy ${application}:${tag} to ${branchName}"
  }

  stages {
    stage('checkout cicd repository') {
      steps {
        git branch: "${branchName}", credentialsId: 'github', url: "${repo}"
      }
    }

    stage('change imageTag') {
      steps {
          sh '''
            cd helm/${projectName}/${application}
            sed -E -i 's@^(\\s+imageTag:\\s").+(")$@\\1'"$tag"'\\2@g' values-${branchName}.yaml
            cat values-${branchName}.yaml
          '''
      }
    }

    stage('commit and push') {
      steps {
        withCredentials([gitUsernamePassword(credentialsId: 'github', gitToolName: 'Default')]) {
          sh '''
            git add helm/${projectName}/${application}
            git commit -m \"$commitInfo\" || true
            git pull origin $branchName
            git merge origin/main
            git push origin $branchName
          '''
        }
      }
    }

    stage('create pull-request') {
      when {
        environment name: 'branchName', value: 'prod'
      }

      steps {
        withCredentials([string(credentialsId: 'GITHUB_TOKEN', variable: 'GITHUB_TOKEN')]) {
          sh '''
            hub pull-request -b main -h ${branchName} -m \"deploy to ${branchName}\" || true
          '''
        }
      }
    }

  }

}