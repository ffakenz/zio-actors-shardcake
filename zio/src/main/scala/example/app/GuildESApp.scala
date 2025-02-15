package example.app

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.Serialization
import dev.profunktor.redis4cats.RedisCommands
import example.behavior.GuildESBehavior.GuildESMessage.Join
import example.behavior.GuildESBehavior.{ GuildES, behavior }
import example.infra.Layers
import example.infra.Layers.ActorSystemZ
import zio.actors.ActorSystem
import zio.{ Random, Scope, System, Task, ZIO, ZIOAppDefault, ZLayer }

object GuildESApp extends ZIOAppDefault {
  val config: ZLayer[Any, SecurityException, Config] =
    ZLayer(
      System
        .env("port")
        .map(_.flatMap(_.toIntOption).fold(Config.default)(port => Config.default.copy(shardingPort = port)))
    )

  val program: ZIO[
    Sharding with ActorSystemZ with Scope with Serialization with RedisCommands[Task, String, String],
    Throwable,
    Unit
  ] =
    for {
      _     <- Sharding.registerEntity(GuildES, behavior)
      _     <- Sharding.registerScoped
      guild <- Sharding.messenger(GuildES)
      user1 <- Random.nextUUID.map(_.toString)
      user2 <- Random.nextUUID.map(_.toString)
      user3 <- Random.nextUUID.map(_.toString)
      _     <- guild.send("guild1")(Join(user1, _)).debug
      _     <- guild.send("guild1")(Join(user2, _)).debug
      _     <- guild.send("guild1")(Join(user3, _)).debug
      _     <- ZIO.never
    } yield ()

  def run: Task[Unit] =
    ZIO
      .scoped(program)
      .provide(
        config,
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        Layers.redis,
        Layers.actorSystem("GuildSystem"),
        StorageRedis.live,
        KryoSerialization.live,
        ShardManagerClient.liveWithSttp,
        GrpcPods.live,
        Sharding.live,
        GrpcShardingService.live
      )
}
