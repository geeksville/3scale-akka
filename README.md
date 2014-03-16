# 3scale-akka

This is a very small library whih uses akka to create an asynchronous caching wrapper
around the excellent 3scale.com service.

## Usage

    import com.geeksville.threescale._
    val threeClient = new ThreeActor("YOUR-PROVIDER-KEY")
    val optionalUsageData = Map("hits" -> "2", "somekey" -> "someval")
    val response = threeClient ? AuthRequest("users-api-key", "service-id-for-call", optionalUsageData)
    if(response.success) throw new Exception("auth failed")

