server {
    host = '0.0.0.0'
    port = 8082
    ioWorkersCount = 1
    ttl = 60 * 60 * 1000
    context = '/'
    websocketFrameLengthLimit = 10 * 1024 * 1024
}