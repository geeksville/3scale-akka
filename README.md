# 3scale-akka


Build Status: [![Dependency Status](https://www.codeship.io/projects/f2510300-ae1c-0131-570d-3aef0687d72b/status)](https://www.codeship.io/projects/19687)


This is a very small library whih uses akka to create an asynchronous caching wrapper
around the excellent 3scale.com service.

## Usage
```scala
import com.geeksville.threescale._
val threeClient = new ThreeActor("YOUR-PROVIDER-KEY")
val optionalUsageData = Map("hits" -> "2", "somekey" -> "someval")
(threeClient ? AuthRequest("users-api-key", "service-id-for-call", optionalUsageData)) map { resp =>
    if(resp.success) throw new Exception("auth failed")
}
```
