package com.example.pekko.grpc

import com.example.pekko.grpc.proto.GreeterServiceGrpc
import com.example.pekko.grpc.proto.HelloReply
import com.example.pekko.grpc.proto.HelloRequest
import io.grpc.stub.StreamObserver
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * gRPC GreeterService 구현체
 * Pekko Actor와 연동하여 인사 메시지 처리
 */
class GreeterServiceImpl(
    private val system: ActorSystem<*>,
    private val greeterActor: ActorRef<GreeterActor.Command>
) : GreeterServiceGrpc.GreeterServiceImplBase() {

    private val log = LoggerFactory.getLogger(GreeterServiceImpl::class.java)
    private val timeout = Duration.ofSeconds(3)

    /**
     * 단순 인사 RPC
     */
    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        log.info("SayHello 요청 받음: {}", request.name)

        val future = AskPattern.ask(
            greeterActor,
            { replyTo: ActorRef<GreeterActor.Greeting> ->
                GreeterActor.Greet(request.name, replyTo)
            },
            timeout,
            system.scheduler()
        )

        future.whenComplete { greeting, error ->
            if (error != null) {
                log.error("인사 처리 실패", error)
                responseObserver.onError(error)
            } else {
                val reply = HelloReply.newBuilder()
                    .setMessage(greeting.message)
                    .setTimestamp(greeting.timestamp)
                    .build()
                responseObserver.onNext(reply)
                responseObserver.onCompleted()
            }
        }
    }

    /**
     * 서버 스트리밍 RPC - 여러 인사 반환
     */
    override fun sayHelloStream(request: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        log.info("SayHelloStream 요청 받음: {}", request.name)

        val future = AskPattern.ask(
            greeterActor,
            { replyTo: ActorRef<GreeterActor.MultipleGreetings> ->
                GreeterActor.GreetMultiple(request.name, 5, replyTo)
            },
            timeout,
            system.scheduler()
        )

        future.whenComplete { greetings, error ->
            if (error != null) {
                log.error("다중 인사 처리 실패", error)
                responseObserver.onError(error)
            } else {
                greetings.greetings.forEach { greeting ->
                    val reply = HelloReply.newBuilder()
                        .setMessage(greeting.message)
                        .setTimestamp(greeting.timestamp)
                        .build()
                    responseObserver.onNext(reply)
                }
                responseObserver.onCompleted()
            }
        }
    }
}
