def call(nexusUrl, version) {
    withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
        sh """
        FILE=\$(ls target/*.war | head -n 1)

        curl -v -u \$USER:\$PASS \
        --upload-file "\$FILE" \
        ${nexusUrl}/repository/maven-releases/com/example/simplecustomer/${version}/simplecustomer-${version}.war
        """
    }
}
