def call(message, webhook) {
    sh """
    curl -X POST -H 'Content-type: application/json' \
    --data '{"text":"${message}"}' \
    ${webhook}
    """
}
