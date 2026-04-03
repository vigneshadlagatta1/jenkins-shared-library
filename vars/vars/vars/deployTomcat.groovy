def call() {
    sh '''
    CONTAINER=$(docker ps -qf "name=tomcat")

    if [ -z "$CONTAINER" ]; then
        echo "❌ Tomcat not running"
        exit 1
    fi

    docker cp target/*.war $CONTAINER:/usr/local/tomcat/webapps/
    '''
}
